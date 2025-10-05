package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.Environment;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class WorkspaceService {
 private final JdbcTemplate db;
 private final Path storage;
 private final WorkCounts counts;
 public WorkspaceService(JdbcTemplate db,Environment env,WorkCounts counts){this.db=db;this.storage=Path.of(env.getProperty("app.storage.path","./data/attachments")).toAbsolutePath().normalize();this.counts=counts;}
 public void ownedPage(User user,long id){
  var owners=db.queryForList("select owner_id from pages where id=?",Long.class,id);
  if(owners.isEmpty())throw new ResourceNotFoundException("Page not found");
  if(!owners.getFirst().equals(user.getId()))throw new ForbiddenException("Page belongs to another workspace");
 }
 public List<Long> descendants(long owner,long id){
  return db.queryForList("WITH RECURSIVE tree AS (SELECT id FROM pages WHERE id=? AND owner_id=? UNION SELECT p.id FROM pages p JOIN tree t ON p.parent_id=t.id WHERE p.owner_id=?) SELECT id FROM tree",Long.class,id,owner,owner);
 }
 @Transactional public Map<String,Object> archive(User user,long id){
  ownedPage(user,id);
  db.queryForList("select id from users where id=? for update",user.getId());
  UUID batch=UUID.randomUUID();int count=0;
  for(long child:descendants(user.getId(),id)){
   count+=db.update("update pages set archived_at=localtimestamp,archive_batch_id=?,updated_at=localtimestamp where id=? and owner_id=? and archived_at is null",batch,child,user.getId());
  }
  db.update("update work_items set archived_at=now(),archive_batch_id=? where owner_id=? and archived_at is null and page_id in(select id from pages where archive_batch_id=?)",batch,user.getId(),batch);
  db.update("update reminders set cancelled=true,archive_batch_id=? where owner_id=? and archive_batch_id is null and cancelled=false and sent_at is null and (page_id in(select id from pages where archive_batch_id=?) or item_id in(select id from work_items where archive_batch_id=?))",batch,user.getId(),batch,batch);
  return Map.of("archiveBatchId",batch,"affected",count);
 }
 @Transactional public void restore(User user,long id){
  ownedPage(user,id);db.queryForList("select id from users where id=? for update",user.getId());
  var row=db.queryForMap("select archived_at,archive_batch_id,parent_id from pages where id=?",id);
  if(row.get("archived_at")==null)return;
  UUID batch=(UUID)row.get("archive_batch_id");
  if(batch==null){db.update("update pages set archived_at=null where id=?",id);return;}
  db.update("update pages p set parent_id=null where p.owner_id=? and p.archive_batch_id=? and exists(select 1 from pages parent where parent.id=p.parent_id and parent.archived_at is not null and parent.archive_batch_id is distinct from ?)",user.getId(),batch,batch);
  db.update("update reminders set cancelled=false,archive_batch_id=null where owner_id=? and archive_batch_id=? and sent_at is null and scheduled_at>now() and (item_id is null or item_id not in(select id from work_items where status='DONE'))",user.getId(),batch);
  db.update("update work_items set archived_at=null,archive_batch_id=null where owner_id=? and archive_batch_id=?",user.getId(),batch);
  db.update("update pages set archived_at=null,archive_batch_id=null,updated_at=localtimestamp where owner_id=? and archive_batch_id=?",user.getId(),batch);
 }
 public Map<String,Object> linkedItemCounts(User user,List<Long> pageIds){
  if(pageIds.isEmpty())return Map.of("tasks",0,"events",0);
  var rows=db.queryForList("select kind,count(*) c from work_items where owner_id=? and archived_at is null and page_id in ("+
    String.join(",",pageIds.stream().map(String::valueOf).toList())+") group by kind",user.getId());
  long tasks=0,events=0;for(var r:rows){if("TASK".equals(r.get("kind")))tasks=((Number)r.get("c")).longValue();else if("EVENT".equals(r.get("kind")))events=((Number)r.get("c")).longValue();}
  return Map.of("tasks",tasks,"events",events);
 }
 @Transactional public void permanent(User user,long id){
  ownedPage(user,id);db.queryForList("select id from users where id=? for update",user.getId());
  var ids=descendants(user.getId(),id);
  for(long child:ids)if(db.queryForObject("select archived_at is null from pages where id=?",Boolean.class,child))throw new IllegalArgumentException("Move the page and its children to Trash first");
  for(long child:ids){
   db.update("insert into file_deletions(id) select id from attachments where owner_id=? and (page_id=? or item_id in(select id from work_items where page_id=?)) on conflict do nothing",user.getId(),child,child);
   db.update("delete from pages where id=? and owner_id=?",child,user.getId());
  }
 }
 @Transactional public void empty(User user){
  db.queryForList("select id from users where id=? for update",user.getId());
  db.update("insert into file_deletions(id) select a.id from attachments a join work_items w on w.id=a.item_id where w.owner_id=? and w.archived_at is not null on conflict do nothing",user.getId());
  db.update("delete from work_items where owner_id=? and archived_at is not null",user.getId());
  var ids=db.queryForList("select id from pages where owner_id=? and archived_at is not null order by id",Long.class,user.getId());
  for(long id:ids)if(db.queryForObject("select count(*) from pages where id=?",Integer.class,id)>0)permanent(user,id);
 }
 public List<Map<String,Object>> listPages(User user,boolean trash,int page,int size){
  return db.queryForList("select p.id,p.title,p.icon,p.parent_id as \"parentId\",p.course_id as \"courseId\",p.archived_at as \"archivedAt\",p.archive_batch_id as \"archiveBatchId\",coalesce(parent.title,'Workspace') as \"originalLocation\",p.created_at as \"createdAt\","
    +"(select count(*) from work_items w where w.page_id=p.id and w.owner_id=p.owner_id and w.archived_at is not null and w.kind='TASK') as \"taskCount\","
    +"(select count(*) from work_items w where w.page_id=p.id and w.owner_id=p.owner_id and w.archived_at is not null and w.kind='EVENT') as \"eventCount\""
    +" from pages p left join pages parent on parent.id=p.parent_id and parent.owner_id=p.owner_id where p.owner_id=? and p.archived_at is "+(trash?"not null":"null")+" order by p.updated_at desc,p.id desc limit ? offset ?",user.getId(),Math.clamp(size,1,200),Math.max(0,page)*Math.clamp(size,1,200));
 }
 public List<Map<String,Object>> children(User user,Long parent,int page,int size){
  if(parent!=null)ownedPage(user,parent);
  return db.queryForList("""
    select p.id,p.parent_id as "parentId",p.title,p.icon,'NOTE' as kind,p.position,p.favorite,p.course_id as "courseId",
    (select count(*) from pages c where c.parent_id=p.id and c.owner_id=p.owner_id and c.archived_at is null) as "childCount",
    exists(select 1 from work_items w where w.page_id=p.id and w.owner_id=p.owner_id and w.due_at is not null and w.archived_at is null) as "hasDueDate",
    (select count(*) from work_items w2 where w2.page_id=p.id and w2.owner_id=p.owner_id and w2.archived_at is null and w2.kind='TASK' and w2.status<>'DONE') as "openTasks"
    from pages p where p.owner_id=? and p.archived_at is null and p.parent_id is not distinct from ?
    order by p.position,p.id limit ? offset ?
    """,user.getId(),parent,Math.clamp(size,1,200),Math.max(0,page)*Math.clamp(size,1,200));
 }
 public Map<String,Object> summary(User user,String zone){
  ZoneId tz;try{tz=ZoneId.of(zone);}catch(Exception e){throw new IllegalArgumentException("Invalid timezone");}
  var now=ZonedDateTime.now(tz);var day=now.toLocalDate().atStartOfDay(tz).toInstant();var tomorrow=now.toLocalDate().plusDays(1).atStartOfDay(tz).toInstant();
  var week=now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(tz).toInstant();var weekEnd=now.toLocalDate().with(java.time.DayOfWeek.MONDAY).plusDays(7).atStartOfDay(tz).toInstant();
  var agg=db.queryForMap("""
   with records as (
    select id,owner_id,course_id,'NOTE' as kind,archived_at at time zone current_setting('TIMEZONE') as archived_at,created_at at time zone current_setting('TIMEZONE') as created_at,null::timestamptz as due_at,'TODO' as status from pages where owner_id=?
    union all select id,owner_id,course_id,kind,archived_at,created_at,due_at,status from work_items where owner_id=?
   ), aggregates as (
    select count(*) filter(where kind='NOTE' and archived_at is null) as "totalPages",
    count(*) filter(where kind='NOTE' and archived_at is null) as notes,
    count(*) filter(where kind='TASK' and archived_at is null) as tasks,
    count(*) filter(where kind='EVENT' and archived_at is null) as events,
    count(*) filter(where kind='TASK' and status<>'DONE' and archived_at is null) as "openTasks",
    count(*) filter(where due_at<now() and status<>'DONE' and archived_at is null) as overdue,
    count(*) filter(where due_at>=?::timestamptz and due_at<?::timestamptz and status<>'DONE' and archived_at is null) as "dueToday",
    count(*) filter(where due_at>=?::timestamptz and due_at<?::timestamptz and status<>'DONE' and archived_at is null) as "dueThisWeek",
    count(*) filter(where created_at>=?::timestamptz and archived_at is null) as "createdThisWeek",
    count(*) filter(where archived_at is not null) as "trashCount" from records
   )
   select aggregates.*,
   (select coalesce(sum(size),0) from attachments where owner_id=?) as "storageUsed",
   (select coalesce(jsonb_agg(jsonb_build_object('id',c.id,'name',c.name,'count',(select count(*) from records r where r.course_id=c.id and r.archived_at is null))),'[]')::text from courses c where c.user_id=?) as "courses"
   from aggregates
   """,user.getId(),user.getId(),day.toString(),tomorrow.toString(),week.toString(),weekEnd.toString(),week.toString(),user.getId(),user.getId());
  var out=new HashMap<>(agg);
  out.remove("courses");
  var taskCounts=counts.compute(" where w.owner_id=:owner and w.archived_at is null and w.kind='TASK'",new MapSqlParameterSource("owner",user.getId()),tz);
  out.put("openTasks",((Number)taskCounts.get("all")).longValue()-((Number)taskCounts.get("done")).longValue());
  out.put("overdue",taskCounts.get("overdue"));
  out.put("dueToday",taskCounts.get("dueToday"));
  out.put("dueThisWeek",taskCounts.get("dueThisWeek"));
  return out;
 }
 @Scheduled(fixedDelay=3600000) @Transactional public void purgeExpired(){
  var candidates=db.queryForList("select id,owner_id from pages where archived_at<localtimestamp-interval '30 days'");
  for(var p:candidates){long id=((Number)p.get("id")).longValue();if(db.queryForObject("select count(*) from pages where id=?",Integer.class,id)==0)continue;
   long owner=((Number)p.get("owner_id")).longValue();
   boolean eligible=descendants(owner,id).stream().allMatch(child->Boolean.TRUE.equals(db.queryForObject("select archived_at<localtimestamp-interval '30 days' from pages where id=?",Boolean.class,child)));
   if(eligible){User u=new User();u.setId(owner);permanent(u,id);}}
  var expired=db.queryForList("select id,owner_id from work_items where archived_at<now()-interval '30 days'");
  for(var row:expired){long id=((Number)row.get("id")).longValue();var ids=db.queryForList("with recursive tree as(select id,archived_at from work_items where id=? union select w.id,w.archived_at from work_items w join tree t on w.parent_id=t.id)select id from tree where archived_at is null or archived_at>=now()-interval '30 days'",Long.class,id);if(!ids.isEmpty())continue;
   db.update("with recursive tree as(select id from work_items where id=? union select w.id from work_items w join tree t on w.parent_id=t.id)insert into file_deletions(id)select a.id from attachments a join tree t on t.id=a.item_id on conflict do nothing",id);db.update("delete from work_items where id=?",id);}

 }
 @Scheduled(fixedDelay=30000) public void cleanupFiles(){
  for(UUID id:db.queryForList("select id from file_deletions limit 100",UUID.class)){
   try{Path file=storage.resolve(id.toString()).normalize();if(!file.getParent().equals(storage))throw new IllegalStateException("Invalid storage path");Files.deleteIfExists(file);db.update("delete from file_deletions where id=?",id);}catch(java.io.IOException ignored){/* Queue retains failures for retry. */}
  }
 }
}


