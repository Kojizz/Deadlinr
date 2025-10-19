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
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PageMediaIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired FileService files;
 @org.junit.jupiter.api.io.TempDir static Path folder;
 @org.springframework.test.context.DynamicPropertySource static void storage(org.springframework.test.context.DynamicPropertyRegistry r){r.add("app.storage.path",()->folder.toString());}
 User alice,bob;long pageId;
 @BeforeEach void setup()throws Exception{
  alice=users.save(User.builder().fullName("Alice").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());bob=users.save(User.builder().fullName("Bob").email(UUID.randomUUID()+"@test").password("unused").role(Role.STUDENT).build());
  pageId=json.readTree(mvc.perform(post("/api/pages").with(user(alice)).contentType("application/json").content("{\"title\":\"Media\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
 }
 static byte[] png()throws Exception{var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();}
 @Test void imageBytesAndReferenceSurviveReloadWithOwnershipAndRangeChecks()throws Exception{
  byte[] bytes=png();var upload=new MockMultipartFile("file","../../diagram.png","image/png",bytes);
  var saved=json.readTree(mvc.perform(multipart("/api/pages/"+pageId+"/files").file(upload).with(user(alice))).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("diagram.png")).andReturn().getResponse().getContentAsString());UUID id=UUID.fromString(saved.path("id").asText());
  assertThat(Files.readAllBytes(files.path(id))).isEqualTo(bytes);assertThat(db.queryForObject("select page_id from attachments where id=?",Long.class,id)).isEqualTo(pageId);
  String reference="/api/files/"+id+"/content";var content=json.createArrayNode().addObject().put("type","image");content.putObject("props").put("url",reference).put("name","diagram.png");
  var body=json.createObjectNode();body.putArray("content").add(content);
  mvc.perform(put("/api/pages/"+pageId+"/content").with(user(alice)).contentType("application/json").content(body.toString())).andExpect(status().isOk());
  mvc.perform(get("/api/pages/"+pageId).with(user(alice))).andExpect(jsonPath("$.content[0].props.url").value(reference));
  mvc.perform(get(reference).with(user(alice))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes)).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline;"))).andExpect(header().string("X-Content-Type-Options","nosniff"));
  mvc.perform(get(reference).with(user(alice)).header("Range","bytes=0-7")).andExpect(status().isPartialContent()).andExpect(content().bytes(Arrays.copyOf(bytes,8)));
  mvc.perform(get(reference)).andExpect(status().isUnauthorized());mvc.perform(get(reference).with(user(bob))).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/pages/"+pageId+"/files").file(upload).with(user(bob))).andExpect(status().isForbidden());
 }
 @Test void invalidUnsafeEmptyAndOversizedUploadsDoNotCreateAttachments()throws Exception{
  var invalid=List.of(new MockMultipartFile("file","script.svg","image/svg+xml","<svg onload='alert(1)'/>".getBytes()),new MockMultipartFile("file","fake.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0}),new MockMultipartFile("file","empty.png","image/png",new byte[0]),new MockMultipartFile("file","too-big.png","image/png",new byte[20*1024*1024+1]));
  for(var file:invalid)mvc.perform(multipart("/api/pages/"+pageId+"/files").file(file).with(user(alice))).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("select count(*) from attachments where owner_id=?",Long.class,alice.getId())).isZero();
 }
 @Test void regularFilesDownloadAndArchivedPagesRejectNewUploads()throws Exception{
  var saved=json.readTree(mvc.perform(multipart("/api/pages/"+pageId+"/files").file(new MockMultipartFile("file","notes.txt","text/plain","Study notes".getBytes())).with(user(alice))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  mvc.perform(get("/api/files/"+saved.path("id").asText()+"/content").with(user(alice))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;"))).andExpect(content().string("Study notes"));
  mvc.perform(delete("/api/pages/"+pageId).with(user(alice))).andExpect(status().isNoContent());
  mvc.perform(multipart("/api/pages/"+pageId+"/files").file(new MockMultipartFile("file","diagram.png","image/png",png())).with(user(alice))).andExpect(status().isBadRequest());
 }
 @Test void mediaTypesAreVerifiedAndHugeImageDimensionsAreRejected()throws Exception{
  byte[] wave=new byte[76];var buffer=java.nio.ByteBuffer.wrap(wave).order(java.nio.ByteOrder.LITTLE_ENDIAN);buffer.put("RIFF".getBytes()).putInt(68).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(32);
  var saved=json.readTree(mvc.perform(multipart("/api/pages/"+pageId+"/files").file(new MockMultipartFile("file","voice.wav","application/octet-stream",wave)).with(user(alice))).andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("audio/wav")).andReturn().getResponse().getContentAsString());
  mvc.perform(get("/api/files/"+saved.path("id").asText()+"/content").with(user(alice))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline;"))).andExpect(content().bytes(wave));
  for(String name:List.of("fake.mp4","fake.webm","fake.mp3","fake.wav"))mvc.perform(multipart("/api/pages/"+pageId+"/files").file(new MockMultipartFile("file",name,"video/mp4","<html>not media</html>".getBytes())).with(user(alice))).andExpect(status().isBadRequest());
  byte[] large=png();java.nio.ByteBuffer.wrap(large).putInt(16,30000).putInt(20,30000);var crc=new java.util.zip.CRC32();crc.update(large,12,17);java.nio.ByteBuffer.wrap(large).putInt(29,(int)crc.getValue());
  mvc.perform(multipart("/api/pages/"+pageId+"/files").file(new MockMultipartFile("file","huge.png","image/png",large)).with(user(alice))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Images must be at most 20 megapixels."));
 }
 @Test void interruptedStorageWriteRollsBackAndRemovesPartialFile()throws Exception{
  List<Path> before;try(var entries=Files.list(folder)){before=entries.sorted().toList();}
  var broken=new MockMultipartFile("file","notes.txt","text/plain","notes".getBytes()){
   int opens;
   @Override public java.io.InputStream getInputStream(){if(opens++==0)return new java.io.ByteArrayInputStream("notes".getBytes());return new java.io.InputStream(){@Override public int read()throws java.io.IOException{throw new java.io.IOException("Interrupted upload");}};}
  };
  assertThatThrownBy(()->files.uploadPage(alice,pageId,broken)).isInstanceOf(java.io.IOException.class);
  assertThat(db.queryForObject("select count(*) from attachments where owner_id=?",Long.class,alice.getId())).isZero();try(var entries=Files.list(folder)){assertThat(entries.sorted().toList()).isEqualTo(before);}
 }
}
