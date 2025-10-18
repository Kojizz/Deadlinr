"use client";
import {useState} from "react";
import {DayPicker,type DateRange} from "react-day-picker";
import "react-day-picker/style.css";
import {CalendarDays} from "lucide-react";
import {localDate} from "@/lib/work";
import {formatMonthDay} from "@/lib/datetime";

type Mode={from?:string;to?:string;due?:string};
function fmt(iso:string){return formatMonthDay(new Date(iso+"T00:00:00"));}
function startOfWeek(d:Date){const x=new Date(d);const day=(x.getDay()+6)%7;x.setDate(x.getDate()-day);x.setHours(0,0,0,0);return x;}

export function DateRangeFilter({params,setMany}:{params:Record<string,string>;setMany:(u:Record<string,string>)=>void}){
 const[isOpen,setOpen]=useState(false);
 const from=params.from?.slice(0,10),to=params.to?.slice(0,10);
 const special=params.due==="nodate"?"No date":params.due==="overdue"?"Overdue":undefined;
 const range:DateRange|undefined=from?{from:new Date(from+"T00:00:00"),to:to?new Date(to+"T00:00:00"):undefined}:undefined;
 const label=special??(from?(to?(from===to?fmt(from):fmt(from)+" – "+fmt(to)):fmt(from)+" – …"):"Any date");
 function apply(r:DateRange|undefined){
  if(!r||!r.from){setMany({from:"",to:"",due:""});return;}
  let a=r.from,b=r.to??r.from;if(b<a){const t=a;a=b;b=t;}
  setMany({from:localDate(a),to:localDate(b),due:""});setOpen(false);
 }
 function preset(name:string){
  const now=new Date();now.setHours(0,0,0,0);
  const add=(d:Date,n:number)=>{const x=new Date(d);x.setDate(x.getDate()+n);return x;};
  switch(name){
   case "Today":apply({from:now,to:now});break;
   case "Tomorrow":apply({from:add(now,1),to:add(now,1)});break;
   case "This week":apply({from:startOfWeek(now),to:add(startOfWeek(now),6)});break;
   case "Next 7 days":apply({from:now,to:add(now,6)});break;
   case "This month":apply({from:new Date(now.getFullYear(),now.getMonth(),1),to:new Date(now.getFullYear(),now.getMonth()+1,0)});break;
   case "Overdue":setMany({due:"overdue",from:"",to:""});setOpen(false);break;
   case "No date":setMany({due:"nodate",from:"",to:""});setOpen(false);break;
   case "Custom":break;
  }
 }
 return <div className="relative inline-flex"><button type="button" className="btn" aria-label="Date range filter" aria-expanded={isOpen} onClick={()=>setOpen(!isOpen)}><CalendarDays size={16}/>{label}</button>
 {isOpen&&<div className="panel absolute left-0 top-full z-50 mt-2 w-[320px] max-w-[85vw] p-3 shadow-lg" role="dialog" aria-label="Choose date range" onKeyDown={e=>{if(e.key==="Escape"){e.stopPropagation();setOpen(false);}}}>
 <div className="flex flex-wrap gap-1 mb-2">{["Today","Tomorrow","This week","Next 7 days","This month","Overdue","No date","Custom"].map(p=><button type="button" key={p} className="filter-chip" onClick={()=>preset(p)}>{p}</button>)}</div>
 <DayPicker mode="range" selected={range} onSelect={apply} weekStartsOn={1} numberOfMonths={1}/>
 <div className="mt-2 flex items-center gap-2"><button type="button" className="btn" onClick={()=>{setMany({from:"",to:"",due:""});setOpen(false);}}>Clear</button><button type="button" className="btn btn-primary flex-1" onClick={()=>setOpen(false)}>Done</button></div>
 </div>}</div>;
}



