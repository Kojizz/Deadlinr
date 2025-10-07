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
