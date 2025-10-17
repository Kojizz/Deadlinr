package com.deadlinr.app.service;

import com.deadlinr.app.entity.User;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class DataExportService {
 public static final int RECORD_LIMIT=2000,ATTACHMENT_LIMIT=100;
 private static final String ID_KEY="_dataWorkspaceId";
 public record Options(String format,boolean includeDocuments,boolean includeAttachments,boolean includeArchived){
  public Options normalized(){DataExportWriter.format(format);return new Options(format,!format.equals("csv")&&includeDocuments,format.equals("json")&&includeAttachments,includeArchived);}
 }
 public record Summary(long tasks,long documents,long attachments,long attachmentBytes,boolean withinLimits,String message){}
 private record Selection(List<Long> tasks,List<Long> documents){}
 private final JdbcTemplate db;private final NamedParameterJdbcTemplate named;private final ObjectMapper json;private final FileService files;private final DataExportWriter writer;
 public DataExportService(JdbcTemplate db,ObjectMapper json,FileService files,DataExportWriter writer){this.db=db;this.named=new NamedParameterJdbcTemplate(db);this.json=json;this.files=files;this.writer=writer;}
 private Selection selection(User user,Options options){
  String active=options.includeArchived()?"":" and archived_at is null";
  var tasks=db.queryForList("select id from work_items where owner_id=? and kind='TASK'"+active+" order by id limit 2001",Long.class,user.getId());
  var documents=options.includeDocuments()?db.queryForList("select id from pages where owner_id=?"+active+" order by id limit 2001",Long.class,user.getId()):List.<Long>of();
  return new Selection(tasks,documents);
 }
 private Map<String,Object> params(User user,Selection selection){return Map.of("owner",user.getId(),"tasks",selection.tasks().isEmpty()?List.of(-1L):selection.tasks(),"documents",selection.documents().isEmpty()?List.of(-1L):selection.documents());}
 private static final String FILE_WHERE=" from attachments where owner_id=:owner and (item_id in(:tasks) or page_id in(:documents))";
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Summary summary(User user,Options requested){
  var options=requested.normalized();var selected=selection(user,options);
  String active=options.includeArchived()?"":" and archived_at is null";
  long tasks=db.queryForObject("select count(*) from work_items where owner_id=? and kind='TASK'"+active,Long.class,user.getId());
  long documents=options.includeDocuments()?db.queryForObject("select count(*) from pages where owner_id=?"+active,Long.class,user.getId()):0;
  long count=0,bytes=0;
  if(options.includeAttachments()&&tasks+documents<=RECORD_LIMIT){var stats=named.queryForMap("select count(*) as total,coalesce(sum(size),0) as bytes"+FILE_WHERE,params(user,selected));count=((Number)stats.get("total")).longValue();bytes=((Number)stats.get("bytes")).longValue();}
  boolean within=tasks+documents<=RECORD_LIMIT&&count<=ATTACHMENT_LIMIT&&bytes*4/3<=DataExportWriter.JSON_LIMIT;
  return new Summary(tasks,documents,count,bytes,within,within?"Export includes all matching records, not just the visible page.":"Selection exceeds the export limits. Reduce its scope; nothing will be truncated.");
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public byte[] export(User user,Options requested){
  var options=requested.normalized();
  // All task/settings mutations use this same per-owner lock. One UUID remains
  // stable across concurrent exports and never appears in editable preferences.
  db.queryForList("select id from users where id=? for update",user.getId());
  var selected=selection(user,options);if(selected.tasks().size()+selected.documents().size()>RECORD_LIMIT)tooLarge("Select at most 2,000 tasks and documents.");
  var params=params(user,selected);
  long textBytes=named.queryForObject("select coalesce(sum(octet_length(description)+octet_length(title)),0) from work_items where owner_id=:owner and id in(:tasks)",params,Long.class);
  if(options.includeDocuments())textBytes+=named.queryForObject("select coalesce(sum(octet_length(content::text)+octet_length(title)),0) from pages where owner_id=:owner and id in(:documents)",params,Long.class);
  if(textBytes>(options.format().equals("json")?DataExportWriter.JSON_LIMIT:DataExportWriter.TEXT_LIMIT))tooLarge("Document/task text exceeds the selected format's size limit.");
  String workspaceId=workspaceId(user);
  ObjectNode root=json.createObjectNode().put("format","deadlinr").put("schemaVersion",1).put("sourceWorkspaceId",workspaceId).put("exportedAt",Instant.now().toString());
  root.set("scope",json.valueToTree(options));
  Set<Long> courses=new TreeSet<>();Set<String> exported=new HashSet<>(),references=new TreeSet<>();
  ArrayNode tasks=root.putArray("tasks");
  named.query("select * from work_items where owner_id=:owner and id in(:tasks) order by id",params,(ResultSet r)->{
   ObjectNode task=tasks.addObject();task.put("id",id(workspaceId,"work",r.getObject("id")));exported.add(task.path("id").asText());
   for(String field:List.of("kind","title","description","status","location","link"))task.put(field,r.getString(field));
   task.put("priority",r.getInt("priority")).put("position",r.getDouble("position")).put("allDay",r.getBoolean("all_day")).put("hardDeadline",r.getBoolean("hard_deadline")).put("repeatRule",r.getString("repeat_rule")).put("timeZone",r.getString("time_zone")).put("sourceRevision",r.getLong("version"));
   ref(task,"parentId",workspaceId,"work",r.getObject("parent_id"),references);ref(task,"documentId",workspaceId,"document",r.getObject("page_id"),references);ref(task,"courseId",workspaceId,"course",r.getObject("course_id"),references);
   if(r.getObject("course_id")!=null)courses.add(r.getLong("course_id"));
   task.set("labels",parse(r.getString("labels")));task.set("reminderMinutes",parse(r.getString("reminder_minutes")));
   for(var pair:List.of("dueAt:due_at","startAt:start_at","completedAt:completed_at","archivedAt:archived_at","createdAt:created_at","updatedAt:updated_at","completionDueAt:completion_due_at")){var parts=pair.split(":");instant(task,parts[0],r,parts[1]);}
   nullable(task,"archiveBatchId",r.getObject("archive_batch_id"));
  });
  ArrayNode documents=root.putArray("documents");
  named.query("select * from pages where owner_id=:owner and id in(:documents) order by id",params,(ResultSet r)->{
   ObjectNode document=documents.addObject();document.put("id",id(workspaceId,"document",r.getObject("id")));exported.add(document.path("id").asText());
   document.put("title",r.getString("title")).put("favorite",r.getBoolean("favorite")).put("position",r.getDouble("position")).put("editor","blocknote");
   nullable(document,"icon",r.getString("icon"));nullable(document,"coverStyle",r.getString("cover_style"));nullable(document,"archiveBatchId",r.getObject("archive_batch_id"));document.set("content",parse(r.getString("content")));
   ref(document,"parentId",workspaceId,"document",r.getObject("parent_id"),references);ref(document,"courseId",workspaceId,"course",r.getObject("course_id"),references);if(r.getObject("course_id")!=null)courses.add(r.getLong("course_id"));
   var legacy=document.putObject("legacyLocalTimestamps").putNull("timeZone");for(String key:List.of("created_at","updated_at","archived_at"))local(legacy,camel(key),r,key);
  });
  ArrayNode courseRows=root.putArray("courses");
  if(!courses.isEmpty())named.query("select * from courses where user_id=:owner and id in(:courses) order by id",Map.of("owner",user.getId(),"courses",courses),(ResultSet r)->{
   var course=courseRows.addObject().put("id",id(workspaceId,"course",r.getObject("id"))).put("name",r.getString("name")).put("code",r.getString("code"));nullable(course,"instructor",r.getString("instructor"));local(course.putObject("legacyLocalTimestamps").putNull("timeZone"),"createdAt",r,"created_at");exported.add(course.path("id").asText());
  });
  var labels=root.putArray("labels");
  db.query("select name,color from labels where owner_id=? order by name limit 2001",(ResultSet r)->{labels.addObject().put("name",r.getString("name")).put("color",r.getString("color"));},user.getId());
  if(labels.size()>RECORD_LIMIT)tooLarge("Export supports at most 2,000 label definitions.");
  var dependencies=root.putArray("dependencies");
  named.query("select d.item_id,d.depends_on_id from work_dependencies d join work_items w on w.id=d.item_id join work_items target on target.id=d.depends_on_id where w.owner_id=:owner and target.owner_id=:owner and d.item_id in(:tasks) order by d.item_id,d.depends_on_id limit 10001",params,(ResultSet r)->{
   var dependency=dependencies.addObject();ref(dependency,"itemId",workspaceId,"work",r.getObject("item_id"),references);ref(dependency,"dependsOnId",workspaceId,"work",r.getObject("depends_on_id"),references);
  });
  if(dependencies.size()>10000)tooLarge("Export supports at most 10,000 task dependencies.");
  List<DataExportWriter.ExportFile> exportFiles=new ArrayList<>();
  if(options.includeAttachments())named.query("select *"+FILE_WHERE+" order by id limit 101",params,(ResultSet r)->{
   UUID fileId=r.getObject("id",UUID.class);var metadata=json.createObjectNode().put("id",id(workspaceId,"attachment",fileId)).put("name",r.getString("name")).put("contentType",r.getString("content_type")).put("size",r.getLong("size"));
   ref(metadata,"itemId",workspaceId,"work",r.getObject("item_id"),references);ref(metadata,"documentId",workspaceId,"document",r.getObject("page_id"),references);instant(metadata,"uploadedAt",r,"uploaded_at");exportFiles.add(new DataExportWriter.ExportFile(metadata,files.path(fileId)));
  });
  if(exportFiles.size()>ATTACHMENT_LIMIT)tooLarge("Export supports at most 100 attachments.");
  long encodedBytes=0;for(var file:exportFiles){long size=file.metadata().path("size").asLong();if(size>DataExportWriter.FILE_LIMIT)tooLarge("An attachment exceeds 20 MiB.");encodedBytes+=4*((size+2)/3);}
  if(encodedBytes+textBytes>DataExportWriter.JSON_LIMIT)tooLarge("JSON export exceeds 50 MiB. Try excluding attachments or documents.");
  var external=root.putArray("externalReferences");references.stream().filter(reference->!exported.contains(reference)).forEach(external::add);
  root.putArray("warnings").add("Only TASK work items are included; calendar events are not exported.").add("Page/course audit timestamps are timezone-unknown local values, preserved in legacyLocalTimestamps.");
  if(!external.isEmpty())((ArrayNode)root.get("warnings")).add("Some relationships refer to records outside the selected scope; externalReferences lists their IDs.");
  return writer.write(root,options.format(),exportFiles);
 }
 private String workspaceId(User user){
  var existing=db.queryForList("select preferences->>? from user_settings where owner_id=?",String.class,ID_KEY,user.getId());
  if(!existing.isEmpty()&&existing.getFirst()!=null){try{return UUID.fromString(existing.getFirst()).toString();}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Stored export identity is invalid. Existing identity was not replaced.");}}
  String value=UUID.randomUUID().toString();
  db.update("insert into user_settings(owner_id,preferences)values(?,jsonb_build_object(cast(? as text),cast(? as text))) on conflict(owner_id) do update set preferences=user_settings.preferences||excluded.preferences",user.getId(),ID_KEY,value);return value;
 }
 private JsonNode parse(String value){try{return value==null?NullNode.instance:json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid stored document data",e);}}
 private static void tooLarge(String message){throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,message);}
 private static String id(String workspace,String type,Object value){return workspace+":"+type+":"+value;}
 private static void nullable(ObjectNode node,String key,Object value){if(value==null)node.putNull(key);else node.put(key,value.toString());}
 private static void ref(ObjectNode node,String key,String workspace,String type,Object value,Set<String> references){if(value==null)node.putNull(key);else{String id=id(workspace,type,value);node.put(key,id);references.add(id);}}
 private static void instant(ObjectNode node,String key,ResultSet row,String column)throws SQLException{Timestamp value=row.getTimestamp(column);nullable(node,key,value==null?null:value.toInstant());}
 private static void local(ObjectNode node,String key,ResultSet row,String column)throws SQLException{nullable(node,key,row.getObject(column,LocalDateTime.class));}
 private static String camel(String key){return switch(key){case "created_at"->"createdAt";case "updated_at"->"updatedAt";default->"archivedAt";};}
}
