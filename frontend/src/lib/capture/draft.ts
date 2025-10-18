import type {CaptureDraft,CaptureInput} from "./types";
import {resolveWallTime,wallTime} from "./timezone";

export function emptyDraft(timeZone:string):CaptureDraft {
 return {title:"",due:"",start:"",allDay:false,labels:"",priority:3,repeatRule:"NONE",timeZone};
}
export function captureInput(draft:CaptureDraft):CaptureInput {
 const title=draft.title.trim();
 if(!title||title.length>500)throw new Error("Enter a title of 1–500 characters.");
 const labels=[...new Set(draft.labels.split(/[,\s]+/).map(s=>s.replace(/^#/,"").toLowerCase()).filter(Boolean))];
 if(labels.length>20||labels.some(label=>!/^[-\p{L}\p{N}_]{1,40}$/u.test(label)))throw new Error("Use up to 20 labels, with 1–40 letters, numbers, underscores or hyphens each.");
 const resolve=(value:string,exact?:string)=>exact&&wallTime(exact,draft.timeZone)===value?exact:resolveWallTime(value,draft.timeZone).instant;
 const dueAt=draft.due?resolve(draft.allDay?draft.due.slice(0,10)+"T23:59":draft.due,draft.allDay?undefined:draft.dueInstant):null;
 const startAt=draft.start?resolve(draft.start,draft.startInstant):null;
 if(dueAt&&startAt&&startAt>dueAt)throw new Error("Start time must be before the deadline.");
 if(![1,2,3,4].includes(draft.priority))throw new Error("Choose a priority from P1 to P4.");
 return {kind:"TASK",title,dueAt,startAt,allDay:!!draft.due&&draft.allDay,priority:draft.priority,labels,repeatRule:draft.repeatRule,timeZone:draft.timeZone};
}
export function draftWarnings(draft:CaptureDraft,now:Date):string[] {
 const warnings:string[]=[];
 for(const [value,instant] of [[draft.due,draft.dueInstant],[draft.start,draft.startInstant]]){
  if(!value)continue;
  try{if(resolveWallTime(value,draft.timeZone).ambiguous)warnings.push(instant&&wallTime(instant,draft.timeZone)===value?"The clocks repeat this time. Your parsed UTC offset will be kept.":"The clocks repeat this time. The earlier occurrence will be saved.");}catch{/* Validation displays nonexistent times separately. */}
 }
 if(draft.due&&draft.due<wallTime(now,draft.timeZone))warnings.push("This deadline is in the past. You can edit it or keep it.");
 return [...new Set(warnings)];
}
