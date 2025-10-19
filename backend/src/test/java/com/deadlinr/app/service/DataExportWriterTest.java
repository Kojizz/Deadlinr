package com.deadlinr.app.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DataExportWriterTest {
 final ObjectMapper json=new ObjectMapper();final DataExportWriter writer=new DataExportWriter(json);
 @TempDir Path directory;
 ObjectNode snapshot(){var root=json.createObjectNode().put("sourceWorkspaceId","demo").put("exportedAt","2026-10-12T05:00:00Z");root.putArray("tasks");root.putArray("documents");return root;}
 String text(byte[] bytes){return new String(bytes,StandardCharsets.UTF_8);}
 @Test void csvRoundTripsQuotesCommasLineBreaksUnicodeAndEmptyValues()throws Exception{
  var root=snapshot();var task=root.withArray("tasks").addObject().put("title","Lab, \"résumé\" 📚").put("description","First\r\nSecond\nThird").putNull("dueAt").put("priority",1);
  task.putArray("labels").add("a,b").add("uni");
  var rows=readCsv(text(writer.write(root,"csv",List.of())));
  assertThat(rows).hasSize(2);assertThat(rows.getFirst()).isEqualTo(DataExportWriter.CSV_COLUMNS);
  assertThat(rows.get(1)).hasSize(rows.getFirst().size());
  assertThat(rows.get(1).get(2)).isEqualTo(task.path("title").asText());assertThat(rows.get(1).get(3)).isEqualTo(task.path("description").asText());assertThat(rows.get(1).get(6)).isEmpty();assertThat(json.readTree(rows.get(1).get(11))).isEqualTo(task.path("labels"));
 }
 @Test void spreadsheetFormulasAndHiddenPrefixesBecomeText(){
  for(String value:List.of("=1+1","+SUM(1)","-2","@SUM(A1)","  =1","\t@cmd","\r\n+1","\uFEFF=2","\u200B-1","\u00A0=3","\tplain"))assertThat(readCsv(DataExportWriter.csvCell(value)+"\r\n").getFirst().getFirst()).isEqualTo("'"+value);
  for(String value:List.of("ordinary","'already text","text = formula","2026-10-16T13:00:00Z",""))assertThat(readCsv(DataExportWriter.csvCell(value)+"\r\n").getFirst().getFirst()).isEqualTo(value);
 }
 @Test void jsonPreservesEveryFieldAndAttachmentBytes()throws Exception{
  var root=snapshot();root.put("schemaVersion",1);root.withArray("tasks").addObject().put("dueAt","2026-11-01T05:30:00Z").put("timeZone","America/New_York").put("title","=SUM(1)");
  byte[] bytes="Study notes\nҚазақша".getBytes(StandardCharsets.UTF_8);Path file=directory.resolve("notes");Files.write(file,bytes);
  var metadata=json.createObjectNode().put("id","file-1").put("name","notes.txt").put("size",bytes.length);
  var result=json.readTree(writer.write(root,"json",List.of(new DataExportWriter.ExportFile(metadata,file))));
  assertThat(result.path("tasks")).isEqualTo(root.path("tasks"));assertThat(result.path("schemaVersion").asInt()).isEqualTo(1);
  var attachment=result.path("attachments").get(0);assertThat(Base64.getDecoder().decode(attachment.path("data").asText())).isEqualTo(bytes);
  assertThat(attachment.path("sha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
  assertThat(root.has("attachments")).isFalse();assertThat(metadata.has("data")).isFalse();
 }
 @Test void missingChangedOrOversizedAttachmentsNeverProducePartialDownloads()throws Exception{
  Path file=directory.resolve("notes");var metadata=json.createObjectNode().put("name","notes.txt").put("size",1);var files=List.of(new DataExportWriter.ExportFile(metadata,file));
  assertStatus(409,()->writer.write(snapshot(),"json",files));Files.writeString(file,"different size");assertStatus(409,()->writer.write(snapshot(),"json",files));
  try(var output=Files.newOutputStream(file)){byte[] block=new byte[1024*1024];for(int i=0;i<21;i++)output.write(block);}
  assertStatus(413,()->writer.write(snapshot(),"json",files));
 }
 @Test void formatAndOutputLimitsAreEnforced(){
  assertThatThrownBy(()->writer.write(snapshot(),"xml",List.of())).isInstanceOf(IllegalArgumentException.class);
  var root=snapshot();root.withArray("tasks").addObject().put("title","x").put("description","x".repeat(DataExportWriter.TEXT_LIMIT));
  assertStatus(413,()->writer.write(root,"csv",List.of()));assertStatus(413,()->writer.write(root,"markdown",List.of()));
 }
 @Test void markdownEscapesActiveContentAndRendersDocumentBlocks()throws Exception{
  var root=snapshot();root.putObject("scope").put("includeDocuments",true);root.withArray("tasks").addObject().put("title","<script>alert(1)</script>\n- [ ] fake").put("status","DONE").put("dueAt","2026-10-16T13:00:00Z").put("timeZone","Asia/Qyzylorda");
  root.withArray("documents").addObject().put("title","Lecture").set("content",json.readTree("[{\"type\":\"heading\",\"props\":{\"level\":1},\"content\":[{\"text\":\"Notes\"}]},{\"type\":\"table\",\"content\":{\"rows\":[{\"cells\":[[{\"text\":\"Topic\"}],[{\"text\":\"Date\"}]]}]}}]"));
  String result=text(writer.write(root,"markdown",List.of()));assertThat(result).contains("- [x] &lt;script&gt;","#### Notes","Topic | Date","Asia/Qyzylorda").doesNotContain("<script>","\n- [ ] fake");
 }
 @Test void checkedInJsonAndCsvSamplesStayCompatible()throws Exception{
  var root=(ObjectNode)json.readTree(Files.readString(Path.of("../samples/deadlinr-export-v1.json")));
  var attachments=new ArrayList<DataExportWriter.ExportFile>();
  for(var attachment:root.path("attachments")){byte[] data=Base64.getDecoder().decode(attachment.path("data").asText());Path file=directory.resolve("sample-"+attachments.size());Files.write(file,data);var metadata=((ObjectNode)attachment).deepCopy();metadata.remove(List.of("data","encoding","sha256"));attachments.add(new DataExportWriter.ExportFile(metadata,file));}
  assertThat(json.readTree(writer.write(root,"json",attachments))).isEqualTo(root);
  assertThat(readCsv(text(writer.write(root,"csv",List.of())))).isEqualTo(readCsv(Files.readString(Path.of("../samples/deadlinr-tasks.csv"))));
  assertThat(text(writer.write(root,"markdown",List.of())).replace("\r\n","\n")).isEqualTo(Files.readString(Path.of("../samples/deadlinr-readable.txt")).replace("\r\n","\n"));
 }
 static void assertStatus(int status,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(status));}
 // An independent CSV reader checks field boundaries, escaped quotes and CRLF.
 static List<List<String>> readCsv(String text){
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}
   else if(!quoted&&(c==','||c=='\r'||c=='\n')){row.add(cell.toString());cell.setLength(0);if(c!=','){rows.add(row);row=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;}}else cell.append(c);}
  assertThat(quoted).isFalse();if(!row.isEmpty()||!cell.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
 }
}
