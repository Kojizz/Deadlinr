package com.deadlinr.app.service;
import com.deadlinr.app.entity.*;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PageNavigationIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long page(Long parent)throws Exception{
  var request=json.createObjectNode().put("title","Notes");if(parent!=null)request.put("parentId",parent);request.set("content",json.readTree("[{\"type\":\"paragraph\",\"content\":[]}]"));
  return json.readTree(mvc.perform(post("/api/pages").with(user(alice)).contentType("application/json").content(request.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
 }
 @Test void deepTrailReturnsOrderedMetadataAndNeverDocumentBodies()throws Exception{
  List<Long> ids=new ArrayList<>();Long parent=null;for(int i=0;i<10;i++){parent=page(parent);ids.add(parent);}
  String body=mvc.perform(get("/api/pages/"+parent+"/ancestors").with(user(alice))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(json.readTree(body).findValuesAsText("id")).isEqualTo(ids.stream().map(String::valueOf).toList());
  assertThat(body).doesNotContain("content","ownerId","searchText");
 }
 @Test void ownershipAndMissingPagesDoNotLeakMetadata()throws Exception{
  long id=page(null);mvc.perform(get("/api/pages/"+id+"/ancestors").with(user(bob))).andExpect(status().isNotFound());
  mvc.perform(get("/api/pages/9223372036854775807/ancestors").with(user(alice))).andExpect(status().isNotFound());
  mvc.perform(get("/api/pages/"+id+"/ancestors")).andExpect(status().isUnauthorized());
 }
 @Test void malformedCycleIsBoundedAndReturnsAConflict()throws Exception{
  long first=page(null),last=page(first);db.update("update pages set parent_id=? where id=?",last,first);
  mvc.perform(get("/api/pages/"+last+"/ancestors").with(user(alice))).andExpect(status().isConflict());
 }
 @Test void overDepthHierarchyFailsExplicitlyRatherThanReturningAnIncompleteTrail()throws Exception{
  Long last=null;for(int i=0;i<102;i++)last=page(last);
  mvc.perform(get("/api/pages/"+last+"/ancestors").with(user(alice))).andExpect(status().isConflict());
 }
 @Test void generatedContractDescribesNullableAncestorsAndAllowedRiskLevels()throws Exception{
  var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  var schemas=spec.path("components").path("schemas");
  assertThat(schemas.path("PageAncestor").path("properties").path("parentId").path("nullable").asBoolean()).isTrue();
  assertThat(schemas.path("PageAncestor").path("properties").path("icon").path("nullable").asBoolean()).isTrue();
  assertThat(schemas.path("DeadlineRisk").path("properties").path("level").path("enum").toString()).contains("HIGH","ATTENTION","NORMAL");
 }
}
