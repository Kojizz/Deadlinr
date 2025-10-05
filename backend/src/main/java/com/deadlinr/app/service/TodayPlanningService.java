package com.deadlinr.app.service;
import com.deadlinr.app.dto.TodayPlan;
import com.deadlinr.app.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service public class TodayPlanningService {
 private final JdbcTemplate db;private final WorkService work;private final Clock clock;
 public TodayPlanningService(JdbcTemplate db,WorkService work,Clock clock){this.db=db;this.work=work;this.clock=clock;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public TodayPlan today(User user,String zone,String view,int page,int size){
  ZoneId tz;try{tz=ZoneId.of(zone);}catch(Exception e){throw new IllegalArgumentException("Invalid timezone");}
  if(!Set.of("today","upcoming").contains(view))throw new IllegalArgumentException("View must be today or upcoming");
  if(page<0||page>10000||size<1||size>50)throw new IllegalArgumentException("Page must be 0–10000 and size 1–50");
  Instant now=clock.instant();LocalDate date=now.atZone(tz).toLocalDate();
  Instant start=date.atStartOfDay(tz).toInstant(),end=date.plusDays(1).atStartOfDay(tz).toInstant(),upcomingEnd=date.plusDays(8).atStartOfDay(tz).toInstant();
  String base=" where w.owner_id=? and w.kind='TASK' and w.archived_at is null and w.status<>'DONE'";
  String selected=view.equals("today")?"(w.due_at<? or (w.start_at>=? and w.start_at<?) or w.status='IN_PROGRESS')":"(w.due_at>=? and w.due_at<?)";
  List<Object> selectedArgs=view.equals("today")?List.of(stamp(end),stamp(start),stamp(end)):List.of(stamp(end),stamp(upcomingEnd));
  List<Object> countArgs=new ArrayList<>(List.of(stamp(now),stamp(start),stamp(end),stamp(start),stamp(end)));
  countArgs.addAll(selectedArgs);countArgs.add(user.getId());
  var counts=db.queryForMap("select count(*) filter(where w.due_at<?) as overdue,"
   +"count(*) filter(where w.due_at>=? and w.due_at<?) as due_today,"
   +"count(*) filter(where w.start_at>=? and w.start_at<?) as planned_today,"
   +"count(*) filter(where w.status='IN_PROGRESS') as in_progress,"
   +"count(*) filter(where "+selected+") as total from work_items w"+base,countArgs.toArray());
  List<Object> args=new ArrayList<>();args.add(user.getId());args.addAll(selectedArgs);
  args.add(stamp(now));args.add(stamp(start));args.add(stamp(end));args.add(size);args.add(page*size);
  var tasks=work.itemsFor(base+" and "+selected+" order by (w.due_at<?) desc nulls last,"
   +"(w.due_at>=? and w.due_at<?) desc nulls last,w.hard_deadline desc,w.priority,w.due_at nulls last,w.id limit ? offset ?",args.toArray());
  var items=tasks.stream().map(task->new TodayPlan.Item(task,DeadlineRiskPolicy.assess(task,now,tz),DeadlineRiskPolicy.plannedToday(task,now,tz))).toList();
  return new TodayPlan("1",now,date,tz.getId(),start,end,view,items,number(counts,"total"),page,size,
   new TodayPlan.Counts(number(counts,"overdue"),number(counts,"due_today"),number(counts,"planned_today"),number(counts,"in_progress")));
 }
 private static Timestamp stamp(Instant value){return Timestamp.from(value);}
 private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
}
