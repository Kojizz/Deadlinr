package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service public class WorkHistoryService {
 private final JdbcTemplate db;private final ObjectMapper json;private final WorkService work;private final NotificationService notifications;
 public WorkHistoryService(JdbcTemplate db,ObjectMapper json,WorkService work,NotificationService notifications){this.db=db;this.json=json;this.work=work;this.notifications=notifications;}
 private JsonNode parse(String s){try{return s==null?null:json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> status(User u){return db.queryForMap("select exists(select 1 from work_history where owner_id=? and not undone) as \"canUndo\",exists(select 1 from work_history where owner_id=? and undone) as \"canRedo\"",u.getId(),u.getId());}
 private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 @Transactional public Map<String,Object> apply(User u,boolean redo){
  work.lock(u);var history=db.queryForList("select id from work_history where owner_id=? and undone=? order by id "+(redo?"asc":"desc")+" limit 1",Long.class,u.getId(),redo);if(history.isEmpty())throw new IllegalArgumentException(redo?"Nothing to redo":"Nothing to undo");long historyId=history.getFirst();var changes=db.queryForList("select item_id,before_state::text,after_state::text,undo_state::text from work_history_rows where history_id=? order by item_id",historyId);
  for(var c:changes){long id=((Number)c.get("item_id")).longValue();work.get(u,id);String expected=(String)c.get(redo?"undo_state":"after_state");String actual=db.queryForObject("select to_jsonb(w)::text from work_items w where id=?",String.class,id);
   var a=(com.fasterxml.jackson.databind.node.ObjectNode)parse(actual);var e=(com.fasterxml.jackson.databind.node.ObjectNode)parse(expected);a.remove(List.of("version","updated_at"));e.remove(List.of("version","updated_at"));if(!a.equals(e))conflict("An item changed since this action. Undo would overwrite newer work.");
   var target=parse((String)c.get(redo?"after_state":"before_state"));if(target!=null&&target.path("archived_at").isNull()&&target.hasNonNull("page_id")&&Boolean.TRUE.equals(db.queryForObject("select archived_at is not null from pages where id=?",Boolean.class,target.path("page_id").asLong())))conflict("Restore the linked page before undoing this action.");
  }
  db.queryForObject("select set_config('deadlinr.skip_history','true',true)",String.class);
  String columns="kind,title,description,page_id,course_id,parent_id,status,priority,due_at,start_at,all_day,hard_deadline,location,link,repeat_rule,labels,reminder_minutes,position,completed_at,archived_at,archive_batch_id,created_at,time_zone,completion_due_at";
  UUID batch=UUID.randomUUID();for(var c:changes){long id=((Number)c.get("item_id")).longValue();String target=(String)c.get(redo?"after_state":"before_state");
   if(target==null)db.update("update work_items set archived_at=now(),archive_batch_id=?,updated_at=now(),version=version+1 where id=? and owner_id=?",batch,id,u.getId());
   else db.update("update work_items set ("+columns+")=(select "+columns+" from jsonb_populate_record(null::work_items,cast(? as jsonb))),updated_at=now(),version=version+1 where id=? and owner_id=?",target,id,u.getId());
   notifications.schedule(work.get(u,id));if(!redo)db.update("update work_history_rows set undo_state=(select to_jsonb(w) from work_items w where id=?) where history_id=? and item_id=?",id,historyId,id);
  }
  if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from work_items w join work_dependencies d on d.item_id=w.id join work_items b on b.id=d.depends_on_id where w.owner_id=? and w.status='DONE' and w.archived_at is null and b.status<>'DONE' and b.archived_at is null)",Boolean.class,u.getId())))conflict("Undo would leave a completed item blocked by unfinished work.");
  db.update("update work_history set undone=? where id=? and owner_id=?",!redo,historyId,u.getId());return status(u);
 }
}
