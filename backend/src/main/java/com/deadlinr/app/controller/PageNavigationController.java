package com.deadlinr.app.controller;
import com.deadlinr.app.dto.PageAncestor;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.PageNavigationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/pages") public class PageNavigationController {
 private final PageNavigationService service;
 public PageNavigationController(PageNavigationService service){this.service=service;}
 @GetMapping("/{id}/ancestors") public List<PageAncestor> ancestors(@AuthenticationPrincipal User user,@PathVariable long id){return service.ancestors(user,id);}
}
