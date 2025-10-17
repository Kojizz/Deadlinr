package com.deadlinr.app.controller;

import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.DataExportService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/data")
public class DataExportController {
 private final DataExportService service;
 public DataExportController(DataExportService service){this.service=service;}
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<java.util.Map<String,String>> malformedOptions(){return ResponseEntity.badRequest().body(java.util.Map.of("message","Invalid export options. Choose a format and valid true/false scope values."));}
 @GetMapping("/summary")
 public ResponseEntity<DataExportService.Summary> summary(@AuthenticationPrincipal User user,@RequestParam(defaultValue="json") String format,@RequestParam(defaultValue="true") boolean includeDocuments,@RequestParam(defaultValue="true") boolean includeAttachments,@RequestParam(defaultValue="false") boolean includeArchived){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(user,new DataExportService.Options(format,includeDocuments,includeAttachments,includeArchived)));
 }
 @PostMapping("/export")
 public ResponseEntity<byte[]> export(@AuthenticationPrincipal User user,@RequestBody DataExportService.Options options){
  byte[] bytes=service.export(user,options);
  String extension=options.format().equals("markdown")?"md":options.format();
  String type=switch(options.format()){case "csv"->"text/csv;charset=UTF-8";case "markdown"->"text/markdown;charset=UTF-8";default->"application/json;charset=UTF-8";};
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(bytes.length)
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("deadlinr-"+LocalDate.now(ZoneOffset.UTC)+"."+extension).build().toString())
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(bytes);
 }
}
