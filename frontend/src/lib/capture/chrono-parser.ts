import {parse,type ParsedComponents} from "chrono-node";
import {emptyDraft} from "./draft";
import {wallTime} from "./timezone";
import type {TaskParser} from "./types";

export const chronoTaskParser:TaskParser={
 id:"chrono-offline",
 parse(text,{now,timeZone}){
  const draft=emptyDraft(timeZone),warnings:string[]=[];
  let remaining=text.trim();
  const priorities=[...remaining.matchAll(/\bp([1-4])\b/gi)];
  draft.priority=priorities.length?Number(priorities[0][1]):3;
  if(priorities.length>1)warnings.push("Several priorities found; using the first. Review the preview.");
  if(/\bp(?:0|[5-9]|\d{2,})\b/i.test(remaining))warnings.push("Priority must be p1, p2, p3 or p4.");
  draft.labels=[...new Set(Array.from(remaining.matchAll(/(?:^|\s)#([\p{L}\p{N}_-]+)/gu),m=>m[1].toLowerCase()))].join(", ");
  remaining=remaining.replace(/\bp[1-4]\b/gi,"").replace(/(^|\s)#[\p{L}\p{N}_-]+/gu,"$1");
  const recurrence=remaining.match(/\bevery\s+(day|week|month|year)\b/i);
  if(recurrence){draft.repeatRule=({day:"DAILY",week:"WEEKLY",month:"MONTHLY",year:"YEARLY"} as Record<string,string>)[recurrence[1].toLowerCase()];remaining=remaining.replace(recurrence[0],"");}
  // Parse calendar components against the user's wall clock, not the process TZ.
  const reference=new Date(wallTime(now,timeZone)+":00.000Z");
  const results=parse(remaining,{instant:reference,timezone:0},{forwardDate:true});
  const spans:{from:number;to:number}[]=[];
  let previousEnd=0;
  function dateValue(parts:ParsedComponents,deadline:boolean):string {
   function exact(instant:Date){
    instant.setUTCSeconds(0,0);
    if(deadline)draft.dueInstant=instant.toISOString();else draft.startInstant=instant.toISOString();
    return wallTime(instant,timeZone);
   }
   const pad=(n:number|null)=>String(n??0).padStart(2,"0");
   const timed=parts.isCertain("hour");
   const value=`${parts.get("year")}-${pad(parts.get("month"))}-${pad(parts.get("day"))}T${timed?pad(parts.get("hour")):deadline?"23":"00"}:${timed?pad(parts.get("minute")):deadline?"59":"00"}`;
   // Chrono marks relative hours/minutes with the reference offset, even though
   // the user did not name a timezone. Apply their elapsed duration to the real
   // instant; this also handles crossing a DST transition correctly.
   if(parts.tags().has("result/relativeDateAndTime")){
    return exact(new Date(now.getTime()+parts.date().getTime()-reference.getTime()));
   }
   if(parts.isCertain("timezoneOffset")){
    const instant=new Date(new Date(value+":00.000Z").getTime()-(parts.get("timezoneOffset")??0)*60000);
    return exact(instant);
   }
   return value;
  }
  for(const result of results){
   const prefix=remaining.slice(previousEnd,result.index);
   const marker=prefix.match(/\b(start(?:s|ing)?|due|by|deadline|on)\s*:?\s*$/i);
   const isStart=!!marker&&/^start/i.test(marker[1]);
   const from=marker?result.index-marker[0].length:result.index;
   if(result.end&&!draft.start&&!draft.due){
    draft.start=dateValue(result.start,false);draft.due=dateValue(result.end,true);draft.allDay=!result.end.isCertain("hour");
   }else if(isStart&&!draft.start){draft.start=dateValue(result.start,false);}
   else if(!isStart&&!draft.due){draft.due=dateValue(result.start,true);draft.allDay=!result.start.isCertain("hour");}
   else {warnings.push("More than one date for the same field; extra text was kept in the title. Review both dates.");previousEnd=result.index+result.text.length;continue;}
   spans.push({from,to:result.index+result.text.length});previousEnd=result.index+result.text.length;
  }
  for(const span of spans.reverse())remaining=remaining.slice(0,span.from)+" "+remaining.slice(span.to);
  draft.title=remaining.replace(/\s+/g," ").trim();
  if(!results.length&&/\b(?:today|tomorrow|yesterday|due|deadline|next|\d{4}-\d{2}-\d{2})\b/i.test(remaining))warnings.push("No date understood. Choose a deadline in the preview or keep this as an undated task.");
  return {draft,warnings};
 }
};
