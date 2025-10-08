import {test,expect} from "@playwright/test";
import {formatUtcOffset} from "../src/lib/datetime";

for(const [zone,date,label] of [
 ["Asia/Almaty","2026-10-16T13:00:00Z","UTC+5"],
 ["Asia/Qyzylorda","2026-10-16T13:00:00Z","UTC+5"],
 ["UTC","2026-10-16T13:00:00Z","UTC"],
 ["Asia/Kathmandu","2026-10-16T13:00:00Z","UTC+5:45"],
 ["America/New_York","2026-07-01T12:00:00Z","UTC-4"],
 ["America/New_York","2026-12-01T12:00:00Z","UTC-5"],
])test(`offset at the deadline: ${zone} ${date}`,()=>{
 const instant=new Date(date);expect(formatUtcOffset(instant,zone)).toBe(label);expect(instant.toISOString()).toBe(date.replace("Z",".000Z"));
});

// ICU versions differ in how they spell a zero offset on Windows and Linux.
for(const raw of ["GMT","GMT+0","GMT-0","GMT+00:00"])test(`zero offset is stable when Intl returns ${raw}`,()=>{
 const original=Intl.DateTimeFormat;
 Intl.DateTimeFormat=new Proxy(original,{construct(target,args){
  const formatter=Reflect.construct(target,args);
  Object.defineProperty(formatter,"formatToParts",{value:()=>[{type:"timeZoneName",value:raw}]});
  return formatter;
 }});
 try{
  const instant=new Date("2026-10-16T13:00:00Z");
  expect(formatUtcOffset(instant,"UTC")).toBe("UTC");
  expect(instant.toISOString()).toBe("2026-10-16T13:00:00.000Z");
 }finally{Intl.DateTimeFormat=original;}
});
