package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.WorkspaceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController @RequestMapping("/api/workspace")
public class WorkspaceController {
 private final WorkspaceService service;
 public WorkspaceController(WorkspaceService service){this.service=service;}
 @GetMapping("/summary") public Map<String,Object> summary(@AuthenticationPrincipal User u,@RequestParam(defaultValue="Asia/Almaty") String zone){return service.summary(u,zone);}
 @GetMapping("/pages") public List<Map<String,Object>> pages(@AuthenticationPrincipal User u,@RequestParam(defaultValue="false") boolean trash,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size){return service.listPages(u,trash,page,size);}
 @GetMapping("/children") public List<Map<String,Object>> children(@AuthenticationPrincipal User u,@RequestParam(required=false) Long parentId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size){return service.children(u,parentId,page,size);}
 @GetMapping("/pages/{id}/impact") public Map<String,Object> impact(@AuthenticationPrincipal User u,@PathVariable long id){service.ownedPage(u,id);var ids=service.descendants(u.getId(),id);var counts=service.linkedItemCounts(u,ids);var result=new HashMap<String,Object>();result.put("affected",ids.size());result.putAll(counts);return result;}
 @PostMapping("/pages/{id}/archive") public Map<String,Object> archive(@AuthenticationPrincipal User u,@PathVariable long id){return service.archive(u,id);}
 @PostMapping("/pages/{id}/restore") public void restore(@AuthenticationPrincipal User u,@PathVariable long id){service.restore(u,id);}
 @DeleteMapping("/pages/{id}") public void permanent(@AuthenticationPrincipal User u,@PathVariable long id){service.permanent(u,id);service.cleanupFiles();}
 @DeleteMapping("/trash") public void empty(@AuthenticationPrincipal User u){service.empty(u);service.cleanupFiles();}
}
