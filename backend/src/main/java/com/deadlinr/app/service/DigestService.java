package com.deadlinr.app.service;
import com.deadlinr.app.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.core.env.Environment;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Service public class DigestService {
 private final JdbcTemplate db;private final PlanningSettingsService settings;private final JavaMailSender mail;private final String from;
 public DigestService(JdbcTemplate db,PlanningSettingsService settings,JavaMailSender mail,Environment env){this.db=db;this.settings=settings;this.mail=mail;from=env.getProperty("app.mail.from","deadlinr@localhost");}
 @Scheduled(fixedDelay=60000) @Transactional public void generate(){
  for(long owner:db.queryForList("select owner_id from user_settings",Long.class)){User u=new User();u.setId(owner);var prefs=settings.get(u);var now=ZonedDateTime.now(ZoneId.of(prefs.path("timeZone").asText()));if(now.toLocalTime().isBefore(LocalTime.parse(prefs.path("digestTime").asText())))continue;
   boolean daily=prefs.path("dailyDigest").asBoolean(),weekly=prefs.path("weeklyReview").asBoolean()&&now.getDayOfWeek()==DayOfWeek.SUNDAY;
   for(String kind:List.of("DAILY","WEEKLY")){if(kind.equals("DAILY")?!daily:!weekly)continue;
    int inserted=db.update("insert into digest_deliveries(owner_id,day,kind,email_pending)values(?,?,?,?) on conflict do nothing",owner,java.sql.Date.valueOf(now.toLocalDate()),kind,kind.equals("DAILY")&&prefs.path("emailDigest").asBoolean());if(inserted==0)continue;
    var counts=db.queryForMap("select count(*) filter(where due_at<?) as overdue,count(*) filter(where due_at>=? and due_at<?) as today,count(*) filter(where due_at>=? and due_at<?) as upcoming from work_items where owner_id=? and archived_at is null and status<>'DONE'",Timestamp.from(now.toInstant()),Timestamp.from(now.toLocalDate().atStartOfDay(now.getZone()).toInstant()),Timestamp.from(now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant()),Timestamp.from(now.toInstant()),Timestamp.from(now.plusDays(7).toInstant()),owner);
    String title=kind.equals("DAILY")?"Today's plan":"Weekly review",body=counts.get("overdue")+" overdue · "+counts.get("today")+" due today · "+counts.get("upcoming")+" in the next week. "+(kind.equals("DAILY")?"Choose your priorities in Today.":"Review overdue work and plan the week ahead.");
    Long id=db.queryForObject("insert into notifications(owner_id,title,body)values(?,?,?) returning id",Long.class,owner,title,body);db.update("update digest_deliveries set notification_id=? where owner_id=? and day=? and kind=?",id,owner,java.sql.Date.valueOf(now.toLocalDate()),kind);
   }
  }
 }
 @Scheduled(fixedDelay=60000) @Transactional public void sendEmails(){
  var rows=db.queryForList("select d.owner_id,d.day,d.kind,u.email,n.title,n.body from digest_deliveries d join users u on u.id=d.owner_id join notifications n on n.id=d.notification_id where d.email_pending and d.attempts<5 and (d.last_attempt_at is null or d.last_attempt_at<now()-interval '5 minutes') order by d.day limit 20 for update of d skip locked");
  for(var row:rows){User u=new User();u.setId(((Number)row.get("owner_id")).longValue());if(!settings.get(u).path("emailDigest").asBoolean()){db.update("update digest_deliveries set email_pending=false where owner_id=? and day=? and kind=?",u.getId(),row.get("day"),row.get("kind"));continue;}
   try{SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)row.get("email"));message.setSubject((String)row.get("title"));message.setText((String)row.get("body"));mail.send(message);db.update("update digest_deliveries set email_pending=false,email_sent_at=now() where owner_id=? and day=? and kind=?",u.getId(),row.get("day"),row.get("kind"));}
   catch(org.springframework.mail.MailException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Daily digest mail delivery failed; queued for retry");}
   db.update("update digest_deliveries set attempts=attempts+1,last_attempt_at=now() where owner_id=? and day=? and kind=?",u.getId(),row.get("day"),row.get("kind"));
  }
 }
}
