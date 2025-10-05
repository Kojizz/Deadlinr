package com.deadlinr.app.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Component
public class DataExportWriter {
 public static final int JSON_LIMIT=50*1024*1024,TEXT_LIMIT=5*1024*1024,FILE_LIMIT=20*1024*1024;
 public record ExportFile(ObjectNode metadata,Path path){}
 private final ObjectMapper json;
 public DataExportWriter(ObjectMapper json){this.json=json;}
 public static String format(String value){
  if(value==null||!Set.of("json","csv","markdown").contains(value))throw new IllegalArgumentException("Choose JSON, CSV or Markdown.");
  return value;
 }
 public byte[] write(ObjectNode snapshot,String format,List<ExportFile> files){
  format(format);var output=new LimitedBuffer(format.equals("json")?JSON_LIMIT:TEXT_LIMIT);
  try{
   if(format.equals("json")){
    try(var generator=json.getFactory().createGenerator(output)){
     generator.useDefaultPrettyPrinter();generator.writeStartObject();
     var fields=snapshot.fields();while(fields.hasNext()){var field=fields.next();if(!field.getKey().equals("attachments")){generator.writeFieldName(field.getKey());json.writeTree(generator,field.getValue());}}
     generator.writeArrayFieldStart("attachments");
     for(var file:files){
      byte[] bytes;
      try{
       if(!Files.isRegularFile(file.path(),LinkOption.NOFOLLOW_LINKS))throw new IOException();
       try(var input=Files.newInputStream(file.path())){bytes=input.readNBytes(FILE_LIMIT+1);}
       if(bytes.length>FILE_LIMIT)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"An attachment exceeds 20 MiB.");
       if(bytes.length!=file.metadata().path("size").asLong())throw new IOException();
      }catch(IOException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Attachment unavailable or changed: "+file.metadata().path("name").asText()+". Retry or export without attachments.");}
      generator.writeStartObject();var metadata=file.metadata().fields();while(metadata.hasNext()){var field=metadata.next();generator.writeFieldName(field.getKey());json.writeTree(generator,field.getValue());}
      generator.writeStringField("encoding","base64");
      generator.writeStringField("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      generator.writeFieldName("data");generator.writeBinary(bytes);generator.writeEndObject();
     }
     generator.writeEndArray();generator.writeEndObject();
    }
   }else{
    try(var writer=new OutputStreamWriter(output,StandardCharsets.UTF_8)){
     if(format.equals("csv"))csv(snapshot,writer);else markdown(snapshot,writer);
    }
   }
   return output.toByteArray();
  }catch(Exception e){
   Throwable cause=e;while(cause.getCause()!=null&&!(cause instanceof ResponseStatusException)&&!(cause instanceof SizeLimit))cause=cause.getCause();
   if(cause instanceof ResponseStatusException status)throw status;
   if(cause instanceof SizeLimit)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Export exceeds the format's size limit. Reduce its scope; no partial file was created.");
   throw new IllegalStateException("Could not encode export",e);
  }
 }
 public static final List<String> CSV_COLUMNS=List.of("sourceWorkspaceId","id","title","description","status","priority","dueAt","startAt","timeZone","allDay","hardDeadline","labels","parentId","documentId","courseId","repeatRule","reminderMinutes","location","link","position","completedAt","archivedAt","createdAt","updatedAt","completionDueAt");
 private void csv(JsonNode root,Writer writer)throws IOException{
  writer.write(String.join(",",CSV_COLUMNS)+"\r\n");
  for(var task:root.path("tasks")){
   List<String> cells=new ArrayList<>();
   for(String name:CSV_COLUMNS){var value=name.equals("sourceWorkspaceId")?root.path(name):task.path(name);cells.add(csvCell(value.isNull()||value.isMissingNode()?"":value.isContainerNode()?value.toString():value.asText()));}
   writer.write(String.join(",",cells)+"\r\n");
  }
 }
 public static String csvCell(String value){
  // Quote every field. Inspect past whitespace/controls so hidden prefixes
  // cannot turn exported student text into a spreadsheet formula.
  int first=0;boolean controls=false;
  while(first<value.length()){
   char c=value.charAt(first);
   if(Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)||Character.getType(c)==Character.FORMAT){controls|=Character.isISOControl(c);first++;}else break;
  }
  if(controls||(first<value.length()&&"=+-@".indexOf(value.charAt(first))>=0))value="'"+value;
  return "\""+value.replace("\"","\"\"")+"\"";
 }
 private void markdown(JsonNode root,Writer writer)throws IOException{
  writer.write("# Deadlinr export\n\nExported: "+root.path("exportedAt").asText()+"\n\nReadable copy. Use JSON to preserve all fields, document blocks and attachment bytes.\n\n## Tasks\n\n");
  if(root.path("tasks").isEmpty())writer.write("No tasks in this selection.\n\n");
  for(var task:root.path("tasks")){
   writer.write("- ["+(task.path("status").asText().equals("DONE")?"x":" ")+"] "+line(task.path("title").asText())+"\n");
   writer.write("  - ID: "+line(task.path("id").asText())+"\n  - Status: "+line(task.path("status").asText())+" · Priority: P"+task.path("priority").asInt()+"\n");
   for(String field:List.of("dueAt","startAt","timeZone","parentId","documentId","repeatRule","archivedAt"))if(task.hasNonNull(field))writer.write("  - "+field+": "+line(task.path(field).asText())+"\n");
   if(task.path("allDay").asBoolean())writer.write("  - Date only (deadline instant retained)\n");
   if(!task.path("labels").isEmpty())writer.write("  - Labels: "+line(task.path("labels").toString())+"\n");
   for(String line:task.path("description").asText().split("\\R",-1))writer.write("    "+line(line)+"\n");
   writer.write("\n");
  }
  if(root.path("scope").path("includeDocuments").asBoolean()){
   writer.write("## Documents\n\n");if(root.path("documents").isEmpty())writer.write("No documents in this selection.\n\n");
   for(var document:root.path("documents")){
    writer.write("### "+line(document.path("title").asText())+"\n\nID: "+line(document.path("id").asText())+"\n\n");
    if(document.hasNonNull("parentId"))writer.write("Parent: "+line(document.path("parentId").asText())+"\n\n");
    blocks(document.path("content"),writer,0);
   }
  }
 }
 private static String line(String value){
  return value.replaceAll("[\\r\\n\\p{Cntrl}]+"," ").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\\","\\\\").replaceAll("([`*_{}\\[\\]()#+.!|~-])","\\\\$1");
 }
 private static String inline(JsonNode content){
  if(content.isTextual())return content.asText();
  if(content.isArray()){StringBuilder out=new StringBuilder();content.forEach(c->out.append(inline(c)));return out.toString();}
  if(content.isObject()){if(content.has("text"))return content.path("text").asText();if(content.has("content"))return inline(content.path("content"));}
  return "";
 }
 private static void blocks(JsonNode blocks,Writer writer,int depth)throws IOException{
  if(blocks.isNull()||blocks.isMissingNode())return;
  if(depth>20){writer.write("[Deeply nested content omitted from readable copy; retained in JSON.]\n\n");return;}
  if(!blocks.isArray()){writer.write("[Document content retained in JSON.]\n\n");return;}
  for(var block:blocks){
   String type=block.path("type").asText(),text=line(inline(block.path("content"))),indent="  ".repeat(depth);
   switch(type){
    case "heading"->writer.write("#".repeat(Math.clamp(block.path("props").path("level").asInt(1)+3,4,6))+" "+text+"\n\n");
    case "bulletListItem"->writer.write(indent+"- "+text+"\n");
    case "numberedListItem"->writer.write(indent+"1. "+text+"\n");
    case "checkListItem"->writer.write(indent+"- ["+(block.path("props").path("checked").asBoolean()?"x":" ")+"] "+text+"\n");
    case "table"->{for(var row:block.path("content").path("rows")){List<String> cells=new ArrayList<>();for(var cell:row.path("cells"))cells.add(line(inline(cell)));writer.write(String.join(" | ",cells)+"\n");}writer.write("\n");}
    case "paragraph"->writer.write(indent+text+"\n\n");
    default->writer.write(indent+"["+line(type)+"] "+text+"\n\n");
   }
   blocks(block.path("children"),writer,depth+1);
  }
 }
 private static class SizeLimit extends RuntimeException{}
 private static class LimitedBuffer extends ByteArrayOutputStream{
  private final int limit;LimitedBuffer(int limit){this.limit=limit;}
  @Override public synchronized void write(int value){if(count>=limit)throw new SizeLimit();super.write(value);}
  @Override public synchronized void write(byte[] bytes,int offset,int length){if((long)count+length>limit)throw new SizeLimit();super.write(bytes,offset,length);}
 }
}
