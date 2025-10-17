package com.deadlinr.app.dto;
import java.time.Instant;
import java.util.List;
public record WorkItem(long id,long ownerId,String kind,String title,String description,Long pageId,Long courseId,Long parentId,
 String status,int priority,Instant dueAt,Instant startAt,boolean allDay,boolean hardDeadline,String location,String link,
 String repeatRule,List<String> labels,List<Integer> reminderMinutes,double position,Instant completedAt,Instant archivedAt,
 Instant createdAt,long version,String timeZone,int fileCount,int subtaskCount,int subtasksDone,boolean blocked,String pageTitle){}
