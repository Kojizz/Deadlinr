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
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class SmartPlanningIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;@Autowired NotificationService notifications;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(String body)throws Exception{String response=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("id").asLong();}

 @Autowired DigestService digest;@Autowired PlanningSettingsService settings;
 String postJson(String path,User u,String body)throws Exception{return mvc.perform(post(path).with(user(u)).contentType("application/json").content(body)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();}
 @Test void labelsTemplatesDependenciesQueryAndAllEndpointOwnership()throws Exception{
  mvc.perform(put("/api/labels/ml").with(user(alice)).contentType("application/json").content("{\"color\":\"#2563eb\"}")).andExpect(status().isOk());mvc.perform(get("/api/labels").with(user(bob))).andExpect(jsonPath("$.length()").value(0));mvc.perform(get("/api/labels").with(user(alice))).andExpect(jsonPath("$[0].name").value("ml"));
  String due=LocalDate.now(ZoneId.of("Asia/Almaty")).atTime(18,0).atZone(ZoneId.of("Asia/Almaty")).toInstant().toString();long parent=create("{\"title\":\"Lab\",\"labels\":[\"ml\"],\"priority\":1,\"dueAt\":\""+due+"\"}");
  mvc.perform(get("/api/work-items").param("q","p1 & due:today & #ml").with(user(alice))).andExpect(jsonPath("$.total").value(1));
  String template=json.readTree(postJson("/api/templates",alice,"{\"name\":\"Lab report\",\"steps\":[{\"title\":\"Research\",\"offsetDays\":-3},{\"title\":\"Submit\",\"offsetDays\":0}]}")).get("id").asText();
  mvc.perform(get("/api/templates").with(user(alice))).andExpect(jsonPath("$.length()").value(1));mvc.perform(get("/api/templates").with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/templates/"+template+"/apply").with(user(bob)).contentType("application/json").content("{\"itemId\":"+parent+"}")).andExpect(status().isNotFound());
  var children=json.readTree(postJson("/api/templates/"+template+"/apply",alice,"{\"itemId\":"+parent+"}"));long research=children.get(0).get("id").asLong(),submit=children.get(1).get("id").asLong();assertThat(Instant.parse(children.get(0).get("dueAt").asText())).isEqualTo(Instant.parse(due).minusSeconds(3*86400));
  postJson("/api/work-items/"+submit+"/dependencies",alice,"{\"dependsOnId\":"+research+"}");assertThat(service.get(alice,submit).blocked()).isTrue();
  mvc.perform(post("/api/work-items/"+research+"/dependencies").with(user(alice)).contentType("application/json").content("{\"dependsOnId\":"+submit+"}")).andExpect(status().isBadRequest());
  mvc.perform(patch("/api/work-items/"+submit).with(user(alice)).contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/work-items/"+submit+"/dependencies").with(user(alice))).andExpect(jsonPath("$[0].id").value(research));mvc.perform(get("/api/work-items/"+submit+"/dependencies").with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(post("/api/work-items/"+submit+"/dependencies").with(user(bob)).contentType("application/json").content("{\"dependsOnId\":"+research+"}")).andExpect(status().isForbidden());mvc.perform(delete("/api/work-items/"+submit+"/dependencies/"+research).with(user(bob))).andExpect(status().isForbidden());mvc.perform(delete("/api/work-items/"+submit+"/dependencies/"+research).with(user(alice))).andExpect(status().isOk());
  mvc.perform(delete("/api/templates/"+template).with(user(bob))).andExpect(status().isNotFound());mvc.perform(delete("/api/templates/"+template).with(user(alice))).andExpect(status().isOk());
 }
 @Test void historyReversesRecurrenceAndBulkAndPreservesOwnership()throws Exception{
  long a=create("{\"title\":\"Weekly\",\"dueAt\":\""+Instant.now().plusSeconds(3600)+"\",\"repeatRule\":\"WEEKLY\"}");
  service.update(alice,a,json.readTree("{\"status\":\"DONE\"}"));assertThat(db.queryForObject("select count(*) from work_items where owner_id=? and archived_at is null",Integer.class,alice.getId())).isEqualTo(2);
  mvc.perform(get("/api/history").with(user(bob))).andExpect(jsonPath("$.canUndo").value(false));mvc.perform(post("/api/history/undo").with(user(bob))).andExpect(status().isBadRequest());
  postJson("/api/history/undo",alice,"{}");assertThat(service.get(alice,a).status()).isEqualTo("TODO");assertThat(db.queryForObject("select count(*) from work_items where owner_id=? and archived_at is null",Integer.class,alice.getId())).isEqualTo(1);
  postJson("/api/history/redo",alice,"{}");assertThat(service.get(alice,a).status()).isEqualTo("DONE");assertThat(db.queryForObject("select count(*) from work_items where owner_id=? and archived_at is null",Integer.class,alice.getId())).isEqualTo(2);
  long b=create("{\"title\":\"Other\"}");postJson("/api/work-items/bulk",alice,"{\"ids\":["+a+","+b+"],\"patch\":{\"priority\":1}}");postJson("/api/history/undo",alice,"{}");assertThat(service.get(alice,a).priority()).isEqualTo(3);assertThat(service.get(alice,b).priority()).isEqualTo(3);
  postJson("/api/history/undo",alice,"{}");assertThat(service.get(alice,b).archivedAt()).isNotNull();postJson("/api/history/redo",alice,"{}");assertThat(service.get(alice,b).archivedAt()).isNull();postJson("/api/history/redo",alice,"{}");assertThat(service.get(alice,b).priority()).isEqualTo(1);
 }
 @Test void matrixGroupsByImportanceAndUrgencyAndIsScoped()throws Exception{
  String soon=Instant.now().plusSeconds(3600).toString(),later=Instant.now().plusSeconds(10*86400).toString();
  long hard=create("{\"title\":\"Hard\",\"priority\":1,\"dueAt\":\""+soon+"\"}");create("{\"title\":\"Plan\",\"priority\":2,\"dueAt\":\""+later+"\"}");create("{\"title\":\"Trivial but close\",\"priority\":4,\"dueAt\":\""+soon+"\"}");create("{\"title\":\"Someday\",\"priority\":3}");long event=create("{\"title\":\"Exam\",\"kind\":\"EVENT\",\"priority\":1,\"startAt\":\""+soon+"\",\"dueAt\":\""+soon+"\"}");long done=create("{\"title\":\"Finished\",\"priority\":1,\"dueAt\":\""+soon+"\"}");service.update(alice,done,json.readTree("{\"status\":\"DONE\"}"));
  var r=mvc.perform(get("/api/matrix").param("urgentDays","2").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.doFirst.count").value(2)).andExpect(jsonPath("$.schedule.count").value(1)).andExpect(jsonPath("$.keepShort.count").value(1)).andExpect(jsonPath("$.considerLater.count").value(1)).andReturn().getResponse().getContentAsString();
  var tree=json.readTree(r);var doFirstIds=tree.get("doFirst").get("items").findValuesAsText("id");assertThat(doFirstIds).contains(String.valueOf(hard),String.valueOf(event));
  mvc.perform(get("/api/matrix").param("urgentDays","0").with(user(alice))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/matrix").with(user(bob))).andExpect(jsonPath("$.doFirst.count").value(0)).andExpect(jsonPath("$.considerLater.count").value(0));
 }
 @Test void smartCountsStatsAndDigestAreRealIdempotentAndScoped()throws Exception{
  long a=create("{\"title\":\"Inbox\"}"),b=create("{\"title\":\"On time\",\"dueAt\":\""+Instant.now().plusSeconds(3600)+"\"}");service.update(alice,b,json.readTree("{\"status\":\"DONE\"}"));
  mvc.perform(get("/api/smart-counts").with(user(alice))).andExpect(jsonPath("$.inbox").value(1)).andExpect(jsonPath("$.logbook").value(1));mvc.perform(get("/api/smart-counts").with(user(bob))).andExpect(jsonPath("$.logbook").value(0));
  mvc.perform(get("/api/stats").with(user(alice))).andExpect(jsonPath("$.week").value(1)).andExpect(jsonPath("$.completionRate").value(.5)).andExpect(jsonPath("$.onTimeRate").value(1.0));mvc.perform(get("/api/stats").with(user(bob))).andExpect(jsonPath("$.week").value(0));
  settings.save(alice,json.readTree("{\"dailyDigest\":true,\"weeklyReview\":false,\"digestTime\":\"00:00\",\"emailDigest\":false}"));digest.generate();digest.generate();assertThat(db.queryForObject("select count(*) from notifications where owner_id=? and title='Today''s plan'",Integer.class,alice.getId())).isEqualTo(1);assertThat(db.queryForObject("select count(*) from digest_deliveries where owner_id=? and email_pending",Integer.class,alice.getId())).isZero();
 }
}
