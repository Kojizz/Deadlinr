package com.deadlinr.app.service;
import com.deadlinr.app.dto.WorkItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class DeadlineRiskPolicyTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 final Instant now=Instant.parse("2026-10-13T05:00:00Z");final ZoneId zone=ZoneId.of("Asia/Almaty");
 WorkItem task(String fields)throws Exception{return json.readValue("{\"kind\":\"TASK\",\"status\":\"TODO\",\"timeZone\":\"Asia/Almaty\","+fields+"}",WorkItem.class);}
 @Test void overdueExplainsFixedDeadlineAndBlockerWithoutChangingTheInstant()throws Exception{
  var task=task("\"dueAt\":\"2026-10-13T04:59:59Z\",\"hardDeadline\":true,\"blocked\":true");
  var risk=DeadlineRiskPolicy.assess(task,now,zone);
  assertThat(risk.level()).isEqualTo("HIGH");assertThat(risk.reasons()).containsExactly("OVERDUE","BLOCKED","HARD_DEADLINE");
  assertThat(task.dueAt()).isEqualTo(Instant.parse("2026-10-13T04:59:59Z"));
 }
 @Test void theSameInstantHasZoneSpecificTodayReasons()throws Exception{
  var task=task("\"dueAt\":\"2026-10-13T19:30:00Z\"");
  assertThat(DeadlineRiskPolicy.assess(task,now,zone).reasons()).containsExactly("DUE_SOON");
  assertThat(DeadlineRiskPolicy.assess(task,now,ZoneId.of("UTC")).reasons()).containsExactly("DUE_TODAY");
  assertThat(DeadlineRiskPolicy.assess(task,now,ZoneId.of("Asia/Qyzylorda"))).isEqualTo(DeadlineRiskPolicy.assess(task,now,zone));
 }
 @Test void exactNowIsDueTodayRatherThanAlreadyOverdueAndSeventyTwoHoursIsInclusive()throws Exception{
  assertThat(DeadlineRiskPolicy.assess(task("\"dueAt\":\"2026-10-13T05:00:00Z\""),now,zone).reasons()).containsExactly("DUE_TODAY");
  assertThat(DeadlineRiskPolicy.assess(task("\"dueAt\":\"2026-10-16T05:00:00Z\""),now,zone).reasons()).containsExactly("DUE_SOON");
  assertThat(DeadlineRiskPolicy.assess(task("\"dueAt\":\"2026-10-16T05:00:01Z\""),now,zone).level()).isEqualTo("NORMAL");
 }
 @Test void anUndatedBlockerNeedsReviewButDoesNotInventADeadline()throws Exception{
  var task=task("\"blocked\":true");
  assertThat(DeadlineRiskPolicy.assess(task,now,zone).reasons()).containsExactly("BLOCKED");
  assertThat(DeadlineRiskPolicy.assess(task,now,zone).level()).isEqualTo("ATTENTION");assertThat(task.dueAt()).isNull();
 }
 @Test void distantFixedDeadlineIsInformationalAndPriorityAloneIsNotRisk()throws Exception{
  var risk=DeadlineRiskPolicy.assess(task("\"dueAt\":\"2026-11-01T05:00:00Z\",\"hardDeadline\":true,\"priority\":1"),now,zone);
  assertThat(risk.level()).isEqualTo("NORMAL");assertThat(risk.reasons()).containsExactly("HARD_DEADLINE");
 }
 @Test void completedArchivedAndEventRecordsHaveNoTaskRisk()throws Exception{
  for(String fields:new String[]{"\"status\":\"DONE\"","\"archivedAt\":\"2026-10-12T05:00:00Z\"","\"kind\":\"EVENT\""}){
   var risk=DeadlineRiskPolicy.assess(task(fields+",\"dueAt\":\"2026-10-12T05:00:00Z\""),now,zone);
   assertThat(risk.level()).isEqualTo("NORMAL");assertThat(risk.reasons()).isEmpty();
  }
 }
 @Test void plannedDateUsesTheSelectedZoneAcrossDst()throws Exception{
  var task=task("\"startAt\":\"2026-03-09T03:30:00Z\"");var reference=Instant.parse("2026-03-08T12:00:00Z");
  assertThat(DeadlineRiskPolicy.plannedToday(task,reference,ZoneId.of("America/New_York"))).isTrue();
  assertThat(DeadlineRiskPolicy.plannedToday(task,reference,ZoneId.of("UTC"))).isFalse();
 }
}
