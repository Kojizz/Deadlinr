package com.deadlinr.app.service;
import com.deadlinr.app.dto.PageRequest;
import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class WorkspaceIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired PageService pages; @Autowired JdbcTemplate db;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(String title,Long parent){return pages.createPage(alice,new PageRequest(title,null,parent,null,null,null)).id();}
 @Test void subtreeBatchSummaryAndRestoreAndPermanent()throws Exception{
  long parent=create("Parent",null),a=create("Child A",parent),b=create("Child B",parent);
  db.update("insert into reminders(owner_id,page_id,scheduled_at)values(?,?,now()+interval '1 day')",alice.getId(),a);
  mvc.perform(get("/api/workspace/pages/"+parent+"/impact").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.affected").value(3));
  mvc.perform(post("/api/workspace/pages/"+parent+"/archive").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.affected").value(3));
  assertThat(db.queryForObject("select count(distinct archive_batch_id) from pages where owner_id=?",Integer.class,alice.getId())).isEqualTo(1);
  mvc.perform(get("/api/workspace/summary").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.trashCount").value(3));
  mvc.perform(get("/api/workspace/pages?trash=true&size=2").with(user(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
  assertThat(db.queryForObject("select cancelled from reminders where page_id=?",Boolean.class,a)).isTrue();
  mvc.perform(post("/api/workspace/pages/"+parent+"/restore").with(user(alice))).andExpect(status().isOk());
  assertThat(pages.getTree(alice).getFirst().children()).hasSize(2);
  assertThat(db.queryForObject("select cancelled from reminders where page_id=?",Boolean.class,a)).isFalse();
  mvc.perform(post("/api/workspace/pages/"+parent+"/archive").with(user(alice))).andExpect(status().isOk());
  mvc.perform(delete("/api/workspace/pages/"+parent).with(user(alice))).andExpect(status().isOk());
  assertThat(db.queryForObject("select count(*) from pages where owner_id=?",Integer.class,alice.getId())).isZero();
  assertThat(db.queryForObject("select count(*) from reminders where owner_id=?",Integer.class,alice.getId())).isZero();
 }
 @Test void allWorkspaceEndpointsIsolateOwners()throws Exception{
  long id=create("Private",null);
  for(String path:List.of("/api/workspace/pages/"+id+"/impact","/api/workspace/children?parentId="+id))
   mvc.perform(get(path).with(user(bob))).andExpect(status().isForbidden());
  for(String action:List.of("archive","restore"))mvc.perform(post("/api/workspace/pages/"+id+"/"+action).with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(delete("/api/workspace/pages/"+id).with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(get("/api/workspace/pages").with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/workspace/summary").with(user(bob))).andExpect(jsonPath("$.totalPages").value(0));
  mvc.perform(get("/api/workspace/children").with(user(bob))).andExpect(jsonPath("$.length()").value(0));
  pages.archive(alice,id);mvc.perform(delete("/api/workspace/trash").with(user(bob))).andExpect(status().isOk());
  assertThat(pages.getTrash(alice)).hasSize(1);
  mvc.perform(delete("/api/workspace/trash").with(user(alice))).andExpect(status().isOk());
 }
 @Test void restoringBatchDoesNotRestorePreviouslyTrashedChild()throws Exception{
  long p=create("P",null),c=create("C",p);pages.archive(alice,c);pages.archive(alice,p);pages.restore(alice,p);
  assertThat(pages.getTrash(alice)).hasSize(1);assertThat(pages.getTree(alice).getFirst().children()).isEmpty();
  mvc.perform(get("/api/workspace/children?size=1").with(user(alice))).andExpect(jsonPath("$[0].childCount").value(0)).andExpect(jsonPath("$[0].content").doesNotExist());
 }
}
