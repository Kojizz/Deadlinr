"use client";
import {keys} from "@/lib/query-keys";
import {useState} from "react";
import {DayPicker} from "react-day-picker";
import "react-day-picker/style.css";
import {useQuery} from "@tanstack/react-query";
import {CalendarDays} from "lucide-react";
import {api} from "@/lib/api";
import {localDate,dueLabel} from "@/lib/work";
import {formatTime} from "@/lib/datetime";
export function DeadlinePicker({value,onChange,allDay=false,onAllDay,label="Due date"}:{value:string|null;onChange:(v:string|null)=>void;allDay?:boolean;onAllDay?:(v:boolean)=>void;label?:string}){
 const [open,setOpen]=useState(false),[month,setMonth]=useState(value?new Date(value):new Date()),[natural,setNatural]=useState(""),[error,setError]=useState("");
 const selected=value?new Date(value):undefined;
 const from=new Date(month.getFullYear(),month.getMonth(),1),to=new Date(month.getFullYear(),month.getMonth()+1,1);
 const days=useQuery({queryKey:keys.work.deadlineDays(localDate(from)),queryFn:()=>api.get<string[]>("/work-items/deadline-days?"+new URLSearchParams({from:from.toISOString(),to:to.toISOString(),zone:Intl.DateTimeFormat().resolvedOptions().timeZone})),enabled:open,staleTime:15000});
 function choose(day:Date|undefined){if(!day){onChange(null);return;}day.setHours(allDay?0:selected?.getHours()??18,allDay?0:selected?.getMinutes()??0,0,0);onChange(day.toISOString());}
 const presets=[["Today",0],["Tomorrow",1],["Next Monday",((8-new Date().getDay())%7)||7],["In a week",7]] as const;
 return <div className="relative"><button type="button" className="btn w-full justify-start" aria-label={label} aria-expanded={open} onClick={()=>setOpen(!open)}><CalendarDays size={16}/>{value?dueLabel(value)+(allDay?"":" · "+new Date(value).toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"})):label}</button>
 {open&&<div className="panel absolute left-0 top-full z-50 mt-2 w-[310px] max-w-[85vw] p-3 shadow-lg" role="dialog" aria-label={"Choose "+label.toLowerCase()} onKeyDown={e=>{if(e.key==="Escape"){e.stopPropagation();setOpen(false);}}}>
 <div className="flex flex-wrap gap-1">{presets.map(([name,offset])=><button type="button" className="filter-chip" key={name} onClick={()=>{const d=new Date();d.setDate(d.getDate()+offset);choose(d);setMonth(d);}}>{name}</button>)}<button type="button" className="filter-chip" onClick={()=>onChange(null)}>No date</button></div>
 <DayPicker mode="single" month={month} onMonthChange={setMonth} selected={selected} onSelect={choose} weekStartsOn={1} showOutsideDays modifiers={{deadline:(d:Date)=>days.data?.includes(localDate(d))??false,weekend:{dayOfWeek:[0,6]}}} modifiersClassNames={{deadline:"deadline-dot",weekend:"weekend-day"}}/>
 <label className="field">Natural date<input aria-label="Natural date" placeholder="fri 18:00" value={natural} onChange={e=>setNatural(e.target.value)} onKeyDown={async e=>{if(e.key==="Enter"){e.preventDefault();const chrono=await import("chrono-node");const date=chrono.parseDate(natural,new Date(),{forwardDate:true});if(date){onChange(date.toISOString());setMonth(date);setError("");}else setError("Try tomorrow 18:00 or a calendar date.");}}}/></label>
 {error&&<p role="alert" className="text-xs text-destructive">{error}</p>}
 <div className="mt-3 flex items-end gap-3"><label className="field flex-1">Time<input aria-label="Due time" type="time" disabled={allDay} value={selected?String(selected.getHours()).padStart(2,"0")+":"+String(selected.getMinutes()).padStart(2,"0"):"18:00"} onChange={e=>{const [h,m]=e.target.value.split(":").map(Number);const d=selected??new Date();d.setHours(h,m,0,0);onChange(d.toISOString());}}/></label>{onAllDay&&<label className="flex gap-2 pb-3 text-sm"><input type="checkbox" checked={allDay} onChange={e=>onAllDay(e.target.checked)}/>All day</label>}</div>
 <button type="button" className="btn btn-primary mt-3 w-full" onClick={()=>setOpen(false)}>Done</button></div>}</div>;
}


