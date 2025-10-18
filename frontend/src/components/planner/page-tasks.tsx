"use client";
import {Flag,CalendarDays} from "lucide-react";
import {useState} from "react";
import {useWorkList,useWorkUpdate} from "@/hooks/use-work";
import {openWork,createWork,type WorkItem} from "@/lib/work";
import {useToast} from "@/components/ui/workspace";
import {PriorityPill} from "@/components/ui/workspace";
import {DeadlinePicker} from "./date-picker";
import {api} from "@/lib/api";
import {useRefreshWorkspace} from "@/hooks/use-workspace";

export function PageTasks({pageId}:{pageId:number}){
  const[open,setOpen]=useState(true);
  const list=useWorkList({pageId:String(pageId),view:"all",size:"100"});
  const items=list.data?.items??[];
  const openCount=items.filter(w=>w.status!=="DONE").length;
  const update=useWorkUpdate(),toast=useToast(),refresh=useRefreshWorkspace();
  return <section className="mt-8">
    <div className="flex items-center justify-between mb-2">
      <button className="btn" onClick={()=>setOpen(!open)}>
        {(open?"▾":"▸")+" Tasks & events · "+openCount+" open"}
      </button>
      <button className="btn btn-primary" onClick={()=>createWork({kind:"TASK",pageId})}>+ Add</button>
    </div>
    {open&&(list.isPending?<div className="skeleton h-24 rounded-xl"/>:!items.length?<EmptyPageTasks/>:PageTasksList(items,update))}
  </section>;
}
function EmptyPageTasks(){return <p className="text-sm text-muted-foreground py-4">No tasks or events on this page yet. Use “+ Add” to create one.</p>;}
function PageTasksList(items:WorkItem[],update:ReturnType<typeof useWorkUpdate>){
  const open=items.filter(w=>w.status!=="DONE");
  const done=items.filter(w=>w.status==="DONE");
  return <div className="grid gap-1">
    {open.map(w=><PageTaskRow key={w.id} w={w} update={update}/>)}
    {done.length>0&&<p className="mt-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">Completed</p>}
    {done.map(w=><PageTaskRow key={w.id} w={w} update={update}/>)}
  </div>;
}
function PageTaskRow({w,update}:{w:WorkItem;update:ReturnType<typeof useWorkUpdate>}){
  const overdue=w.dueAt&&w.status!=="DONE"&&new Date(w.dueAt)<new Date();
  return <div className="list-row group" data-item-id={w.id}>
    <input type="checkbox" aria-label={"Complete "+w.title} checked={w.status==="DONE"} onChange={e=>update.mutate({id:w.id,patch:{status:e.target.checked?"DONE":"TODO"}})}/>
    <button type="button" className="min-w-0 flex-1 text-left" onClick={()=>openWork(w.id)}>
      <span className={"text-sm truncate block "+(w.status==="DONE"?"line-through text-muted-foreground":"")}>{w.title}</span>
    </button>
    <span className="hidden sm:inline-flex" title={w.kind}>{w.kind==="EVENT"?<CalendarDays size={15}/>:<Flag size={15}/>}</span>
    <span className="hidden sm:inline-flex" title={"Priority P"+w.priority}><PriorityPill priority={w.priority}/></span>
    <span className="text-[10px] text-muted-foreground">{w.fileCount} files</span>
    <span onClick={e=>e.stopPropagation()} className={overdue?"overdue-chip":""}>
      <DeadlinePicker label={"Due date of "+w.title} value={w.dueAt} allDay={w.allDay} onChange={v=>update.mutate({id:w.id,patch:{dueAt:v}})}/>
    </span>
  </div>;
}

