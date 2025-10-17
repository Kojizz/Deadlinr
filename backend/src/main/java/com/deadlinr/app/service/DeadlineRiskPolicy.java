package com.deadlinr.app.service;
import com.deadlinr.app.dto.TodayPlan;
import com.deadlinr.app.dto.WorkItem;
import java.time.*;
import java.util.*;

// A rule explanation, never a probability or a mutation of the student's schedule.
public final class DeadlineRiskPolicy {
 private DeadlineRiskPolicy(){}
 public static TodayPlan.Risk assess(WorkItem task,Instant now,ZoneId zone){
  if(!task.kind().equals("TASK")||task.status().equals("DONE")||task.archivedAt()!=null)return new TodayPlan.Risk("NORMAL",List.of());
  List<String> reasons=new ArrayList<>();Instant due=task.dueAt();boolean overdue=due!=null&&due.isBefore(now);
  if(overdue)reasons.add("OVERDUE");
  else if(due!=null){
   if(due.atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate()))reasons.add("DUE_TODAY");
   else if(!due.isAfter(now.plus(Duration.ofHours(72))))reasons.add("DUE_SOON");
  }
  if(task.blocked())reasons.add("BLOCKED");
  if(task.hardDeadline()&&due!=null)reasons.add("HARD_DEADLINE");
  boolean attention=task.blocked()||reasons.contains("DUE_TODAY")||reasons.contains("DUE_SOON");
  return new TodayPlan.Risk(overdue?"HIGH":attention?"ATTENTION":"NORMAL",List.copyOf(reasons));
 }
 public static boolean plannedToday(WorkItem task,Instant now,ZoneId zone){
  return task.startAt()!=null&&task.startAt().atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate());
 }
}
