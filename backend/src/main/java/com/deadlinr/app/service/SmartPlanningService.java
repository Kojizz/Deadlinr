package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.dto.WorkItem;
import com.deadlinr.app.exception.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Service public class SmartPlanningService {
 private final JdbcTemplate db;private final ObjectMapper json;private final WorkService work;private final PlanningSettingsService settings;
 public SmartPlanningService(JdbcTemplate db,ObjectMapper json,WorkService work,PlanningSettingsService settings){this.db=db;this.json=json;this.work=work;this.settings=settings;}
 private JsonNode parse(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
 public List<Map<String,Object>> labels(User u){return db.queryForList("select name,color from labels where owner_id=? union select value as name,'#2563eb' as color from work_items w cross join lateral jsonb_array_elements_text(w.labels) where w.owner_id=? and w.archived_at is null and not exists(select 1 from labels l where l.owner_id=w.owner_id and l.name=value) order by name limit 500",u.getId(),u.getId());}
 public void label(User u,String name,JsonNode n){if(!name.matches("[\\p{L}\\p{N}_-]{1,40}")||!n.path("color").asText().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid label or color");db.update("insert into labels(owner_id,name,color)values(?,?,?) on conflict(owner_id,name) do update set color=excluded.color",u.getId(),name.toLowerCase(Locale.ROOT),n.path("color").asText());}
 public List<Map<String,Object>> templates(User u,int page){return db.query("select id,name,steps::text from task_templates where owner_id=? order by created_at desc limit 100 offset ?",(r,i)->Map.of("id",r.getObject("id"),"name",r.getString("name"),"steps",parse(r.getString("steps"))),u.getId(),Math.max(0,page)*100);}
 public Map<String,Object> template(User u,JsonNode n){String name=n.path("name").asText().trim();if(name.isBlank()||name.length()>80||!n.path("steps").isArray()||n.path("steps").isEmpty()||n.path("steps").size()>50)throw new IllegalArgumentException("Template needs a name and 1-50 steps");
  for(var step:n.path("steps"))if(step.path("title").asText().isBlank()||step.path("title").asText().length()>500||!step.path("offsetDays").isIntegralNumber()||Math.abs(step.path("offsetDays").asLong())>365)throw new IllegalArgumentException("Each step needs a title and offset between -365 and 365 days");UUID id=UUID.randomUUID();db.update("insert into task_templates(id,owner_id,name,steps)values(?,?,?,cast(? as jsonb))",id,u.getId(),name,n.path("steps").toString());return Map.of("id",id,"name",name,"steps",n.path("steps"));
 }
 public void deleteTemplate(User u,UUID id){if(db.update("delete from task_templates where id=? and owner_id=?",id,u.getId())==0)throw new ResourceNotFoundException("Template not found");}
 @Transactional public List<WorkItem> apply(User u,UUID id,long itemId){work.lock(u);var rows=db.queryForList("select steps::text from task_templates where id=? and owner_id=?",String.class,id,u.getId());if(rows.isEmpty())throw new ResourceNotFoundException("Template not found");var parent=work.get(u,itemId);if(parent.dueAt()==null)throw new IllegalArgumentException("Set the parent deadline first");List<WorkItem> result=new ArrayList<>();for(var step:parse(rows.getFirst())){
  var n=json.createObjectNode().put("title",step.path("title").asText()).put("parentId",itemId).put("timeZone",parent.timeZone()).put("dueAt",parent.dueAt().atZone(ZoneId.of(parent.timeZone())).plusDays(step.path("offsetDays").asInt()).toInstant().toString());if(parent.courseId()!=null)n.put("courseId",parent.courseId());result.add(work.create(u,n));}return result;
 }
 public List<Map<String,Object>> dependencies(User u,long id){work.get(u,id);return db.queryForList("select w.id,w.title,w.status,w.archived_at as \"archivedAt\" from work_dependencies d join work_items w on w.id=d.depends_on_id where d.item_id=? and w.owner_id=? order by w.title",id,u.getId());}
 @Transactional public void depend(User u,long id,long other){work.lock(u);var item=work.get(u,id);var blocker=work.get(u,other);if(item.archivedAt()!=null||blocker.archivedAt()!=null)throw new IllegalArgumentException("Restore items before linking them");
  if(id==other||Boolean.TRUE.equals(db.queryForObject("with recursive chain as(select depends_on_id from work_dependencies where item_id=? union select d.depends_on_id from work_dependencies d join chain c on d.item_id=c.depends_on_id)select exists(select 1 from chain where depends_on_id=?)",Boolean.class,other,id)))throw new IllegalArgumentException("Dependency would create a cycle");
  if(item.status().equals("DONE")&&!blocker.status().equals("DONE"))throw new IllegalArgumentException("Reopen this item before adding an unfinished dependency");db.update("insert into work_dependencies(item_id,depends_on_id)values(?,?) on conflict do nothing",id,other);
 }
 @Transactional public void unlink(User u,long id,long other){work.lock(u);work.get(u,id);work.get(u,other);db.update("delete from work_dependencies where item_id=? and depends_on_id=?",id,other);}
 public Map<String,Object> matrix(User u,int urgentDays){
  if(urgentDays<1||urgentDays>60)throw new IllegalArgumentException("urgentDays range 1�-60");
  var base=" where w.owner_id=? and w.archived_at is null and w.status<>'DONE'";
  var cutoff=java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(urgentDays*86400L));
  var important=" and (w.hard_deadline or w.priority<=2)";
  var notImportant=" and not (w.hard_deadline or w.priority<=2)";
  var urgent=" and w.due_at is not null and w.due_at<=?";
  var notUrgent=" and (w.due_at is null or w.due_at>?)";
  Map<String,Object> resp=new LinkedHashMap<>();
  resp.put("doFirst",pack(u.getId(),cutoff,base+important+urgent));
  resp.put("schedule",pack(u.getId(),cutoff,base+important+notUrgent));
  resp.put("keepShort",pack(u.getId(),cutoff,base+notImportant+urgent));
  resp.put("considerLater",pack(u.getId(),cutoff,base+notImportant+notUrgent));
  return resp;
 }
 private Map<String,Object> pack(long ownerId,java.sql.Timestamp cutoff,String where){
  long count=db.queryForObject("select count(*) from work_items w"+where,Long.class,ownerId,cutoff);
  return Map.of("count",count,"items",work.itemsFor(where+" order by w.hard_deadline desc,w.priority asc,w.due_at nulls last,w.id limit 25",ownerId,cutoff));
 }
 public Map<String,Object> counts(User u,String zone){var today=LocalDate.now(ZoneId.of(zone)).atStartOfDay(ZoneId.of(zone));return db.queryForMap("""
 select count(*) filter(where due_at is null and course_id is null and status='TODO') as inbox,
 count(*) filter(where due_at<? and status<>'DONE') as today,
 count(*) filter(where due_at>=? and due_at<? and status<>'DONE') as upcoming,
 count(*) filter(where due_at is null and status in('TODO','IN_PROGRESS')) as anytime,
 count(*) filter(where status='SOMEDAY') as someday,count(*) filter(where status='DONE') as logbook
 from work_items where owner_id=? and archived_at is null
 """,Timestamp.from(today.plusDays(1).toInstant()),Timestamp.from(today.toInstant()),Timestamp.from(today.plusDays(14).toInstant()),u.getId());}
 public Map<String,Object> stats(User u,String zone){ZoneId tz=ZoneId.of(zone);LocalDate today=LocalDate.now(tz),monday=today.with(DayOfWeek.MONDAY);var days=db.queryForList("select (completed_at at time zone ?)::date::text as day,count(*) as count from work_items where owner_id=? and status='DONE' and completed_at>=? group by 1 order by 1",zone,u.getId(),Timestamp.from(today.minusDays(364).atStartOfDay(tz).toInstant()));Map<String,Integer> byDay=new HashMap<>();for(var d:days)byDay.put((String)d.get("day"),((Number)d.get("count")).intValue());int streak=0;LocalDate cursor=byDay.containsKey(today.toString())?today:today.minusDays(1);while(byDay.containsKey(cursor.toString())){streak++;cursor=cursor.minusDays(1);}int week=0;for(int i=0;i<7;i++)week+=byDay.getOrDefault(monday.plusDays(i).toString(),0);
  var totals=db.queryForMap("select count(*) as total,count(*) filter(where status='DONE') as completed,count(*) filter(where status='DONE' and completion_due_at is not null) as scheduled,count(*) filter(where status='DONE' and completed_at<=completion_due_at) as on_time from work_items where owner_id=?",u.getId());double total=((Number)totals.get("total")).doubleValue(),done=((Number)totals.get("completed")).doubleValue(),scheduled=((Number)totals.get("scheduled")).doubleValue();return Map.of("days",days,"today",byDay.getOrDefault(today.toString(),0),"week",week,"streak",streak,"goal",settings.get(u).path("weeklyGoal").asInt(),"completionRate",total==0?0:done/total,"onTimeRate",scheduled==0?0:((Number)totals.get("on_time")).doubleValue()/scheduled,"completed",done);
 }
}

