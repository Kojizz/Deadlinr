package com.deadlinr.app.service;
import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(TodayPlanningIntegrationTest.TimeConfig.class)
class TodayPlanningIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 static final AtomicReference<Instant> NOW=new AtomicReference<>();
 @TestConfiguration static class TimeConfig {
  @Bean @Primary Clock fixedPlanningClock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}public Instant instant(){return NOW.get();}};}
 }
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService work;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
 User alice,bob;
 @BeforeEach void setup(){NOW.set(Instant.parse("2026-10-13T05:00:00Z"));alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(User owner,String fields)throws Exception{return work.create(owner,json.readTree("{\"title\":\"Study\",\"reminderMinutes\":[],"+fields+"}")).id();}
 JsonNode plan(String zone,String view,int page,int size)throws Exception{return json.readTree(mvc.perform(get("/api/planning/today").with(user(alice)).param("zone",zone).param("view",view).param("page",String.valueOf(page)).param("size",String.valueOf(size))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void todayIncludesOverduePlannedAndInProgressAndOmitsEventsDoneArchivedAndOtherOwners()throws Exception{
  long overdue=create(alice,"\"dueAt\":\"2026-10-12T20:00:00Z\"");
  long planned=create(alice,"\"startAt\":\"2026-10-13T08:00:00Z\",\"dueAt\":\"2026-10-16T12:00:00Z\"");
  long ongoing=create(alice,"\"status\":\"IN_PROGRESS\"");
  create(alice,"\"kind\":\"EVENT\",\"dueAt\":\"2026-10-13T08:00:00Z\"");create(alice,"\"status\":\"DONE\",\"dueAt\":\"2026-10-13T08:00:00Z\"");
  long archived=create(alice,"\"dueAt\":\"2026-10-13T08:00:00Z\"");work.archive(alice,archived);
  create(alice,"\"status\":\"SOMEDAY\"");create(bob,"\"dueAt\":\"2026-10-13T08:00:00Z\"");
  var result=plan("Asia/Almaty","today",0,20);assertThat(result.path("total").asInt()).isEqualTo(3);
  assertThat(result.path("items").findValuesAsText("id")).containsExactly(String.valueOf(overdue),String.valueOf(planned),String.valueOf(ongoing));
  assertThat(result.path("counts").path("overdue").asInt()).isEqualTo(1);assertThat(result.path("counts").path("plannedToday").asInt()).isEqualTo(1);
  assertThat(result.path("items").get(1).path("plannedToday").asBoolean()).isTrue();
 }
 @Test void zoneMidnightIsExclusiveAndSavedUtcInstantsNeverChange()throws Exception{
  long id=create(alice,"\"dueAt\":\"2026-10-13T19:00:00Z\",\"timeZone\":\"Asia/Qyzylorda\"");
  assertThat(plan("Asia/Almaty","today",0,20).path("total").asInt()).isZero();
  assertThat(plan("Asia/Qyzylorda","today",0,20).path("total").asInt()).isZero();
  var utc=plan("UTC","today",0,20);assertThat(utc.path("total").asInt()).isEqualTo(1);
  assertThat(utc.path("items").get(0).path("task").path("timeZone").asText()).isEqualTo("Asia/Qyzylorda");
  assertThat(work.get(alice,id).dueAt()).isEqualTo(Instant.parse("2026-10-13T19:00:00Z"));
 }
 @Test void dstDaysAreCalendarDaysRatherThanTwentyFourHourWindows()throws Exception{
  for(var entry:Map.of("2026-03-08T12:00:00Z",23L,"2026-11-01T12:00:00Z",25L).entrySet()){
   NOW.set(Instant.parse(entry.getKey()));var result=plan("America/New_York","today",0,20);
   assertThat(Duration.between(Instant.parse(result.path("dayStartsAt").asText()),Instant.parse(result.path("dayEndsAt").asText())).toHours()).isEqualTo(entry.getValue());
  }
 }
 @Test void dependencyCompletionRefreshesRiskWithoutViewingMutatingTasksOrHistory()throws Exception{
  long blocker=create(alice,"\"title\":\"Research\"");long task=create(alice,"\"title\":\"Submit\",\"dueAt\":\"2026-10-13T08:00:00Z\",\"hardDeadline\":true");
  db.update("insert into work_dependencies(item_id,depends_on_id)values(?,?)",task,blocker);
  var before=work.get(alice,task);long history=db.queryForObject("select count(*) from work_history",Long.class);
  var result=plan("Asia/Almaty","today",0,20);
  assertThat(result.path("items").get(0).path("risk").path("reasons").toString()).contains("BLOCKED","HARD_DEADLINE","DUE_TODAY");
  assertThat(work.get(alice,task)).isEqualTo(before);assertThat(db.queryForObject("select count(*) from work_history",Long.class)).isEqualTo(history);
  work.update(alice,blocker,json.readTree("{\"status\":\"DONE\"}"));assertThat(plan("Asia/Almaty","today",0,20).path("items").get(0).path("risk").path("reasons").toString()).doesNotContain("BLOCKED");
 }
 @Test void upcomingIsBoundedAndPaginationIsStable()throws Exception{
  List<Long> ids=new ArrayList<>();for(int i=0;i<5;i++)ids.add(create(alice,"\"dueAt\":\"2026-10-14T08:00:00Z\""));
  create(alice,"\"dueAt\":\"2026-10-20T19:00:00Z\"");
  var first=plan("Asia/Almaty","upcoming",0,2);var second=plan("Asia/Almaty","upcoming",1,2);var last=plan("Asia/Almaty","upcoming",2,2);
  assertThat(first.path("total").asInt()).isEqualTo(5);assertThat(first.path("items").findValuesAsText("id")).containsExactly(String.valueOf(ids.get(0)),String.valueOf(ids.get(1)));
  assertThat(second.path("items").findValuesAsText("id")).containsExactly(String.valueOf(ids.get(2)),String.valueOf(ids.get(3)));assertThat(last.path("items")).hasSize(1);
 }
 @Test void authenticationOwnershipAndInputLimitsAreEnforced()throws Exception{
  mvc.perform(get("/api/planning/today")).andExpect(status().isUnauthorized());
  for(var invalid:Map.of("zone","Invalid/Zone","view","all","page","-1","size","51").entrySet())mvc.perform(get("/api/planning/today").with(user(alice)).param(invalid.getKey(),invalid.getValue())).andExpect(status().isBadRequest());
  mvc.perform(get("/api/planning/today").with(user(alice)).param("page","not-a-number")).andExpect(status().isBadRequest());
  create(alice,"\"dueAt\":\"2026-10-13T08:00:00Z\"");mvc.perform(get("/api/planning/today").with(user(bob))).andExpect(jsonPath("$.total").value(0));
 }
}
