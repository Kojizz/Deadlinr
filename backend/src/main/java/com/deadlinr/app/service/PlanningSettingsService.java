package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service public class PlanningSettingsService {
 private final JdbcTemplate db;private final ObjectMapper json;
 public PlanningSettingsService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 private JsonNode parse(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
 public ObjectNode get(User u){ObjectNode n=json.createObjectNode().put("urgentDays",2).put("escalationCap",24).put("weeklyGoal",10).put("timeZone","Asia/Almaty").put("digestTime","08:00").put("dailyDigest",false).put("weeklyReview",true).put("emailDigest",false);var rows=db.queryForList("select preferences::text from user_settings where owner_id=?",String.class,u.getId());if(!rows.isEmpty())n.setAll((ObjectNode)parse(rows.getFirst()));n.remove("_dataWorkspaceId");return n;}
 @Transactional public ObjectNode save(User u,JsonNode patch){
  if(!patch.isObject())throw new IllegalArgumentException("Expected preferences");db.queryForList("select id from users where id=? for update",u.getId());ObjectNode n=get(u);
  patch.fields().forEachRemaining(e->{if(!n.has(e.getKey()))throw new IllegalArgumentException("Unknown preference: "+e.getKey());n.set(e.getKey(),e.getValue());});
  for(var pair:Map.of("urgentDays",30,"escalationCap",168,"weeklyGoal",1000).entrySet())if(!n.path(pair.getKey()).isIntegralNumber()||n.path(pair.getKey()).asInt()<1||n.path(pair.getKey()).asInt()>pair.getValue())throw new IllegalArgumentException("Invalid "+pair.getKey());
  try{ZoneId.of(n.path("timeZone").asText());LocalTime.parse(n.path("digestTime").asText());}catch(Exception e){throw new IllegalArgumentException("Invalid timezone or digest time");}
  for(String key:List.of("dailyDigest","weeklyReview","emailDigest"))if(!n.path(key).isBoolean())throw new IllegalArgumentException("Invalid "+key);
  db.update("insert into user_settings(owner_id,preferences)values(?,cast(? as jsonb)) on conflict(owner_id) do update set preferences=user_settings.preferences||excluded.preferences",u.getId(),n.toString());return n;
 }
 public List<Map<String,Object>> views(User u,int page){return db.query("select id,name,filters::text from saved_views where owner_id=? order by created_at desc limit 100 offset ?",(r,i)->Map.of("id",r.getObject("id"),"name",r.getString("name"),"filters",parse(r.getString("filters"))),u.getId(),Math.max(0,page)*100);}
 public Map<String,Object> saveView(User u,JsonNode n){String name=n.path("name").asText().trim();if(name.isEmpty()||name.length()>80||!n.path("filters").isObject()||n.path("filters").toString().length()>4000)throw new IllegalArgumentException("Provide a view name and filters");UUID id=UUID.randomUUID();db.update("insert into saved_views(id,owner_id,name,filters)values(?,?,?,cast(? as jsonb))",id,u.getId(),name,n.path("filters").toString());return Map.of("id",id,"name",name,"filters",n.path("filters"));}
 public void deleteView(User u,UUID id){if(db.update("delete from saved_views where id=? and owner_id=?",id,u.getId())==0)throw new ResourceNotFoundException("View not found");}
}
