package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.dto.WorkItem;
import com.deadlinr.app.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController @RequestMapping("/api") public class SmartPlanningController {
 private final SmartPlanningService service;private final WorkHistoryService history;
 public SmartPlanningController(SmartPlanningService service,WorkHistoryService history){this.service=service;this.history=history;}
 @GetMapping("/labels") public List<Map<String,Object>> labels(@AuthenticationPrincipal User u){return service.labels(u);}
 @PutMapping("/labels/{name}") public void label(@AuthenticationPrincipal User u,@PathVariable String name,@RequestBody JsonNode n){service.label(u,name,n);}
 @GetMapping("/templates") public List<Map<String,Object>> templates(@AuthenticationPrincipal User u,@RequestParam(defaultValue="0") int page){return service.templates(u,page);}
 @PostMapping("/templates") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Map<String,Object> template(@AuthenticationPrincipal User u,@RequestBody JsonNode n){return service.template(u,n);}
 @DeleteMapping("/templates/{id}") public void remove(@AuthenticationPrincipal User u,@PathVariable UUID id){service.deleteTemplate(u,id);}
 @PostMapping("/templates/{id}/apply") public List<WorkItem> apply(@AuthenticationPrincipal User u,@PathVariable UUID id,@RequestBody JsonNode n){return service.apply(u,id,n.path("itemId").asLong());}
 @GetMapping("/work-items/{id}/dependencies") public List<Map<String,Object>> dependencies(@AuthenticationPrincipal User u,@PathVariable long id){return service.dependencies(u,id);}
 @PostMapping("/work-items/{id}/dependencies") public void depend(@AuthenticationPrincipal User u,@PathVariable long id,@RequestBody JsonNode n){service.depend(u,id,n.path("dependsOnId").asLong());}
 @DeleteMapping("/work-items/{id}/dependencies/{other}") public void unlink(@AuthenticationPrincipal User u,@PathVariable long id,@PathVariable long other){service.unlink(u,id,other);}
 @GetMapping("/matrix") public Map<String,Object> matrix(@AuthenticationPrincipal User u,@RequestParam(defaultValue="2") int urgentDays){return service.matrix(u,urgentDays);}
 @GetMapping("/smart-counts") public Map<String,Object> counts(@AuthenticationPrincipal User u,@RequestParam(defaultValue="Asia/Almaty") String zone){return service.counts(u,zone);}
 @GetMapping("/stats") public Map<String,Object> stats(@AuthenticationPrincipal User u,@RequestParam(defaultValue="Asia/Almaty") String zone){return service.stats(u,zone);}
 @GetMapping("/history") public Map<String,Object> history(@AuthenticationPrincipal User u){return history.status(u);}
 @PostMapping("/history/undo") public Map<String,Object> undo(@AuthenticationPrincipal User u){return history.apply(u,false);}
 @PostMapping("/history/redo") public Map<String,Object> redo(@AuthenticationPrincipal User u){return history.apply(u,true);}
}
