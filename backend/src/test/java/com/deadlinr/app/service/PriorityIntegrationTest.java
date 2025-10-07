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
class PriorityIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;@Autowired NotificationService notifications;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(String body)throws Exception{String response=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("id").asLong();}

 @Test void hardDeadlineLadderCapConfirmationAndCompletion()throws Exception{
  mvc.perform(put("/api/settings").with(user(alice)).contentType("application/json").content("{\"escalationCap\":2}")).andExpect(status().isOk());
  String due=Instant.now().minusSeconds(2).toString();long id=create("{\"title\":\"Critical exam\",\"priority\":1,\"hardDeadline\":true,\"dueAt\":\""+due+"\",\"reminderMinutes\":[]}");
  assertThat(db.queryForObject("select count(*) from reminders where item_id=? and category='ESCALATION'",Integer.class,id)).isEqualTo(7);
  notifications.deliver();notifications.deliver();
  mvc.perform(get("/api/notifications").with(user(alice))).andExpect(jsonPath("$[0].body").value("Critical · Hard deadline escalation"));
  assertThat(db.queryForObject("select count(*) from notifications where item_id=?",Integer.class,id)).isEqualTo(1);
  String patch="{\"dueAt\":\""+Instant.now().plusSeconds(3600)+"\"}";
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content(patch)).andExpect(status().isConflict());
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content(patch.replace("}",",\"confirmMove\":true}"))).andExpect(status().isOk());
  create("{\"title\":\"Low\",\"priority\":4}");mvc.perform(get("/api/work-items?priorities=1&hardOnly=true").with(user(alice))).andExpect(jsonPath("$.total").value(1));
  mvc.perform(patch("/api/work-items/"+id).with(user(alice)).contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("select count(*) from reminders where item_id=? and sent_at is null",Integer.class,id)).isZero();
 }
 @Test void settingsAndSavedViewsAreScopedAndValidated()throws Exception{
  mvc.perform(put("/api/settings").with(user(alice)).contentType("application/json").content("{\"urgentDays\":5}")).andExpect(status().isOk());
  mvc.perform(get("/api/settings").with(user(bob))).andExpect(jsonPath("$.urgentDays").value(2));
  mvc.perform(put("/api/settings").with(user(alice)).contentType("application/json").content("{\"urgentDays\":0}")).andExpect(status().isBadRequest());
  String result=mvc.perform(post("/api/views").with(user(alice)).contentType("application/json").content("{\"name\":\"Urgent labs\",\"filters\":{\"priorities\":\"1\",\"label\":\"ml\"}}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  String id=json.readTree(result).get("id").asText();mvc.perform(get("/api/views").with(user(alice))).andExpect(jsonPath("$.length()").value(1));mvc.perform(get("/api/views").with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(delete("/api/views/"+id).with(user(bob))).andExpect(status().isNotFound());mvc.perform(delete("/api/views/"+id).with(user(alice))).andExpect(status().isNoContent());
 }
}
