package com.deadlinr.app.service;
import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PageStructureIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;@Autowired WorkspaceService workspace;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("A").email(UUID.randomUUID()+"@t").password("x").role(Role.STUDENT).build());}
 long createPage() throws Exception{String r=mvc.perform(post("/api/pages").with(user(alice)).contentType("application/json").content("{\"title\":\"Project\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(r).get("id").asLong();}
 long createItem(String title,Long pageId,String kind,String dueAt,List<Integer> reminders) throws Exception{
  StringBuilder body=new StringBuilder("{\"title\":\""+title+"\",\"kind\":\""+kind+"\",\"timeZone\":\"UTC\"");
  if(pageId!=null)body.append(",\"pageId\":").append(pageId);
  if(dueAt!=null)body.append(",\"dueAt\":\"").append(dueAt).append('"');
  if(reminders!=null){body.append(",\"reminderMinutes\":[").append(String.join(",",reminders.stream().map(String::valueOf).toList())).append(']');}
  body.append('}');
  String r=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content(body.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  return json.readTree(r).get("id").asLong();
 }
 @Test void pageIdFiltersListAndPageTitleJoins() throws Exception{
  long pid=createPage();
  long a=createItem("linked",pid,"TASK",null,null);
  long b=createItem("floating",null,"TASK",null,null);
  var page=Map.copyOf(Map.of("zone","UTC","kind","TASK","pageId",String.valueOf(pid)));
  var filtered=(List<?>)service.list(alice,new HashMap<String,String>(Map.of("zone","UTC","kind","TASK","pageId",String.valueOf(pid)))).get("items");
  assertThat(filtered).hasSize(1);
  var item=service.get(alice,a);
  assertThat(item.pageTitle()).isEqualTo("Project");
  var floating=service.get(alice,b);
  assertThat(floating.pageTitle()).isNull();
 }
 @Test void treeNodesHaveOpenTaskCount() throws Exception{
  long pid=createPage();
  createItem("open1",pid,"TASK",null,null);
  long done=createItem("done1",pid,"TASK",null,null);
  service.update(alice,done,json.readTree("{\"status\":\"DONE\"}"));
  var tree=mvc.perform(get("/api/pages/tree").with(user(alice))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var nodes=json.readTree(tree);
  var project=java.util.stream.StreamSupport.stream(nodes.spliterator(),false).filter(n->n.get("title").asText().equals("Project")).findFirst().orElseThrow();
  assertThat(project.get("openTasks").asInt()).isEqualTo(1);
 }
 @Test void archivingPageCascadesItemsRemindersAndRestoresThem() throws Exception{
  long pid=createPage();
  long itemId=createItem("task",pid,"TASK",Instant.now().plusSeconds(86400).toString(),List.of(10));
  long eventId=createItem("event",pid,"EVENT",Instant.now().plusSeconds(86400).toString(),null);
  long doneItem=createItem("done",pid,"TASK",Instant.now().plusSeconds(86400).toString(),null);
  service.update(alice,doneItem,json.readTree("{\"status\":\"DONE\"}"));
  db.update("insert into reminders(owner_id,item_id,scheduled_at)values(?,?,?)",alice.getId(),doneItem,Timestamp.from(Instant.now().plusSeconds(3600)));
  workspace.archive(alice,pid);
  var cancelled=db.queryForObject("select count(*) from reminders where item_id=? and cancelled=true",Integer.class,itemId);
  assertThat(cancelled).isGreaterThan(0);
  var archivedItems=db.queryForObject("select count(*) from work_items where id in(?,?,?) and archived_at is not null",Integer.class,itemId,eventId,doneItem);
  assertThat(archivedItems).isEqualTo(3);
  workspace.restore(alice,pid);
  var restoredReminder=db.queryForObject("select count(*) from reminders where item_id=? and cancelled=false and sent_at is null",Integer.class,itemId);
  assertThat(restoredReminder).isGreaterThan(0);
  var doneCancelled=db.queryForObject("select count(*) from reminders where item_id=? and cancelled=true",Integer.class,doneItem);
  assertThat(doneCancelled).isGreaterThan(0);
  var backItems=db.queryForObject("select count(*) from work_items where id in(?,?) and archived_at is null",Integer.class,itemId,eventId);
  assertThat(backItems).isEqualTo(2);
 }
 @Test void archivedItemRemindersRespectDoneAndPast() throws Exception{
  long pid=createPage();
  long a=createItem("a",pid,"TASK",Instant.now().plusSeconds(86400).toString(),List.of(60));
  service.archive(alice,a);
  service.restore(alice,a);
  var anyPastUncancelled=db.queryForObject("select count(*) from reminders r where r.item_id=? and r.cancelled=false and r.scheduled_at<=now()",Integer.class,a);
  assertThat(anyPastUncancelled).isZero();
 }
 @Test void workspaceSummaryMatchesTasksListCounts() throws Exception{
  long pid=createPage();
  long todoOverdue=createItem("ov",pid,"TASK",Instant.now().minusSeconds(3600).toString(),null);
  long today=createItem("today",pid,"TASK",Instant.now().plusSeconds(3600).toString(),null);
  long done=createItem("done",pid,"TASK",Instant.now().minusSeconds(3600).toString(),null);
  service.update(alice,done,json.readTree("{\"status\":\"DONE\"}"));
  long evtToday=createItem("evt",pid,"EVENT",Instant.now().plusSeconds(3600).toString(),null);
  var summary=workspace.summary(alice,"UTC");
  var taskCounts=(Map<String,Object>)service.list(alice,new HashMap<String,String>(Map.of("zone","UTC","kind","TASK","view","all"))).get("counts");
  var totalTasks=((Number)taskCounts.get("all")).longValue();
  var doneTasks=((Number)taskCounts.get("done")).longValue();
  assertThat(((Number)summary.get("openTasks")).longValue()).isEqualTo(totalTasks-doneTasks);
  assertThat(((Number)summary.get("overdue")).longValue()).isEqualTo(((Number)taskCounts.get("overdue")).longValue());
  assertThat(((Number)summary.get("dueToday")).longValue()).isEqualTo(((Number)taskCounts.get("dueToday")).longValue());
  assertThat(((Number)summary.get("dueThisWeek")).longValue()).isEqualTo(((Number)taskCounts.get("dueThisWeek")).longValue());
 }
 @Test void archiveMessagesInTrashShowLinkedItemCounts() throws Exception{
  long pid=createPage();
  createItem("t1",pid,"TASK",null,null);
  createItem("e1",pid,"EVENT",null,null);
  var impact=workspace.descendants(alice.getId(),pid).size();
  var counts=workspace.linkedItemCounts(alice,workspace.descendants(alice.getId(),pid));
  assertThat(counts.get("tasks")).isEqualTo(1L);
  assertThat(counts.get("events")).isEqualTo(1L);
  workspace.archive(alice,pid);
  var trash=workspace.listPages(alice,true,0,20);
  var found=trash.stream().filter(r->((Number)r.get("id")).longValue()==pid).findFirst().orElseThrow();
  assertThat(((Number)found.get("taskCount")).longValue()).isEqualTo(1L);
  assertThat(((Number)found.get("eventCount")).longValue()).isEqualTo(1L);
 }
}





