package com.deadlinr.app.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for task/item counts. Both the Tasks list
 * (WorkService) and the workspace overview (WorkspaceService) use this one
 * query so the numbers always agree.
 */
@Service
public class WorkCounts {
 private final NamedParameterJdbcTemplate named;
 public WorkCounts(JdbcTemplate db){this.named=new NamedParameterJdbcTemplate(db);}

 public Map<String,Object> compute(String where,MapSqlParameterSource p,ZoneId zone){
  var now=ZonedDateTime.now(zone);
  var day=now.toLocalDate().atStartOfDay(zone).toInstant();
  var tomorrow=now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
  var monday=now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant();
  var nextMonday=now.toLocalDate().with(java.time.DayOfWeek.MONDAY).plusWeeks(1).atStartOfDay(zone).toInstant();
  p.addValue("dayStart",day.toString()).addValue("dayEnd",tomorrow.toString()).addValue("weekStart",monday.toString()).addValue("weekEnd",nextMonday.toString());
  return named.queryForMap("select count(*) as all,count(*) filter(where status='TODO') as todo,count(*) filter(where status='IN_PROGRESS') as progress,count(*) filter(where status='DONE') as done,count(*) filter(where status='SOMEDAY') as someday,count(*) filter(where status<>'DONE' and due_at<now()) as overdue,count(*) filter(where status<>'DONE' and due_at>=cast(:dayStart as timestamptz) and due_at<cast(:dayEnd as timestamptz)) as \"dueToday\",count(*) filter(where status<>'DONE' and due_at>=cast(:weekStart as timestamptz) and due_at<cast(:weekEnd as timestamptz)) as \"dueThisWeek\" from work_items w"+where,p);
 }
}
