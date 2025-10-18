"use client";
import {keys} from "@/lib/query-keys";
import {useState} from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {createWork,openWork,type WorkList,type WorkItem} from "@/lib/work";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {useToast} from "@/components/ui/workspace";
export default function Calendar(){
 const [range,setRange]=useState({from:new Date(new Date().getFullYear(),new Date().getMonth(),1).toISOString(),to:new Date(new Date().getFullYear(),new Date().getMonth()+1,8).toISOString()});
 const [layers,setLayers]=useState(["TASK","EVENT"]);
 const refresh=useRefreshWorkspace(),toast=useToast();
 const query=useQuery({queryKey:keys.work.calendar(JSON.stringify(range)),queryFn:async()=>{const params=(page:number)=>"/work-items?"+new URLSearchParams({...range,size:"200",page:String(page)});const first=await api.get<WorkList>(params(0));const rest=await Promise.all(Array.from({length:Math.max(0,Math.ceil(first.total/200)-1)},(_,i)=>api.get<WorkList>(params(i+1))));return [first,...rest].flatMap(r=>r.items);}});
 const events=(query.data??[]).filter(w=>layers.includes(w.kind)).map(w=>({id:String(w.id),title:w.title,start:w.kind==="EVENT"?(w.startAt??w.dueAt!):w.dueAt!,end:w.kind==="EVENT"?w.dueAt??undefined:undefined,allDay:w.allDay,backgroundColor:"hsl(var(--primary))",borderColor:"transparent",textColor:"hsl(var(--primary-foreground))",extendedProps:{item:w}}));
 return <section className="workspace"><div className="page-heading"><div><p className="eyebrow">See what’s ahead</p><h1>Calendar</h1></div><button className="btn btn-primary" onClick={()=>createWork({kind:"EVENT"})}>+ New event</button></div>
 <div className="mb-5 flex flex-wrap gap-4">{["TASK","EVENT"].map(k=><label key={k} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={layers.includes(k)} onChange={e=>setLayers(s=>e.target.checked?[...s,k]:s.filter(x=>x!==k))}/>{k.toLowerCase()}s</label>)}</div>
 {query.isError&&<p role="alert" className="mb-4">Could not load calendar. <button className="btn" onClick={()=>void query.refetch()}>Retry</button></p>}
 <div className="panel p-3"><FullCalendar plugins={[dayGridPlugin,timeGridPlugin,interactionPlugin]} initialView="dayGridMonth" headerToolbar={{left:"prev,next today",center:"title",right:"dayGridMonth,timeGridWeek,timeGridDay"}} buttonText={{today:"Today",month:"Month",week:"Week",day:"Day"}} height="auto" firstDay={1} nowIndicator selectable editable events={events}
 datesSet={info=>setRange(old=>old.from===info.start.toISOString()&&old.to===info.end.toISOString()?old:{from:info.start.toISOString(),to:info.end.toISOString()})}
 dateClick={info=>createWork({kind:"TASK",dueAt:info.date.toISOString(),allDay:info.allDay})}
 select={info=>createWork({kind:"EVENT",startAt:info.start.toISOString(),dueAt:info.end.toISOString(),allDay:info.allDay})}
 eventClick={info=>openWork(Number(info.event.id))}
 eventDrop={async info=>{const w=info.event.extendedProps.item as WorkItem;try{await api.patch("/work-items/"+w.id,{dueAt:(w.kind==="EVENT"?info.event.end??info.event.start:info.event.start)?.toISOString(),...(w.kind==="EVENT"?{startAt:info.event.start?.toISOString()}:{}),allDay:info.event.allDay,version:w.version});await refresh();}catch(e){info.revert();toast({message:e instanceof Error?e.message:"Could not reschedule"});}}}
 eventResize={async info=>{const w=info.event.extendedProps.item as WorkItem;try{await api.patch("/work-items/"+w.id,{dueAt:info.event.end?.toISOString(),version:w.version});await refresh();}catch(e){info.revert();toast({message:String(e)});}}}/></div>
 </section>;
}



