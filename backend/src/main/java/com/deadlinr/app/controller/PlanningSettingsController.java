package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController @RequestMapping("/api") public class PlanningSettingsController {
 private final PlanningSettingsService service;
 public PlanningSettingsController(PlanningSettingsService service){this.service=service;}
 @GetMapping("/settings") public JsonNode get(@AuthenticationPrincipal User u){return service.get(u);}
 @PutMapping("/settings") public JsonNode save(@AuthenticationPrincipal User u,@RequestBody JsonNode n){return service.save(u,n);}
 @GetMapping("/views") public List<Map<String,Object>> views(@AuthenticationPrincipal User u,@RequestParam(defaultValue="0") int page){return service.views(u,page);}
 @PostMapping("/views") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Map<String,Object> create(@AuthenticationPrincipal User u,@RequestBody JsonNode n){return service.saveView(u,n);}
 @DeleteMapping("/views/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal User u,@PathVariable UUID id){service.deleteView(u,id);}
}
