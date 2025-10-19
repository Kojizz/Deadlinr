package com.deadlinr.app.service;

import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class DataExportIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService work;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired FileService files;
 @org.junit.jupiter.api.io.TempDir static Path folder;
 @org.springframework.test.context.DynamicPropertySource static void storage(org.springframework.test.context.DynamicPropertyRegistry r){r.add("app.storage.path",()->folder.toString());}
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("private").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("private").role(Role.STUDENT).build());}
 long task(User owner,String title)throws Exception{return work.create(owner,json.createObjectNode().put("title",title)).id();}
 JsonNode export(User owner,boolean documents,boolean attachments,boolean archived)throws Exception{
  return json.readTree(mvc.perform(post("/api/data/export").with(user(owner)).contentType("application/json").content(options("json",documents,attachments,archived)))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.endsWith(".json\""))).andReturn().getResponse().getContentAsByteArray());
 }
 String options(String format,boolean documents,boolean attachments,boolean archived){return json.createObjectNode().put("format",format).put("includeDocuments",documents).put("includeAttachments",attachments).put("includeArchived",archived).toString();}
 @Test void completeOwnedSnapshotIncludesDocumentsRelationshipsAndAttachmentBytes()throws Exception{
  long course=db.queryForObject("insert into courses(user_id,name,code,instructor,created_at)values(?,'Databases','CS301','Dr Demo','2026-09-01 08:00')returning id",Long.class,alice.getId());
  String content="[{\"id\":\"block-1\",\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Detailed notes\",\"styles\":{\"bold\":true}}],\"children\":[]}]";
  long page=db.queryForObject("insert into pages(owner_id,course_id,title,content,favorite,position,created_at,updated_at)values(?,?,'Syllabus',cast(? as jsonb),true,1.5,'2026-09-01 08:00','2026-10-12 12:00')returning id",Long.class,alice.getId(),course,content);
  long parent=task(alice,"Final report"),child=task(alice,"Read rubric"),foreign=task(bob,"Private other account");String description="Full description ".repeat(30);
  db.update("update work_items set description=?,page_id=?,course_id=?,labels='[\"uni\"]',reminder_minutes='[60,1440]',hard_deadline=true,repeat_rule='WEEKLY',position=1.25,version=7 where id=?",description,page,course,parent);
  db.update("update work_items set parent_id=?,status='DONE',completed_at='2026-10-11T08:00:00Z' where id=?",parent,child);
  db.update("insert into work_dependencies(item_id,depends_on_id)values(?,?)",parent,child);db.update("insert into labels(owner_id,name,color)values(?,'uni','#2563eb')",alice.getId());
  files.upload(alice,parent,new org.springframework.mock.web.MockMultipartFile("file","notes.txt","text/plain","lecture notes".getBytes()));
  files.upload(bob,foreign,new org.springframework.mock.web.MockMultipartFile("file","private.txt","text/plain","secret".getBytes()));
  JsonNode result=export(alice,true,true,false);String source=result.path("sourceWorkspaceId").asText();UUID.fromString(source);
  assertThat(result.path("schemaVersion").asInt()).isEqualTo(1);assertThat(result.path("tasks").size()).isEqualTo(2);assertThat(result.toString()).doesNotContain("Private other account","private.txt","secret","password","ownerId");
  JsonNode first=result.path("tasks").get(0);assertThat(first.path("description").asText()).isEqualTo(description);assertThat(first.path("sourceRevision").asLong()).isEqualTo(7);assertThat(first.path("hardDeadline").asBoolean()).isTrue();assertThat(first.path("repeatRule").asText()).isEqualTo("WEEKLY");assertThat(first.path("reminderMinutes").toString()).isEqualTo("[60,1440]");
  assertThat(first.path("documentId").asText()).isEqualTo(source+":document:"+page);assertThat(first.path("courseId")).isEqualTo(result.path("courses").get(0).path("id"));
  assertThat(result.path("tasks").get(1).path("parentId")).isEqualTo(first.path("id"));assertThat(result.path("dependencies").get(0).path("dependsOnId")).isEqualTo(result.path("tasks").get(1).path("id"));
  assertThat(result.path("documents").get(0).path("content")).isEqualTo(json.readTree(content));assertThat(result.path("documents").get(0).path("legacyLocalTimestamps").path("createdAt").asText()).isEqualTo("2026-09-01T08:00");assertThat(result.path("documents").get(0).path("legacyLocalTimestamps").path("timeZone").isNull()).isTrue();
  assertThat(result.path("labels").get(0).path("color").asText()).isEqualTo("#2563eb");assertThat(result.path("attachments").size()).isEqualTo(1);assertThat(Base64.getDecoder().decode(result.path("attachments").get(0).path("data").asText())).isEqualTo("lecture notes".getBytes());
  assertThat(result.path("externalReferences").size()).isZero();
  JsonNode scoped=export(alice,false,false,false);assertThat(scoped.path("documents").size()).isZero();assertThat(scoped.path("attachments").size()).isZero();assertThat(scoped.path("externalReferences").toString()).contains(source+":document:"+page);
 }
 @Test void utcDatesRoundTripAcrossMachineZonesIncludingDstAndDateOnly()throws Exception{
  var examples=List.of(new String[]{"America/New_York","2026-11-01T05:30:00Z","2026-11-01T01:30"},new String[]{"Asia/Qyzylorda","2026-10-16T18:59:00Z","2026-10-16T23:59"},new String[]{"Pacific/Auckland","2026-10-15T20:00:00Z","2026-10-16T09:00"});
  for(var example:examples){long id=task(alice,example[0]);db.update("update work_items set due_at=cast(? as timestamptz),start_at=cast(? as timestamptz),time_zone=?,all_day=? where id=?",example[1],Instant.parse(example[1]).minusSeconds(3600).toString(),example[0],example[0].equals("Asia/Qyzylorda"),id);}
  TimeZone previous=TimeZone.getDefault();try{for(String machine:List.of("UTC","America/Los_Angeles","Asia/Tokyo")){TimeZone.setDefault(TimeZone.getTimeZone(machine));var result=json.readTree(json.writeValueAsBytes(export(alice,false,false,false)));
   for(int i=0;i<examples.size();i++){var example=examples.get(i);var task=result.path("tasks").get(i);assertThat(Instant.parse(task.path("dueAt").asText())).isEqualTo(Instant.parse(example[1]));assertThat(Instant.parse(task.path("dueAt").asText()).atZone(ZoneId.of(task.path("timeZone").asText())).toLocalDateTime().toString()).isEqualTo(example[2]);assertThat(Instant.parse(task.path("startAt").asText())).isEqualTo(Instant.parse(example[1]).minusSeconds(3600));}
  }}finally{TimeZone.setDefault(previous);}
 }
 @Test void identityPersistsAcrossSettingsEditsFormatsAndOwners()throws Exception{
  task(alice,"Identity");var first=export(alice,false,false,false);String identity=first.path("sourceWorkspaceId").asText();
  mvc.perform(put("/api/settings").with(user(alice)).contentType("application/json").content("{\"weeklyGoal\":15}")).andExpect(status().isOk()).andExpect(jsonPath("$._dataWorkspaceId").doesNotExist());
  mvc.perform(put("/api/settings").with(user(alice)).contentType("application/json").content("{\"_dataWorkspaceId\":\"replacement\"}")).andExpect(status().isBadRequest());
  var second=export(alice,false,false,false);assertThat(second.path("sourceWorkspaceId").asText()).isEqualTo(identity);assertThat(second.path("tasks")).isEqualTo(first.path("tasks"));assertThat(export(bob,false,false,false).path("sourceWorkspaceId").asText()).isNotEqualTo(identity);
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content(options("csv",true,true,false))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(identity)));
 }
 @Test void completedAndArchivedSelectionIsExplicitAndNotPaginated()throws Exception{
  db.update("insert into work_items(owner_id,title)select ?,'Task '||n from generate_series(1,205)n",alice.getId());
  long archived=task(alice,"Trash");work.archive(alice,archived);db.update("update work_items set status='DONE' where owner_id=? and title='Task 1'",alice.getId());
  db.update("insert into work_items(owner_id,title,kind)values(?,'Calendar event','EVENT')",alice.getId());
  assertThat(export(alice,false,false,false).path("tasks").size()).isEqualTo(205);assertThat(export(alice,false,false,true).path("tasks").size()).isEqualTo(206);
  mvc.perform(get("/api/data/summary").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.tasks").value(205)).andExpect(jsonPath("$.withinLimits").value(true));
 }
 @Test void badRequestsAndRowLimitsAreRejectedOnTheServer()throws Exception{
  mvc.perform(post("/api/data/export").contentType("application/json").content(options("json",true,true,false))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content("{oops")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content(options("exe",true,true,false))).andExpect(status().isBadRequest());
  db.update("insert into work_items(owner_id,title)select ?,'Task '||n from generate_series(1,2001)n",alice.getId());
  mvc.perform(get("/api/data/summary").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.withinLimits").value(false)).andExpect(jsonPath("$.tasks").value(2001));
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content(options("json",false,false,false))).andExpect(status().isPayloadTooLarge());
  assertThat(db.queryForObject("select count(*) from user_settings where owner_id=?",Long.class,alice.getId())).isZero();
 }
 @Test void unavailableFilesCanBeExcludedAndOversizedTextFails()throws Exception{
  long id=task(alice,"Files");var attachment=files.upload(alice,id,new org.springframework.mock.web.MockMultipartFile("file","notes.txt","text/plain","notes".getBytes()));Files.delete(files.path(attachment.id()));
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content(options("json",true,true,false))).andExpect(status().isConflict());assertThat(export(alice,true,false,false).path("tasks").size()).isEqualTo(1);
  db.update("update work_items set description=repeat('x',?) where id=?",DataExportWriter.TEXT_LIMIT+1,id);
  mvc.perform(post("/api/data/export").with(user(alice)).contentType("application/json").content(options("csv",false,false,false))).andExpect(status().isPayloadTooLarge());
 }
 @Test void openApiIncludesTheDownloadAndSummaryContract()throws Exception{
  byte[] spec=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/data/export'].post").exists()).andExpect(jsonPath("$.paths['/api/data/summary'].get").exists()).andReturn().getResponse().getContentAsByteArray();
  // A build artifact also lets the existing TS generator run without a live API.
  Files.write(Path.of("target/data-export-openapi.json"),spec);
 }
}
