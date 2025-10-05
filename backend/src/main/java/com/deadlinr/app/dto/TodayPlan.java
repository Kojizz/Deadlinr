package com.deadlinr.app.dto;
import java.time.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record TodayPlan(String rulesVersion,Instant asOf,LocalDate date,String timeZone,
 Instant dayStartsAt,Instant dayEndsAt,@Schema(allowableValues={"today","upcoming"}) String view,List<Item> items,long total,int page,int size,Counts counts){
 @Schema(name="PlanningItem") public record Item(WorkItem task,Risk risk,boolean plannedToday){}
 @Schema(name="DeadlineRisk") public record Risk(@Schema(allowableValues={"HIGH","ATTENTION","NORMAL"}) String level,List<String> reasons){}
 @Schema(name="PlanningCounts") public record Counts(long overdue,long dueToday,long plannedToday,long inProgress){}
}
