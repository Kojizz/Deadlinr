// Date inputs are wall-clock values. Only this boundary turns them into instants.
// Intl supplies the installed IANA timezone rules, including DST transitions.
export function wallTime(instant:string|Date,timeZone:string):string {
 const date=typeof instant==="string"?new Date(instant):instant;
 const parts=new Intl.DateTimeFormat("en-CA",{timeZone,year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(date);
 const get=(name:string)=>parts.find(p=>p.type===name)!.value;
 return `${get("year")}-${get("month")}-${get("day")}T${get("hour")}:${get("minute")}`;
}

export function resolveWallTime(value:string,timeZone:string):{instant:string;ambiguous:boolean} {
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))throw new Error("Choose a valid date and time.");
 const nominal=new Date(value+":00.000Z").getTime();
 if(!Number.isFinite(nominal)||new Date(nominal).toISOString().slice(0,16)!==value)throw new Error("Choose a valid date and time.");
 // Sample offsets on either side of a transition, then verify each candidate.
 const offsets=new Set<number>();
 for(const hours of [-36,-12,0,12,36]){
  const sample=nominal+hours*3600000;
  offsets.add(new Date(wallTime(new Date(sample),timeZone)+":00.000Z").getTime()-sample);
 }
 const candidates=[...offsets].map(offset=>nominal-offset).filter(candidate=>wallTime(new Date(candidate),timeZone)===value).sort((a,b)=>a-b);
 if(!candidates.length)throw new Error(`That time does not exist in ${timeZone} because the clocks change. Choose another time.`);
 return {instant:new Date(candidates[0]).toISOString(),ambiguous:candidates.length>1};
}
