package com.deadlinr.app.service;
import com.deadlinr.app.dto.WorkItem;
import com.deadlinr.app.dto.SearchResult;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class WorkService {
 private final JdbcTemplate db;private final NamedParameterJdbcTemplate named;private final ObjectMapper json;
 private final WorkspaceService workspace;private final NotificationService notifications;private final WorkCounts counts;
 private static final Set<String> FIELDS=Set.of("kind","title","description","pageId","courseId","parentId","status","priority","dueAt","startAt","allDay","hardDeadline","location","link","repeatRule","labels","reminderMinutes","position","timeZone","version","confirmMove");
 private static final String SELECT_LIST="""
  select w.id,w.owner_id,w.kind,w.title,left(w.description,160) as description,w.page_id,w.course_id,w.parent_id,w.status,w.priority,w.due_at,w.start_at,w.all_day,w.hard_deadline,w.location,w.link,w.repeat_rule,w.labels,w.reminder_minutes,w.position,w.completed_at,w.archived_at,w.archive_batch_id,w.created_at,w.updated_at,w.version,w.time_zone,w.completion_due_at,exists(select 1 from work_dependencies d join work_items b on b.id=d.depends_on_id where d.item_id=w.id and b.status<>'DONE' and b.archived_at is null) as blocked,(select count(*) from attachments a where a.item_id=w.id) as file_count,
  (select count(*) from work_items c where c.parent_id=w.id and c.archived_at is null) as subtask_count,
  (select count(*) from work_items c where c.parent_id=w.id and c.archived_at is null and c.status='DONE') as subtasks_done,
  (select p.title from pages p where p.id=w.page_id) as page_title
  from work_items w
  """;
 private static final String SELECT="""
  select w.*,exists(select 1 from work_dependencies d join work_items b on b.id=d.depends_on_id where d.item_id=w.id and b.status<>'DONE' and b.archived_at is null) as blocked,(select count(*) from attachments a where a.item_id=w.id) as file_count,
  (select count(*) from work_items c where c.parent_id=w.id and c.archived_at is null) as subtask_count,
  (select count(*) from work_items c where c.parent_id=w.id and c.archived_at is null and c.status='DONE') as subtasks_done,
  (select p.title from pages p where p.id=w.page_id) as page_title
  from work_items w
  """;
 public WorkService(JdbcTemplate db,ObjectMapper json,WorkspaceService workspace,NotificationService notifications,WorkCounts counts){this.db=db;this.named=new NamedParameterJdbcTemplate(db);this.json=json;this.workspace=workspace;this.notifications=notifications;this.counts=counts;}
 private Instant instant(ResultSet r,String col)throws SQLException{var t=r.getTimestamp(col);return t==null?null:t.toInstant();}
 private <T> List<T> listJson(String text,Class<T> type){try{return json.readValue(text,json.getTypeFactory().constructCollectionType(List.class,type));}catch(Exception e){throw new IllegalStateException("Invalid stored list",e);}}
 private WorkItem map(ResultSet r,int row)throws SQLException{return new WorkItem(r.getLong("id"),r.getLong("owner_id"),r.getString("kind"),r.getString("title"),r.getString("description"),r.getObject("page_id",Long.class),r.getObject("course_id",Long.class),r.getObject("parent_id",Long.class),r.getString("status"),r.getInt("priority"),instant(r,"due_at"),instant(r,"start_at"),r.getBoolean("all_day"),r.getBoolean("hard_deadline"),r.getString("location"),r.getString("link"),r.getString("repeat_rule"),listJson(r.getString("labels"),String.class),listJson(r.getString("reminder_minutes"),Integer.class),r.getDouble("position"),instant(r,"completed_at"),instant(r,"archived_at"),instant(r,"created_at"),r.getLong("version"),r.getString("time_zone"),r.getInt("file_count"),r.getInt("subtask_count"),r.getInt("subtasks_done"),r.getBoolean("blocked"),r.getString("page_title"));}
 public WorkItem get(User u,long id){var found=db.query(SELECT+" where w.id=?",this::map,id);if(found.isEmpty())throw new ResourceNotFoundException("Item not found");if(found.getFirst().ownerId()!=u.getId())throw new ForbiddenException("Item belongs to another workspace");return found.getFirst();}
 public List<WorkItem> itemsFor(String where,Object...args){return db.query(SELECT_LIST+where,this::map,args);}
 public void lock(User u){db.queryForList("select id from users where id=? for update",u.getId());}
 private String text(JsonNode n,String key,String fallback){return n.hasNonNull(key)?n.get(key).asText().trim():fallback;}
 private Instant date(JsonNode n,String key){try{return n.hasNonNull(key)&&!n.get(key).asText().isBlank()?Instant.parse(n.get(key).asText()):null;}catch(Exception e){throw new IllegalArgumentException("Invalid "+key);}}
 private ObjectNode normalize(User u,JsonNode input,WorkItem old){
  if(!input.isObject())throw new IllegalArgumentException("Expected an item object");
  input.fieldNames().forEachRemaining(k->{if(!FIELDS.contains(k))throw new IllegalArgumentException("Unknown field: "+k);});
  ObjectNode n=old==null?json.createObjectNode():json.valueToTree(old);input.fields().forEachRemaining(e->n.set(e.getKey(),e.getValue()));
  String title=text(n,"title","");if(title.isBlank()||title.length()>500)throw new IllegalArgumentException("Title must be 1–500 characters");n.put("title",title);
  for(var entry:Map.of("kind",Set.of("TASK","EVENT"),"status",Set.of("TODO","IN_PROGRESS","DONE","SOMEDAY"),"repeatRule",Set.of("NONE","DAILY","WEEKLY","MONTHLY","YEARLY")).entrySet()){
   String fallback=entry.getKey().equals("kind")?"TASK":entry.getKey().equals("status")?"TODO":"NONE";String value=text(n,entry.getKey(),fallback);
   if(!entry.getValue().contains(value))throw new IllegalArgumentException("Invalid "+entry.getKey());n.put(entry.getKey(),value);
  }
  if(n.has("priority")&&!n.path("priority").isIntegralNumber())throw new IllegalArgumentException("Priority must be a number");
  int priority=n.path("priority").asInt(3);if(priority<1||priority>4)throw new IllegalArgumentException("Priority must be 1–4");n.put("priority",priority);
  String zone=text(n,"timeZone","Asia/Almaty");try{ZoneId.of(zone);}catch(Exception e){throw new IllegalArgumentException("Invalid timezone");}n.put("timeZone",zone);
  Instant due=date(n,"dueAt"),start=date(n,"startAt");if(start!=null&&due!=null&&start.isAfter(due))throw new IllegalArgumentException("End date must follow the start");
  if(n.path("hardDeadline").asBoolean()&&due==null)throw new IllegalArgumentException("A hard deadline needs a due date");
  for(String key:List.of("description","location","link")){String value=text(n,key,"");if(value.length()>(key.equals("description")?50000:2000))throw new IllegalArgumentException(key+" is too long");n.put(key,value);}
  String link=n.path("link").asText();if(!link.isBlank()&&!link.matches("(?i)^https?://.*"))throw new IllegalArgumentException("Link must start with https:// or http://");
  if(n.hasNonNull("courseId")&&db.queryForObject("select count(*) from courses where id=? and user_id=?",Integer.class,n.get("courseId").asLong(),u.getId())==0)throw new ResourceNotFoundException("Course not found");
  if(n.hasNonNull("pageId")){workspace.ownedPage(u,n.get("pageId").asLong());if(db.queryForObject("select archived_at is not null from pages where id=?",Boolean.class,n.get("pageId").asLong()))throw new IllegalArgumentException("Restore the linked page first");}
  if(n.hasNonNull("parentId")){
   long id=n.get("parentId").asLong();WorkItem parent=get(u,id);if(parent.archivedAt()!=null)throw new IllegalArgumentException("Restore the parent first");
   if(old!=null&&descendants(u,old.id()).contains(id))throw new IllegalArgumentException("Subtask cycle");
   if(!n.hasNonNull("pageId")&&parent.pageId()!=null)n.put("pageId",parent.pageId());
  }
  if(!n.has("labels"))n.set("labels",json.createArrayNode());if(!n.path("labels").isArray()||n.path("labels").size()>20)throw new IllegalArgumentException("At most 20 labels");
  ArrayNode labels=json.createArrayNode();Set<String> unique=new LinkedHashSet<>();
  n.path("labels").forEach(v->{String label=v.asText().replaceFirst("^#","").trim().toLowerCase(Locale.ROOT);if(!label.matches("[\\p{L}\\p{N}_-]{1,40}"))throw new IllegalArgumentException("Invalid label");unique.add(label);});unique.forEach(labels::add);n.set("labels",labels);
  if(!n.has("reminderMinutes"))n.set("reminderMinutes",json.createArrayNode().add(60));
  if(!n.path("reminderMinutes").isArray()||n.path("reminderMinutes").size()>10)throw new IllegalArgumentException("At most 10 reminders");
  n.path("reminderMinutes").forEach(v->{if(!v.isIntegralNumber()||v.asInt()<0||v.asInt()>525600)throw new IllegalArgumentException("Invalid reminder offset");});
  if(!Double.isFinite(n.path("position").asDouble()))throw new IllegalArgumentException("Invalid position");
  return n;
 }
 private Long longValue(JsonNode n,String key){return n.hasNonNull(key)?n.get(key).asLong():null;}
 private MapSqlParameterSource params(User u,ObjectNode n){
  var p=new MapSqlParameterSource("owner",u.getId());for(String key:List.of("kind","title","description","status","location","link","repeatRule","timeZone"))p.addValue(key,n.path(key).asText());
  for(String key:List.of("pageId","courseId","parentId"))p.addValue(key,longValue(n,key));
  for(String key:List.of("dueAt","startAt")){var d=date(n,key);p.addValue(key,d==null?null:Timestamp.from(d));}
  p.addValue("priority",n.path("priority").asInt(3)).addValue("allDay",n.path("allDay").asBoolean()).addValue("hardDeadline",n.path("hardDeadline").asBoolean()).addValue("position",n.path("position").asDouble()).addValue("labels",n.path("labels").toString()).addValue("reminderMinutes",n.path("reminderMinutes").toString());
  return p;
 }
 @Transactional public WorkItem create(User u,JsonNode input){
  lock(u);ObjectNode n=normalize(u,input,null);if(!input.has("position"))n.put("position",db.queryForObject("select coalesce(max(position),0)+1024 from work_items where owner_id=?",Double.class,u.getId()));var p=params(u,n);
  long id=named.queryForObject("""
   insert into work_items(owner_id,kind,title,description,page_id,course_id,parent_id,status,priority,due_at,start_at,all_day,hard_deadline,location,link,repeat_rule,labels,reminder_minutes,position,time_zone,completed_at)
   values(:owner,:kind,:title,:description,:pageId,:courseId,:parentId,:status,:priority,:dueAt,:startAt,:allDay,:hardDeadline,:location,:link,:repeatRule,cast(:labels as jsonb),cast(:reminderMinutes as jsonb),:position,:timeZone,case when :status='DONE' then now() end) returning id
   """,p,Long.class);
  WorkItem item=get(u,id);notifications.schedule(item);return item;
 }
 @Transactional public WorkItem update(User u,long id,JsonNode patch){
  lock(u);WorkItem old=get(u,id);if(old.archivedAt()!=null)throw new IllegalArgumentException("Restore this item before editing");
  if(patch.hasNonNull("version")&&patch.get("version").asLong()!=old.version())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"This item changed. Refresh and retry.");
  if(old.blocked()&&patch.path("status").asText().equals("DONE"))throw new IllegalArgumentException("Complete blocking items first");
  ObjectNode n=normalize(u,patch,old);
  Instant proposed=date(n,"dueAt");if(old.hardDeadline()&&old.dueAt()!=null&&(proposed==null||proposed.isAfter(old.dueAt()))&&!patch.path("confirmMove").asBoolean())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"This is a hard deadline. Move anyway?");
  var p=params(u,n).addValue("id",id);
  named.update("""
   update work_items set kind=:kind,title=:title,description=:description,page_id=:pageId,course_id=:courseId,parent_id=:parentId,status=:status,priority=:priority,due_at=:dueAt,start_at=:startAt,all_day=:allDay,hard_deadline=:hardDeadline,location=:location,link=:link,repeat_rule=:repeatRule,labels=cast(:labels as jsonb),reminder_minutes=cast(:reminderMinutes as jsonb),position=:position,time_zone=:timeZone,
   completed_at=case when :status='DONE' then coalesce(completed_at,now()) else null end,updated_at=now(),version=version+1 where id=:id and owner_id=:owner
   """,p);
  WorkItem item=get(u,id);notifications.schedule(item);
  if(!old.status().equals("DONE")&&item.status().equals("DONE")&&!item.repeatRule().equals("NONE")&&item.dueAt()!=null){
   ZonedDateTime next=item.dueAt().atZone(ZoneId.of(item.timeZone()));
   next=switch(item.repeatRule()){case "DAILY"->next.plusDays(1);case "WEEKLY"->next.plusWeeks(1);case "MONTHLY"->next.plusMonths(1);case "YEARLY"->next.plusYears(1);default->next;};
   ObjectNode repeat=json.createObjectNode();for(String key:FIELDS)if(n.has(key)&&!Set.of("version","confirmMove").contains(key))repeat.set(key,n.get(key));
   repeat.put("status","TODO").put("dueAt",next.toInstant().toString());
   if(item.startAt()!=null)repeat.put("startAt",item.startAt().plus(Duration.between(item.dueAt(),next.toInstant())).toString());
   create(u,repeat);
  }
  return item;
 }
 public List<Long> descendants(User u,long id){return db.queryForList("with recursive tree as(select id from work_items where id=? and owner_id=? union select w.id from work_items w join tree t on w.parent_id=t.id where w.owner_id=?)select id from tree",Long.class,id,u.getId(),u.getId());}
 @Transactional public void archive(User u,long id){lock(u);get(u,id);UUID batch=UUID.randomUUID();for(long child:descendants(u,id)){db.update("update work_items set archived_at=now(),archive_batch_id=?,version=version+1 where id=? and owner_id=? and archived_at is null",batch,child,u.getId());db.update("update reminders set cancelled=true,archive_batch_id=? where item_id=? and cancelled=false and sent_at is null",batch,child);}}
 public void cleanupFiles(){workspace.cleanupFiles();}
 @Transactional public void restore(User u,long id){lock(u);WorkItem old=get(u,id);if(old.archivedAt()==null)return;if(old.pageId()!=null&&db.queryForObject("select archived_at is not null from pages where id=?",Boolean.class,old.pageId()))throw new IllegalArgumentException("Restore the linked page first");UUID batch=db.queryForObject("select archive_batch_id from work_items where id=?",UUID.class,id);
  db.update("update work_items w set parent_id=null where w.owner_id=? and w.archive_batch_id=? and exists(select 1 from work_items p where p.id=w.parent_id and p.archived_at is not null and p.archive_batch_id is distinct from ?)",u.getId(),batch,batch);
  var ids=db.queryForList("select id from work_items where owner_id=? and archive_batch_id=?",Long.class,u.getId(),batch);
  db.update("update work_items set archived_at=null,archive_batch_id=null,version=version+1 where owner_id=? and archive_batch_id=?",u.getId(),batch);
  for(long child:ids)notifications.schedule(get(u,child));
  db.update("update reminders set cancelled=false,archive_batch_id=null where owner_id=? and archive_batch_id=? and sent_at is null and scheduled_at>now() and (item_id is null or item_id not in(select id from work_items where status='DONE'))",u.getId(),batch);
 }
 public Map<String,Object> list(User u,Map<String,String> source){
  Map<String,String> filters=new HashMap<>(source);String query=filters.getOrDefault("q","");List<String> words=new ArrayList<>();List<String> labels=new ArrayList<>();
  for(String term:query.split("&")){String value=term.trim();if(value.matches("(?i)p[1-4]"))filters.put("priorities",value.substring(1));else if(value.startsWith("#"))labels.add(value.substring(1));else if(value.startsWith("due:")){String due=value.substring(4);switch(due){case "today"->filters.put("view","today");case "overdue"->filters.put("view","overdue");case "none"->filters.put("view","anytime");case "tomorrow"->{ZoneId z=ZoneId.of(filters.getOrDefault("zone","Asia/Almaty"));var d=LocalDate.now(z).plusDays(1).atStartOfDay(z);filters.put("from",d.toInstant().toString());filters.put("to",d.plusDays(1).toInstant().toString());}default->throw new IllegalArgumentException("Use due:today, tomorrow, overdue or none");}}else if(value.startsWith("status:")){filters.put("status",switch(value.substring(7)){case "todo"->"TODO";case "progress"->"IN_PROGRESS";case "done"->"DONE";case "someday"->"SOMEDAY";default->throw new IllegalArgumentException("Unknown status filter");});}else if(!value.isEmpty())words.add(value);}
  filters.put("q",String.join(" ",words));
  ZoneId zone;try{zone=ZoneId.of(filters.getOrDefault("zone","Asia/Almaty"));}catch(Exception e){throw new IllegalArgumentException("Invalid timezone");}
  var now=ZonedDateTime.now(zone);var today=now.toLocalDate().atStartOfDay(zone);
  var p=new MapSqlParameterSource("owner",u.getId());String where=" where w.owner_id=:owner and w.archived_at is null";
  String view=filters.getOrDefault("view","all");
  if(view.equals("trash"))where=" where w.owner_id=:owner and w.archived_at is not null";
  if(view.equals("today")){where+=" and w.status<>'DONE' and w.due_at<:tomorrow";p.addValue("tomorrow",Timestamp.from(today.plusDays(1).toInstant()));}
  if(view.equals("overdue"))where+=" and w.status<>'DONE' and w.due_at<now()";
  if(view.equals("inbox"))where+=" and w.due_at is null and w.course_id is null and w.status='TODO'";
  if(view.equals("anytime"))where+=" and w.due_at is null and w.status in('TODO','IN_PROGRESS')";
  if(view.equals("someday"))where+=" and w.status='SOMEDAY'";
  if(view.equals("logbook"))where+=" and w.status='DONE'";
  if(view.equals("upcoming")){int days=Math.clamp(Integer.parseInt(filters.getOrDefault("days","14")),1,365);where+=" and w.status<>'DONE' and w.due_at>=:today and w.due_at<:until";p.addValue("today",Timestamp.from(today.toInstant())).addValue("until",Timestamp.from(today.plusDays(days).toInstant()));}
  for(String key:List.of("kind","courseId","parentId"))if(filters.containsKey(key)&&!filters.get(key).isBlank()){String col=key.equals("kind")?"kind":key.equals("courseId")?"course_id":"parent_id";where+=" and w."+col+"=:"+key;p.addValue(key,key.equals("kind")?filters.get(key):Long.valueOf(filters.get(key)));}
   if(filters.containsKey("pageId")&&!filters.get("pageId").isBlank()){where+=" and w.page_id=:pageId";p.addValue("pageId",Long.valueOf(filters.get("pageId")));}

  if("true".equals(filters.get("hardOnly")))where+=" and w.hard_deadline";
  if(!labels.isEmpty()){where+=" and w.labels @> cast(:queryLabels as jsonb)";p.addValue("queryLabels",json.valueToTree(labels).toString());}
  if(filters.containsKey("label")&&!filters.get("label").isBlank()){where+=" and w.labels @> cast(:label as jsonb)";p.addValue("label",json.createArrayNode().add(filters.get("label")).toString());}
  if(filters.containsKey("q")&&!filters.get("q").isBlank()){where+=" and (w.title ilike :q or w.description ilike :q)";p.addValue("q","%"+filters.get("q").replace("%","\\%").replace("_","\\_")+"%");}
  for(String key:List.of("from","to"))if(filters.containsKey(key)&&!filters.get(key).isBlank()){String v=filters.get(key);boolean dateOnly=v.length()<=10;Instant bound=dateOnly?LocalDate.parse(v).atStartOfDay(zone).toInstant():Instant.parse(v);if(key.equals("to"))bound=(dateOnly?LocalDate.parse(v).plusDays(1):LocalDateTime.ofInstant(bound,zone).toLocalDate().plusDays(1)).atStartOfDay(zone).toInstant();where+=" and w.due_at"+(key.equals("from")?">=":"<")+":"+key;p.addValue(key,Timestamp.from(bound));}
  if("nodate".equals(filters.get("due")))where+=" and w.due_at is null";
  if("overdue".equals(filters.get("due")))where+=" and w.status<>'DONE' and w.due_at<now()";
  var priorityCounts=named.queryForMap("select count(*) filter(where priority=1) as p1,count(*) filter(where priority=2) as p2,count(*) filter(where priority=3) as p3,count(*) filter(where priority=4) as p4 from work_items w"+where,p);
  if(filters.containsKey("priorities")&&!filters.get("priorities").isBlank()){var values=Arrays.stream(filters.get("priorities").split(",")).map(Integer::valueOf).toList();where+=" and w.priority in(:priorities)";p.addValue("priorities",values);}
  var metrics=counts.compute(where,p,zone);
  if(filters.containsKey("status")&&!filters.get("status").isBlank()){where+=" and w.status=:status";p.addValue("status",filters.get("status"));}
  int size=Math.clamp(Integer.parseInt(filters.getOrDefault("size","100")),1,200),page=Math.max(0,Integer.parseInt(filters.getOrDefault("page","0")));
  String direction="desc".equalsIgnoreCase(filters.get("dir"))?" desc":" asc";
  String order=switch(filters.getOrDefault("sort","smart")){case "title"->"lower(w.title)"+direction+",w.id";case "due"->"w.due_at"+direction+" nulls last,w.id";case "manual"->"w.position,w.id";case "created"->"w.created_at"+direction+",w.id";case "priority"->"w.priority"+direction+",w.id";case "status"->"w.status"+direction+",w.id";default->"w.hard_deadline desc,w.priority,w.due_at nulls last,w.id";};

  p.addValue("groupZone",zone.getId()).addValue("groupToday",Timestamp.from(today.toInstant())).addValue("groupTomorrow",Timestamp.from(today.plusDays(1).toInstant())).addValue("groupWeekEnd",Timestamp.from(today.with(java.time.DayOfWeek.MONDAY).plusWeeks(1).toInstant()));
  String group=switch(filters.getOrDefault("group","none")){
   case "day"->"coalesce(to_char(w.due_at at time zone :groupZone,'YYYY-MM-DD'),'No date')";case "status"->"w.status";case "priority"->"'P'||w.priority";case "course"->"coalesce((select name from courses c where c.id=w.course_id),'No course')";
   case "due"->"case when w.due_at is null then 'No date' when w.due_at<now() and w.status<>'DONE' then 'Overdue' when w.due_at>=:groupToday and w.due_at<:groupTomorrow then 'Today' when w.due_at>=:groupTomorrow and w.due_at<:groupWeekEnd then 'This week' else 'Later' end";
   default->"'All items'";};
  var groupCounts=named.queryForList("select "+group+" as name,count(*) as count from work_items w"+where+" group by 1",p);
  Long total=named.queryForObject("select count(*) from work_items w"+where,p,Long.class);
  p.addValue("size",size).addValue("offset",page*size);
  return Map.of("items",named.query(SELECT_LIST+where+" order by "+order+" limit :size offset :offset",p,this::map),"total",total,"counts",metrics,"groupCounts",groupCounts,"priorityCounts",priorityCounts,"page",page,"size",size);
 }
 public List<String> deadlineDays(User u,String from,String to,String zone){
  ZoneId.of(zone);return db.queryForList("select distinct to_char(due_at at time zone ?, 'YYYY-MM-DD') from work_items where owner_id=? and archived_at is null and status<>'DONE' and due_at>=?::timestamptz and due_at<?::timestamptz order by 1",String.class,zone,u.getId(),from,to);
 }

 @Transactional public List<WorkItem> bulk(User u,List<Long> ids,JsonNode patch,String operation){
  if(ids==null||ids.isEmpty()||ids.size()>100||ids.contains(null))throw new IllegalArgumentException("Select 1–100 items");
  if(!Set.of("update","archive","restore").contains(operation))throw new IllegalArgumentException("Invalid bulk operation");
  lock(u);var unique=new LinkedHashSet<>(ids);for(long id:unique)get(u,id);
  List<WorkItem> result=new ArrayList<>();for(long id:unique){switch(operation){case "archive"->archive(u,id);case "restore"->restore(u,id);default->{if(patch==null)throw new IllegalArgumentException("Patch is required");update(u,id,patch);}}result.add(get(u,id));}return result;
 }
 public List<Map<String,Object>> reminders(User u,long id){get(u,id);return db.queryForList("select id,scheduled_at as \"scheduledAt\",sent_at as \"sentAt\",cancelled,category from reminders where item_id=? and owner_id=? order by scheduled_at",id,u.getId());}
 @Transactional public void permanent(User u,long id){lock(u);get(u,id);var ids=descendants(u,id);for(long child:ids)if(get(u,child).archivedAt()==null)throw new IllegalArgumentException("Move the item and subtasks to Trash first");
  for(long child:ids){db.update("insert into file_deletions(id)select id from attachments where item_id=? on conflict do nothing",child);}db.update("delete from work_items where id=? and owner_id=?",id,u.getId());
 }

 public List<SearchResult> search(User u,String q){if(q==null||q.isBlank())return List.of();return db.query("select id,title,left(description,180) snippet from work_items where owner_id=? and archived_at is null and title ilike ? order by updated_at desc limit 30",(r,n)->new SearchResult(r.getLong("id"),r.getString("title"),null,"WORK",r.getString("snippet")),u.getId(),"%"+q+"%");}
}









