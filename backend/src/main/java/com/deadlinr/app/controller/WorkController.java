package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.dto.WorkItem;
import com.deadlinr.app.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController @RequestMapping("/api/work-items")
public class WorkController {
 private final WorkService service;public WorkController(WorkService service){this.service=service;}
 public record BulkRequest(List<Long> ids,JsonNode patch,String operation){}
 @PostMapping("/bulk") public List<WorkItem> bulk(@AuthenticationPrincipal User u,@RequestBody BulkRequest b){return service.bulk(u,b.ids(),b.patch(),b.operation()==null?"update":b.operation());}
 @GetMapping("/{id}/reminders") public List<Map<String,Object>> reminders(@AuthenticationPrincipal User u,@PathVariable long id){return service.reminders(u,id);}
 @DeleteMapping("/{id}/permanent") public void permanent(@AuthenticationPrincipal User u,@PathVariable long id){service.permanent(u,id);service.cleanupFiles();}
 @GetMapping public Map<String,Object> list(@AuthenticationPrincipal User u,@RequestParam Map<String,String> filters){return service.list(u,filters);}
 @GetMapping("/deadline-days") public List<String> days(@AuthenticationPrincipal User u,@RequestParam String from,@RequestParam String to,@RequestParam(defaultValue="Asia/Almaty") String zone){return service.deadlineDays(u,from,to,zone);}
 @GetMapping("/{id}") public WorkItem get(@AuthenticationPrincipal User u,@PathVariable long id){return service.get(u,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public WorkItem create(@AuthenticationPrincipal User u,@RequestBody JsonNode input){return service.create(u,input);}
 @PatchMapping("/{id}") public WorkItem update(@AuthenticationPrincipal User u,@PathVariable long id,@RequestBody JsonNode patch){return service.update(u,id,patch);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal User u,@PathVariable long id){service.archive(u,id);}
 @PostMapping("/{id}/restore") public void restore(@AuthenticationPrincipal User u,@PathVariable long id){service.restore(u,id);}
}
