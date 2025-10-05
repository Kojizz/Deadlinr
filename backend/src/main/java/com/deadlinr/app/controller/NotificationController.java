package com.deadlinr.app.controller;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController @RequestMapping("/api/notifications")
public class NotificationController{
 private final NotificationService service;public NotificationController(NotificationService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal User u,@RequestParam(defaultValue="0") int page){return service.list(u,page);}
 @PostMapping("/{id}/read") public void read(@AuthenticationPrincipal User u,@PathVariable long id){service.read(u,id);}
}
