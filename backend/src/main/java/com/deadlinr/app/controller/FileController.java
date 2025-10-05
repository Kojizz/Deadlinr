package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.*;
import com.deadlinr.app.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
public class FileController {
 private final FileService files;private final WorkspaceService workspace;
 public FileController(FileService files,WorkspaceService workspace){this.files=files;this.workspace=workspace;}
 @GetMapping("/api/work-items/{id}/files") public List<FileService.FileInfo> list(@AuthenticationPrincipal User u,@PathVariable long id){return files.list(u,id);}
 @PostMapping(value="/api/work-items/{id}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public FileService.FileInfo upload(@AuthenticationPrincipal User u,@PathVariable long id,@RequestParam MultipartFile file)throws IOException{return files.upload(u,id,file);}
 @PostMapping(value="/api/pages/{id}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public FileService.FileInfo uploadPage(@AuthenticationPrincipal User u,@PathVariable long id,@RequestParam MultipartFile file)throws IOException{return files.uploadPage(u,id,file);}
 @GetMapping("/api/files/{id}/content") public ResponseEntity<Resource> content(@AuthenticationPrincipal User u,@PathVariable UUID id){
  var info=files.owned(u,id);Path path=files.path(id);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ResourceNotFoundException("File is unavailable");
  var disposition=FileService.previewable(info.contentType())?ContentDisposition.inline():ContentDisposition.attachment();
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(info.contentType())).contentLength(info.size())
   .header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(info.name(),StandardCharsets.UTF_8).build().toString())
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
 }
 @GetMapping("/api/files/{id}") public ResponseEntity<Resource> download(@AuthenticationPrincipal User u,@PathVariable UUID id){
  var info=files.owned(u,id);Path path=files.path(id);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ResourceNotFoundException("File is unavailable");
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(info.contentType())).contentLength(info.size())
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(info.name(),StandardCharsets.UTF_8).build().toString())
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
 }
 @DeleteMapping("/api/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal User u,@PathVariable UUID id){files.delete(u,id);workspace.cleanupFiles();}
}
