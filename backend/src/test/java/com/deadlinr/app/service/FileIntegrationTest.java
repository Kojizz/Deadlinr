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
class FileIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired WorkService service;@Autowired NotificationService notifications;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 User alice,bob;
 @BeforeEach void setup(){alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());}
 long create(String body)throws Exception{String response=mvc.perform(post("/api/work-items").with(user(alice)).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("id").asLong();}

 @Autowired FileService files;@Autowired WorkspaceService workspace;
 @org.junit.jupiter.api.io.TempDir static java.nio.file.Path folder;
 @org.springframework.test.context.DynamicPropertySource static void storage(org.springframework.test.context.DynamicPropertyRegistry r){r.add("app.storage.path",()->folder.toString());}
 @Test void attachmentEndpointsProtectOwnershipAndDeleteDisk()throws Exception{
  long id=create("{\"title\":\"File task\"}");
  var file=new org.springframework.mock.web.MockMultipartFile("file","../notes.txt","text/plain","hello attachment".getBytes());
  String body=mvc.perform(multipart("/api/work-items/"+id+"/files").file(file).with(user(alice))).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("notes.txt")).andReturn().getResponse().getContentAsString();
  UUID fileId=UUID.fromString(json.readTree(body).get("id").asText());assertThat(java.nio.file.Files.exists(files.path(fileId))).isTrue();
  mvc.perform(get("/api/work-items/"+id+"/files").with(user(alice))).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/files/"+fileId).with(user(alice))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;"))).andExpect(content().string("hello attachment"));
  mvc.perform(get("/api/work-items/"+id+"/files").with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/work-items/"+id+"/files").file(file).with(user(bob))).andExpect(status().isForbidden());
  mvc.perform(get("/api/files/"+fileId).with(user(bob))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/files/"+fileId).with(user(bob))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/files/"+fileId).with(user(alice))).andExpect(status().isNoContent());assertThat(java.nio.file.Files.exists(files.path(fileId))).isFalse();
  for(String name:List.of("bad.html","fake.pdf"))mvc.perform(multipart("/api/work-items/"+id+"/files").file(new org.springframework.mock.web.MockMultipartFile("file",name,"text/html","<script>bad</script>".getBytes())).with(user(alice))).andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/work-items/"+id+"/files").file(new org.springframework.mock.web.MockMultipartFile("file","big.txt","text/plain",new byte[20*1024*1024+1])).with(user(alice))).andExpect(status().isBadRequest());
 }
 @Test void bulkIsAtomicAndEveryNewEndpointIsOwned()throws Exception{
  long a=create("{\"title\":\"A\"}"),b=create("{\"title\":\"B\"}");
  long foreign=service.create(bob,json.readTree("{\"title\":\"Private\"}")).id();
  mvc.perform(post("/api/work-items/bulk").with(user(alice)).contentType("application/json").content("{\"ids\":["+a+","+foreign+"],\"patch\":{\"status\":\"DONE\"}}")).andExpect(status().isForbidden());assertThat(service.get(alice,a).status()).isEqualTo("TODO");
  mvc.perform(post("/api/work-items/bulk").with(user(alice)).contentType("application/json").content("{\"ids\":["+a+","+b+"],\"patch\":{\"status\":\"DONE\"}}")).andExpect(status().isOk());
  mvc.perform(get("/api/work-items?group=status").with(user(alice))).andExpect(jsonPath("$.counts.done").value(2)).andExpect(jsonPath("$.groupCounts[0].count").value(2));
  mvc.perform(get("/api/work-items/"+a+"/reminders").with(user(bob))).andExpect(status().isForbidden());mvc.perform(get("/api/work-items/"+a+"/reminders").with(user(alice))).andExpect(status().isOk());
  mvc.perform(delete("/api/work-items/"+a+"/permanent").with(user(bob))).andExpect(status().isForbidden());mvc.perform(delete("/api/work-items/"+a+"/permanent").with(user(alice))).andExpect(status().isBadRequest());
  var file=files.upload(alice,a,new org.springframework.mock.web.MockMultipartFile("file","test.txt","text/plain","test".getBytes()));
  service.archive(alice,a);mvc.perform(delete("/api/work-items/"+a+"/permanent").with(user(alice))).andExpect(status().isOk());assertThat(java.nio.file.Files.exists(files.path(file.id()))).isFalse();
 }
}
