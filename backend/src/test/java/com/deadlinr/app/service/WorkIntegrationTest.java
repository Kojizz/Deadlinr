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
class WorkIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;@Autowired NotificationService notifications;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(String body)throws Exception{String response=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("id").asLong();}
 @Test void createsScheduledItemsAndFiltersAndOptimisticConflict()throws Exception{
  String due=Instant.now().plusSeconds(3600).toString();long id=create("{\"title\":\"Lab\",\"kind\":\"TASK\",\"dueAt\":\""+due+"\",\"labels\":[\"ml\"],\"priority\":1}");
  mvc.perform(get("/api/work-items?kind=TASK&priorities=1&label=ml").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].title").value("Lab"));
  mvc.perform(get("/api/workspace/summary").with(user(alice))).andExpect(jsonPath("$.openTasks").value(1));
  mvc.perform(get("/api/work-items/deadline-days").param("from",Instant.now().minusSeconds(86400).toString()).param("to",Instant.now().plusSeconds(86400).toString()).with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content("{\"title\":\"Stale\",\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content("{\"title\":\"bad\",\"priority\":9}")).andExpect(status().isBadRequest());
 }
 @Test void ownershipOnEveryWorkAndNotificationRoute()throws Exception{
  long id=create("{\"title\":\"Private\"}");
  mvc.perform(get("/api/work-items/"+id).with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(patch("/api/work-items/"+id).with(user(bob)).contentType("application/json").content("{\"title\":\"Attack\"}")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/work-items/"+id).with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(post("/api/work-items/"+id+"/restore").with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(get("/api/work-items").with(user(bob))).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/work-items/deadline-days").param("from",Instant.now().minusSeconds(86400).toString()).param("to",Instant.now().plusSeconds(86400).toString()).with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/work-items").with(user(bob)).contentType("application/json").content("{\"title\":\"Attack\",\"parentId\":"+id+"}")).andExpect(status().isForbidden());
  long notice=db.queryForObject("insert into notifications(owner_id,item_id,title)values(?,?,?)returning id",Long.class,alice.getId(),id,"Private notice");
  mvc.perform(get("/api/notifications").with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/notifications/"+notice+"/read").with(user(bob))).andExpect(status().isNotFound());
  mvc.perform(post("/api/notifications/"+notice+"/read").with(user(alice))).andExpect(status().isOk());
 }
 @Test void captureInstantsAndTimezoneSurviveDatabaseReload()throws Exception{
  for(String zone:List.of("Asia/Qyzylorda","America/New_York","Asia/Kathmandu")){
   var due=LocalDateTime.of(2026,10,16,18,0).atZone(ZoneId.of(zone)).toInstant();
   var start=due.minusSeconds(3600);
   var input=json.createObjectNode().put("title","Report").put("dueAt",due.toString()).put("startAt",start.toString()).put("timeZone",zone).put("priority",1);
   long id=create(input.toString());
   var loaded=service.get(alice,id);
   assertThat(loaded.dueAt()).isEqualTo(due);
   assertThat(loaded.startAt()).isEqualTo(start);
   assertThat(loaded.timeZone()).isEqualTo(zone);
   assertThat(loaded.dueAt().atZone(ZoneId.of(loaded.timeZone())).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026,10,16,18,0));
   mvc.perform(get("/api/work-items/"+id).with(user(alice))).andExpect(status().isOk())
    .andExpect(jsonPath("$.dueAt").value(due.toString())).andExpect(jsonPath("$.startAt").value(start.toString())).andExpect(jsonPath("$.timeZone").value(zone));
  }
 }
 @Test void completionRepeatsAndReminderDeliveryIsIdempotent()throws Exception{
  String due=Instant.now().minusSeconds(10).toString();long id=create("{\"title\":\"Weekly lab\",\"dueAt\":\""+due+"\",\"repeatRule\":\"WEEKLY\",\"reminderMinutes\":[0]}");
  notifications.deliver();notifications.deliver();
  assertThat(db.queryForObject("select count(*) from notifications where item_id=?",Integer.class,id)).isEqualTo(1);
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("select count(*) from work_items where owner_id=?",Integer.class,alice.getId())).isEqualTo(2);
  mvc.perform(delete("/api/work-items/"+id).with(user(alice))).andExpect(status().isNoContent());
  mvc.perform(post("/api/work-items/"+id+"/restore").with(user(alice))).andExpect(status().isOk());
  assertThat(service.get(alice,id).archivedAt()).isNull();
 }
}
