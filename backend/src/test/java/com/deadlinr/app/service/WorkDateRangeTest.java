package com.deadlinr.app.service;
import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class WorkDateRangeTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;
 User alice; String zone="Asia/Almaty";
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("A").email(UUID.randomUUID()+"@t").password("x").role(Role.STUDENT).build());}
 long create(String title,String dueAt)throws Exception{String response=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content("{\"title\":\""+title+"\",\"dueAt\":\""+dueAt+"\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();}
 List<String> titles(Map<String,String> src){var f=new HashMap<>(src);f.put("zone",zone);var result=service.list(alice,f);var items=(java.util.List<?>)result.get("items");java.util.List<String> out=new ArrayList<>();for(var raw:items){var w=(com.deadlinr.app.dto.WorkItem)raw;out.add(w.title());}return out;}
 String due(LocalDate d,int h){return d.atTime(h,0).atZone(ZoneId.of(zone)).toInstant().toString();}
 @Test void singleDayRangeIsInclusiveOfWholeDay()throws Exception{
  var d=LocalDate.of(2026,10,5);
  create("morning",due(d,9));create("late",due(d,23));create("other",due(d.plusDays(1),9));
  var got=titles(Map.of("from","2026-10-05","to","2026-10-05"));
  Assertions.assertTrue(got.containsAll(List.of("morning","late"))&&got.size()==2,got.toString());
 }
 @Test void rangeAcrossMonthBoundary()throws Exception{
  create("sep",due(LocalDate.of(2026,9,30),20));create("oct",due(LocalDate.of(2026,10,1),1));create("nov",due(LocalDate.of(2026,11,1),1));
  var got=titles(Map.of("from","2026-09-30","to","2026-10-31"));
  Assertions.assertTrue(got.containsAll(List.of("sep","oct"))&&!got.contains("nov"),got.toString());
 }
 @Test void endOfDayIsInclusiveInUserZone()throws Exception{
  var d=LocalDate.of(2026,3,31);
  // 23:00 in Almaty (UTC+5) is 18:00 UTC — same local day, must be included by to=that day.
  create("tz-edge",due(d,23));
  var got=titles(Map.of("from","2026-03-31","to","2026-03-31"));
  Assertions.assertTrue(got.contains("tz-edge"),got.toString());
  var before=titles(Map.of("from","2026-03-30","to","2026-03-30"));
  Assertions.assertFalse(before.contains("tz-edge"));
 }
 @Test void dueNoDateAndOverdueFilters()throws Exception{
  mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content("{\"title\":\"floating\"}")).andExpect(status().isCreated());
  create("past",Instant.now().minusSeconds(3600).toString());
  create("future",Instant.now().plusSeconds(7200).toString());
  var none=titles(Map.of("due","nodate"));
  Assertions.assertTrue(none.contains("floating")&&none.size()==1,none.toString());
  var over=titles(Map.of("due","overdue"));
  Assertions.assertTrue(over.contains("past")&&!over.contains("future")&&!over.contains("floating"),over.toString());
 }
}
