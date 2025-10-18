"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {keys} from "@/lib/query-keys";
import {openWork} from "@/lib/work";
import {formatDateTime,formatUtcOffset} from "@/lib/datetime";
import {riskReasons,type PlanningItem,type TodayPlan} from "@/lib/today-plan";
import {useWorkUpdate} from "@/hooks/use-work";
import {EmptyState} from "@/components/ui/workspace";

export default function TodayView(){
 const [view,setView]=useState<"today"|"upcoming">("today"),[page,setPage]=useState(0);
 const zone=Intl.DateTimeFormat().resolvedOptions().timeZone;
 const query=useQuery({queryKey:keys.work.today(zone,view,page),queryFn:({signal})=>api.get<TodayPlan>("/planning/today?"+new URLSearchParams({zone,view,page:String(page),size:"20"}),signal),staleTime:30_000,refetchInterval:60_000,refetchIntervalInBackground:false});
 const update=useWorkUpdate(),data=query.data;
 useEffect(()=>{if(data&&!data.items.length&&page>0)setPage(Math.max(0,Math.ceil(data.total/data.size)-1));},[data,page]);
 const next=data?.items.filter(item=>!item.task.blocked).slice(0,3)??[];
 const switchView=(value:"today"|"upcoming")=>{setView(value);setPage(0);};
 function card({task,risk,plannedToday}:PlanningItem){
  const level=risk.level==="HIGH"?"Overdue":risk.level==="ATTENTION"?"Needs attention":"On your plan";
  return <article key={task.id} aria-label={task.title} className="task-card">
   <div className="flex flex-wrap items-start justify-between gap-2"><h3 className="min-w-0 flex-1"><button className="text-left font-semibold hover:underline" onClick={()=>openWork(task.id)}>{task.title}</button></h3><span className={"filter-chip "+(risk.level==="HIGH"?"text-destructive":"")}>{level}</span></div>
   <p className="mt-2 text-xs text-muted-foreground">P{task.priority} · {task.status==="IN_PROGRESS"?"In progress":task.status==="SOMEDAY"?"Someday":"To do"}</p>
   <dl className="mt-3 grid gap-2 text-sm">
    <div><dt className="font-medium">Deadline</dt><dd>{task.dueAt?<><time dateTime={task.dueAt}>{formatDateTime(task.dueAt,task.timeZone)}</time> <span className="text-xs text-muted-foreground">{formatUtcOffset(task.dueAt,task.timeZone)}</span></>:"No deadline"}</dd></div>
    {task.startAt&&<div><dt className="font-medium">Planned start{plannedToday?" · today":""}</dt><dd><time dateTime={task.startAt}>{formatDateTime(task.startAt,task.timeZone)}</time> <span className="text-xs text-muted-foreground">{formatUtcOffset(task.startAt,task.timeZone)}</span></dd></div>}
   </dl>
   {!!risk.reasons.length&&<ul aria-label="Deadline and dependency notes" className="mt-3 list-disc space-y-1 pl-4 text-sm text-muted-foreground">{risk.reasons.map(reason=><li key={reason}>{riskReasons[reason]??reason}</li>)}</ul>}
   {task.pageId!=null&&task.pageTitle&&<Link prefetch={false} href={"/pages/"+task.pageId} className="mt-3 inline-block text-sm text-primary">{task.pageTitle}</Link>}
   <div className="mt-4 flex flex-wrap gap-2">
    {task.status!=="IN_PROGRESS"&&<button className="btn" disabled={update.isPending||task.blocked} onClick={()=>update.mutate({id:task.id,patch:{status:"IN_PROGRESS",version:task.version}})}>Start task</button>}
    <button className="btn" disabled={update.isPending||task.blocked} onClick={()=>update.mutate({id:task.id,patch:{status:"DONE",version:task.version}})}>Complete task</button>
    {task.blocked?<Link prefetch={false} href={`/tasks?edit=${task.id}&section=planning`} className="btn">Review dependencies</Link>:<button className="btn" onClick={()=>openWork(task.id)}>View details</button>}
   </div>
  </article>;
 }
 return <section className="workspace wide-workspace">
  <div className="page-heading"><div><p className="eyebrow">One step at a time</p><h1>Today</h1><p className="mt-2 text-sm text-muted-foreground">Overdue deadlines, work planned for today, and tasks already in progress.</p></div><Link prefetch={false} href="/tasks" className="btn">All tasks</Link></div>
  {data&&<p className="mb-4 text-xs text-muted-foreground">Viewing {data.date} · {formatUtcOffset(data.asOf,data.timeZone)}. Deadlines retain their saved timezone.</p>}
  <div className="stats-grid mb-6" aria-label="Today counts">{[["Overdue",data?.counts.overdue],["Due today",data?.counts.dueToday],["Planned today",data?.counts.plannedToday],["In progress",data?.counts.inProgress]].map(([label,count])=><div key={label} className="panel stat"><span className="eyebrow">{label}</span><strong>{count??"…"}</strong></div>)}</div>
  <p className="mb-4 text-xs text-muted-foreground">Counts can overlap. Attention labels explain deadlines and blockers; they are not predictions.</p>
  <div className="mb-5 flex flex-wrap gap-2" role="group" aria-label="Planning view"><button className="filter-chip" aria-pressed={view==="today"} onClick={()=>switchView("today")}>Today&apos;s work</button><button className="filter-chip" aria-pressed={view==="upcoming"} onClick={()=>switchView("upcoming")}>Next seven days</button></div>
  {query.isError?<p role="alert">Could not load your plan. <button className="btn" onClick={()=>void query.refetch()}>Retry</button></p>:query.isPending?<div role="status" aria-label="Loading today's plan" className="skeleton h-48 rounded-xl"/>:data&&<>
   {view==="today"&&page===0&&next.length>0&&<section aria-label="Suggested next" className="panel mb-6 p-5"><h2 className="font-semibold">Suggested next</h2><p className="mt-1 text-sm text-muted-foreground">Up to three unblocked tasks from this page, ordered by deadline urgency and priority.</p><ol className="mt-3 list-decimal space-y-2 pl-5">{next.map(item=><li key={item.task.id}><button className="text-left text-sm text-primary hover:underline" onClick={()=>openWork(item.task.id)}>{item.task.title}</button></li>)}</ol></section>}
   <section aria-label={view==="today"?"Today's tasks":"Upcoming deadlines"}><h2 className="mb-3 font-semibold">{view==="today"?"Today's tasks":"Upcoming deadlines"} <span className="text-sm font-normal text-muted-foreground">({data.total})</span></h2>
    {!data.items.length?<EmptyState title={view==="today"?"A clear day":"No upcoming deadlines"} description={view==="today"?"Nothing is overdue, planned for today, or in progress. Check upcoming deadlines or add a task.":"No unfinished task deadlines fall in the next seven calendar days."}/>:<div className="task-grid">{data.items.map(card)}</div>}
   </section>
   {data.total>data.size&&<nav aria-label="Plan pagination" className="mt-5 flex items-center gap-3"><button className="btn" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><span className="text-sm">Page {page+1} of {Math.ceil(data.total/data.size)}</span><button className="btn" disabled={(page+1)*data.size>=data.total} onClick={()=>setPage(p=>p+1)}>Next</button></nav>}
  </>}
 </section>;
}
