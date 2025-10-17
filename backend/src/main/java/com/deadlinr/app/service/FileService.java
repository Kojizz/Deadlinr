package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
@Service
public class FileService {
 public record FileInfo(UUID id,String name,long size,String contentType,Instant uploadedAt){}
 private final JdbcTemplate db;private final WorkService work;private final WorkspaceService workspace;private final Path storage;
 private static final long MAX=20L*1024*1024;
 private static final Map<String,String> TYPES=Map.ofEntries(Map.entry("pdf","application/pdf"),Map.entry("png","image/png"),Map.entry("jpg","image/jpeg"),Map.entry("jpeg","image/jpeg"),Map.entry("gif","image/gif"),Map.entry("mp4","video/mp4"),Map.entry("webm","video/webm"),Map.entry("mp3","audio/mpeg"),Map.entry("wav","audio/wav"),Map.entry("txt","text/plain"),Map.entry("csv","text/csv"),Map.entry("zip","application/zip"),Map.entry("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),Map.entry("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),Map.entry("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation"));
 public static boolean previewable(String type){return Set.of("image/png","image/jpeg","image/gif","video/mp4","video/webm","audio/mpeg","audio/wav").contains(type);}
 public FileService(JdbcTemplate db,WorkService work,WorkspaceService workspace,Environment env){this.db=db;this.work=work;this.workspace=workspace;storage=Path.of(env.getProperty("app.storage.path","./data/attachments")).toAbsolutePath().normalize();}
 private FileInfo map(java.sql.ResultSet r,int row)throws java.sql.SQLException{return new FileInfo(r.getObject("id",UUID.class),r.getString("name"),r.getLong("size"),r.getString("content_type"),r.getTimestamp("uploaded_at").toInstant());}
 public List<FileInfo> list(User u,long itemId){work.get(u,itemId);return db.query("select * from attachments where owner_id=? and item_id=? order by uploaded_at,id",this::map,u.getId(),itemId);}
 public FileInfo owned(User u,UUID id){var rows=db.query("select * from attachments where id=? and owner_id=?",this::map,id,u.getId());if(rows.isEmpty())throw new ResourceNotFoundException("File not found");return rows.getFirst();}
 public Path path(UUID id){var p=storage.resolve(id.toString()).normalize();if(!p.getParent().equals(storage))throw new IllegalArgumentException("Invalid file");return p;}
 @Transactional(rollbackFor=IOException.class) public FileInfo upload(User u,long itemId,MultipartFile file)throws IOException{
  work.lock(u);if(work.get(u,itemId).archivedAt()!=null)throw new IllegalArgumentException("Restore this item before attaching files");
  return store(u,itemId,null,file);
 }
 @Transactional(rollbackFor=IOException.class) public FileInfo uploadPage(User u,long pageId,MultipartFile file)throws IOException{
  work.lock(u);workspace.ownedPage(u,pageId);
  if(Boolean.TRUE.equals(db.queryForObject("select archived_at is not null from pages where id=?",Boolean.class,pageId)))throw new IllegalArgumentException("Restore this page before attaching files");
  return store(u,null,pageId,file);
 }
 private FileInfo store(User u,Long itemId,Long pageId,MultipartFile file)throws IOException{
  if(file.isEmpty()||file.getSize()>MAX)throw new IllegalArgumentException("Files must be between 1 byte and 20 MB");
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("file").replace('\\','/');
  name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}\\\"<>:]","_").strip();
  if(name.length()>200)name=name.substring(name.length()-200);
  String extension=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
  String type=TYPES.get(extension);if(type==null)throw new IllegalArgumentException("Allowed files: PNG, JPEG, GIF, MP4, WebM, MP3, WAV, PDF, TXT, CSV, ZIP and Office documents. SVG and HTML are not supported.");
  byte[] head;try(var in=file.getInputStream()){head=in.readNBytes(4096);}
  boolean valid=switch(extension){case "pdf"->ascii(head,0,5).equals("%PDF-");case "png"->head.length>=8&&head[0]==(byte)137&&head[1]==80&&head[2]==78&&head[3]==71;case "jpg","jpeg"->head.length>=3&&head[0]==(byte)255&&head[1]==(byte)216&&head[2]==(byte)255;case "gif"->ascii(head,0,4).equals("GIF8");case "mp4"->head.length>=16&&ascii(head,4,4).equals("ftyp")&&java.nio.ByteBuffer.wrap(head).getInt()>=16&&java.nio.ByteBuffer.wrap(head).getInt()<=file.getSize()&&Set.of("isom","iso2","iso4","iso5","iso6","avc1","mp41","mp42","M4V ","dash").contains(ascii(head,8,4));case "webm"->webm(head);case "mp3"->ascii(head,0,3).equals("ID3")||(head.length>2&&(head[0]&255)==255&&(head[1]&224)==224);case "wav"->head.length>=44&&ascii(head,0,4).equals("RIFF")&&ascii(head,8,4).equals("WAVE");case "zip","docx","xlsx","pptx"->head.length>=4&&head[0]==80&&head[1]==75;default->true;};
  if(!valid)throw new IllegalArgumentException("File content does not match its extension");
  if(type.startsWith("image/"))validateImage(file);
  if(extension.equals("txt")||extension.equals("csv"))for(byte b:head)if(b==0)throw new IllegalArgumentException("Use UTF-8 text files");
  Files.createDirectories(storage);UUID id=UUID.randomUUID();Path target=path(id);
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(target);}catch(IOException ignored){}}});
  try(var in=file.getInputStream()){Files.copy(in,target);}
  db.update("insert into attachments(id,owner_id,item_id,page_id,name,size,content_type)values(?,?,?,?,?,?,?)",id,u.getId(),itemId,pageId,name,file.getSize(),type);
  return owned(u,id);
 }
 private static String ascii(byte[] bytes,int offset,int length){return bytes.length<offset+length?"":new String(bytes,offset,length,java.nio.charset.StandardCharsets.US_ASCII);}
 private static boolean webm(byte[] bytes){
  if(bytes.length<12||java.nio.ByteBuffer.wrap(bytes).getInt()!=0x1a45dfa3)return false;
  for(int i=4;i+7<=bytes.length;i++)if(bytes[i]==0x42&&bytes[i+1]==(byte)0x82&&bytes[i+2]==(byte)0x84&&ascii(bytes,i+3,4).equals("webm"))return true;
  return false;
 }
 private static void validateImage(MultipartFile file){
  try(var input=file.getInputStream();var image=new javax.imageio.stream.MemoryCacheImageInputStream(input)){
   var readers=javax.imageio.ImageIO.getImageReaders(image);if(!readers.hasNext())throw new IllegalArgumentException("Invalid image. Use PNG, JPEG or GIF.");
   var reader=readers.next();try{reader.setInput(image);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||(long)width*height>20_000_000)throw new IllegalArgumentException("Images must be at most 20 megapixels.");if(reader.read(0)==null)throw new IOException();}finally{reader.dispose();}
  }catch(IOException e){throw new IllegalArgumentException("Image could not be decoded. Choose a valid PNG, JPEG or GIF.");}
 }
 @Transactional public void delete(User u,UUID id){owned(u,id);db.update("insert into file_deletions(id)values(?) on conflict do nothing",id);db.update("delete from attachments where id=? and owner_id=?",id,u.getId());}
}
