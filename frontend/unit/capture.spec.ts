import {test,expect} from "@playwright/test";
import {chronoTaskParser} from "../src/lib/capture/chrono-parser";
import {captureInput,draftWarnings,emptyDraft} from "../src/lib/capture/draft";
import {resolveWallTime,wallTime} from "../src/lib/capture/timezone";

const context={now:new Date("2026-10-12T05:00:00Z"),timeZone:"Asia/Qyzylorda"};
const phrases:[string,string,string,boolean][]=[
 ["Report due Friday 18:00 #work p1","Report","2026-10-16T18:00",false],
 ["Read tomorrow","Read","2026-10-13T23:59",true],
 ["Quiz next Mon","Quiz","2026-10-19T23:59",true],
 ["Essay today 18:00","Essay","2026-10-12T18:00",false],
 ["Homework yesterday","Homework","2026-10-11T23:59",true],
 ["Past report due 2024-01-15 09:30","Past report","2024-01-15T09:30",false],
 ["Read a chapter","Read a chapter","",false],
 ["Lab due Oct 20, 2026","Lab","2026-10-20T23:59",true],
 ["Call tomorrow at 6pm","Call","2026-10-13T18:00",false],
 ["Meet in 2 hours","Meet","2026-10-12T12:00",false],
 ["Submit in 3 days","Submit","2026-10-15T23:59",true],
 ["Review due Sunday","Review","2026-10-18T23:59",true],
 ["New term due Jan 1, 2027","New term","2027-01-01T23:59",true],
 ["Leap day due Feb 29, 2028","Leap day","2028-02-29T23:59",true],
 ["Report tomorrow 00:00","Report","2026-10-13T00:00",false],
 ["  Draft   tomorrow   p2 #Uni  ","Draft","2026-10-13T23:59",true],
 ["Study every week Friday 17:00","Study","2026-10-16T17:00",false],
 ["Finish by Friday 18:00","Finish","2026-10-16T18:00",false],
 ["Call due 2026-10-16 18:00 UTC","Call","2026-10-16T23:00",false],
 ["No deadline #университет p4","No deadline","",false],
];
for(const [phrase,title,due,allDay] of phrases)test(`phrase: ${phrase}`,async()=>{
 const {draft}=await chronoTaskParser.parse(phrase,context);
 expect(draft.title).toBe(title);expect(draft.due).toBe(due);expect(draft.allDay).toBe(allDay);
 const saved=JSON.parse(JSON.stringify(captureInput(draft)));
 expect(saved.timeZone).toBe(context.timeZone);
 if(due)expect(wallTime(saved.dueAt,saved.timeZone)).toBe(due);else expect(saved.dueAt).toBeNull();
});
test("labels, priority, recurrence and explicit start are independent",async()=>{
 const {draft}=await chronoTaskParser.parse("Report start tomorrow 09:00 due Friday 18:00 #Work #work #大学 p1 every week",context);
 expect(draft).toMatchObject({title:"Report",start:"2026-10-13T09:00",due:"2026-10-16T18:00",labels:"work, 大学",priority:1,repeatRule:"WEEKLY"});
 expect(captureInput(draft)).toMatchObject({startAt:"2026-10-13T04:00:00.000Z",dueAt:"2026-10-16T13:00:00.000Z",labels:["work","大学"]});
});
test("a range fills both start and deadline",async()=>{
 const {draft}=await chronoTaskParser.parse("Study tomorrow from 9am to 11am",context);
 expect(draft).toMatchObject({title:"Study",start:"2026-10-13T09:00",due:"2026-10-13T11:00"});
});
test("past dates are preserved and warned about",async()=>{
 const {draft}=await chronoTaskParser.parse("Essay yesterday",context);
 expect(draftWarnings(draft,context.now).join(" ")).toContain("in the past");
 expect(captureInput(draft).dueAt).toBe("2026-10-11T18:59:00.000Z");
});
test("invalid dates stay in the title for review",async()=>{
 const {draft,warnings}=await chronoTaskParser.parse("Essay due 2026-02-30",context);
 expect(draft.due).toBe("");expect(draft.title).toContain("2026-02-30");expect(warnings.length).toBeGreaterThan(0);
});
test("multiple deadlines are never silently discarded",async()=>{
 const {draft,warnings}=await chronoTaskParser.parse("Essay tomorrow and Friday",context);
 expect(warnings.length).toBeGreaterThan(0);expect(draft.title).toContain("Friday");
});
test("empty or date-only phrases need a title",async()=>{
 for(const phrase of ["","tomorrow #work p1"]){const {draft}=await chronoTaskParser.parse(phrase,context);expect(()=>captureInput(draft)).toThrow("title");}
});
test("start after deadline and invalid labels are rejected",()=>{
 const draft={...emptyDraft(context.timeZone),title:"Essay",start:"2026-10-17T09:00",due:"2026-10-16T18:00"};
 expect(()=>captureInput(draft)).toThrow("Start time");expect(()=>captureInput({...draft,start:"",labels:"bad!label"})).toThrow("labels");
});
for(const [zone,instant] of [["Asia/Qyzylorda","2026-10-16T13:00:00.000Z"],["America/New_York","2026-10-16T22:00:00.000Z"],["Asia/Kathmandu","2026-10-16T12:15:00.000Z"],["Pacific/Auckland","2026-10-16T05:00:00.000Z"]]){
 test(`save/reload preserves wall time in ${zone}`,()=>{
  const saved=JSON.parse(JSON.stringify(captureInput({...emptyDraft(zone),title:"Essay",due:"2026-10-16T18:00"})));
  expect(saved.dueAt).toBe(instant);expect(wallTime(saved.dueAt,saved.timeZone)).toBe("2026-10-16T18:00");
 });
}
test("DST gaps are rejected; overlaps select the earlier occurrence",()=>{
 expect(()=>resolveWallTime("2026-03-08T02:30","America/New_York")).toThrow("does not exist");
 expect(resolveWallTime("2026-11-01T01:30","America/New_York")).toEqual({instant:"2026-11-01T05:30:00.000Z",ambiguous:true});
 expect(draftWarnings({...emptyDraft("America/New_York"),due:"2026-11-01T01:30"},context.now).join(" ")).toContain("earlier occurrence");
});
test("parsing uses the supplied zone rather than the machine timezone",async()=>{
 const {draft}=await chronoTaskParser.parse("Essay tomorrow 18:00",{now:new Date("2026-10-12T23:30:00Z"),timeZone:"Asia/Qyzylorda"});
 expect(draft.due).toBe("2026-10-14T18:00");
});
test("explicit offsets preserve the second occurrence of a repeated hour",async()=>{
 const {draft}=await chronoTaskParser.parse("Essay due 2026-11-01 01:30 -0500",{...context,timeZone:"America/New_York"});
 expect(draft.due).toBe("2026-11-01T01:30");expect(captureInput(draft).dueAt).toBe("2026-11-01T06:30:00.000Z");
 expect(draftWarnings(draft,context.now).join(" ")).toContain("offset will be kept");
});
test("relative hours measure elapsed time across DST transitions",async()=>{
 for(const [now,dueAt,due] of [["2026-03-08T06:30:00Z","2026-03-08T08:30:00.000Z","2026-03-08T04:30"],["2026-11-01T04:30:00Z","2026-11-01T06:30:00.000Z","2026-11-01T01:30"]]){
  const {draft}=await chronoTaskParser.parse("Essay in 2 hours",{now:new Date(now),timeZone:"America/New_York"});
  expect(draft.due).toBe(due);expect(captureInput(draft).dueAt).toBe(dueAt);
 }
});
