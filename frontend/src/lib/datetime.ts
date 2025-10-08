// Single date/time formatting helper with an explicit locale (default English)
// and 24-hour clock. Use these everywhere instead of toLocaleString/toLocaleDateString
// with an undefined locale, and never include seconds.
const MONTH_DAY = new Intl.DateTimeFormat("en-US", {month: "short", day: "numeric"});
const DATE = new Intl.DateTimeFormat("en-US", {month: "short", day: "numeric", year: "numeric"});
const TIME = new Intl.DateTimeFormat("en-GB", {hour: "2-digit", minute: "2-digit", hour12: false});
const DATE_TIME = new Intl.DateTimeFormat("en-US", {month: "short", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false});

function toDate(value: string | number | Date): Date {
  return value instanceof Date ? value : new Date(value);
}

export function formatMonthDay(value: string | number | Date): string {
  return MONTH_DAY.format(toDate(value));
}

export function formatDate(value: string | number | Date, timeZone?: string): string {
  return (timeZone ? new Intl.DateTimeFormat("en-US", {month:"short",day:"numeric",year:"numeric",timeZone}) : DATE).format(toDate(value));
}

export function formatTime(value: string | number | Date): string {
  return TIME.format(toDate(value));
}

export function formatDateTime(value: string | number | Date, timeZone?: string): string {
  return (timeZone ? new Intl.DateTimeFormat("en-US", {month:"short",day:"numeric",year:"numeric",hour:"2-digit",minute:"2-digit",hourCycle:"h23",timeZone}) : DATE_TIME).format(toDate(value));
}

// A zone's reference city is not the user's location. Use its offset at the
// displayed instant (including historical/DST changes), without altering it.
export function formatUtcOffset(value:string|number|Date,timeZone:string):string {
 const part=new Intl.DateTimeFormat("en-US",{timeZone,timeZoneName:"shortOffset"}).formatToParts(toDate(value)).find(p=>p.type==="timeZoneName");
 return part?.value.replace(/^GMT/,"UTC").replace(/^UTC[+-]0+(?::0+)?$/,"UTC")??timeZone;
}
