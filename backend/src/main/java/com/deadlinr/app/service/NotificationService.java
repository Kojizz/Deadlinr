package com.deadlinr.app.service;
import com.deadlinr.app.dto.WorkItem;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.sql.Timestamp;
import java.util.*;
@Service
public class NotificationService {
 private final JdbcTemplate db;public NotificationService(JdbcTemplate db){this.db=db;}
 @Transactional public void schedule(WorkItem w){
  db.update("delete from reminders where item_id=? and sent_at is null",w.id());
  if(w.dueAt()==null||w.status().equals("DONE")||w.archivedAt()!=null)return;
  if(w.hardDeadline()){
   Integer cap=db.queryForObject("select coalesce((select (preferences->>'escalationCap')::int from user_settings where owner_id=?),24)",Integer.class,w.ownerId());
   List<java.time.Instant> times=new ArrayList<>();for(int minutes:List.of(4320,1440,180,60,0))times.add(w.dueAt().minusSeconds(minutes*60L));for(int hour=1;hour<=cap;hour++)times.add(w.dueAt().plusSeconds(hour*3600L));
   java.time.Instant now=java.time.Instant.now(),latest=times.stream().filter(d->!d.isAfter(now)).max(java.time.Instant::compareTo).orElse(null);
   for(var time:times)db.update("insert into reminders(owner_id,item_id,scheduled_at,category,cancelled)values(?,?,?,'ESCALATION',?) on conflict do nothing",w.ownerId(),w.id(),Timestamp.from(time),time.isBefore(now)&&!time.equals(latest));
  }
  for(int minutes:new LinkedHashSet<>(w.reminderMinutes()))db.update("insert into reminders(owner_id,item_id,scheduled_at)values(?,?,?) on conflict do nothing",w.ownerId(),w.id(),Timestamp.from(w.dueAt().minusSeconds(minutes*60L)));
 }
 @Scheduled(fixedDelay=15000) @Transactional public void deliver(){
  var pending=db.queryForList("""
   select r.id,r.owner_id,r.item_id,r.scheduled_at,r.category,w.title from reminders r join work_items w on w.id=r.item_id
   where r.scheduled_at<=now() and r.sent_at is null and not r.cancelled and w.archived_at is null and w.status<>'DONE'
   order by r.scheduled_at limit 100 for update of r skip locked
   """);
  for(var p:pending){
   db.update("insert into notifications(owner_id,item_id,reminder_id,title,body)values(?,?,?,?,?) on conflict(reminder_id) do nothing",p.get("owner_id"),p.get("item_id"),p.get("id"),p.get("title"),"ESCALATION".equals(p.get("category"))?"Critical · Hard deadline escalation":"Deadline reminder");
   db.update("update reminders set sent_at=now() where id=?",p.get("id"));
  }
 }
 public List<Map<String,Object>> list(User u,int page){return db.queryForList("select id,item_id as \"itemId\",title,body,read_at as \"readAt\",created_at as \"createdAt\" from notifications where owner_id=? order by created_at desc,id desc limit 50 offset ?",u.getId(),Math.max(0,page)*50);}
 public void read(User u,long id){if(db.update("update notifications set read_at=now() where id=? and owner_id=?",id,u.getId())==0)throw new ResourceNotFoundException("Notification not found");}
}
