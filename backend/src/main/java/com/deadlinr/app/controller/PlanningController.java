package com.deadlinr.app.controller;
import com.deadlinr.app.dto.TodayPlan;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.TodayPlanningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestController @RequestMapping("/api/planning") public class PlanningController {
 private final TodayPlanningService service;
 public PlanningController(TodayPlanningService service){this.service=service;}
 @GetMapping("/today") public TodayPlan today(@AuthenticationPrincipal User user,
  @RequestParam(defaultValue="Asia/Almaty") String zone,@RequestParam(defaultValue="today") String view,
  @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.today(user,zone,view,page,size);}
 @ExceptionHandler(MethodArgumentTypeMismatchException.class)
 public ResponseEntity<Map<String,Object>> invalidQuery(MethodArgumentTypeMismatchException e){return ResponseEntity.badRequest().body(Map.of("status",400,"message","Invalid "+e.getName()));}
}
