"use client";
import {keys} from "@/lib/query-keys";
import {Suspense,useState,useRef,useEffect} from "react";
import {useSearchParams,useRouter,usePathname} from "next/navigation";
import {useQuery} from "@tanstack/react-query";
import {useVirtualizer} from "@tanstack/react-virtual";
import {useWorkList,useWorkUpdate} from "@/hooks/use-work";
import {openWork,dueLabel,localDate,type WorkItem,type WorkInput} from "@/lib/work";
import {formatDateTime,formatUtcOffset} from "@/lib/datetime";
import {api} from "@/lib/api";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {EmptyState,ConfirmDialog,useToast} from "@/components/ui/workspace";
import {Flag,FileText,Trash2,Paperclip,Bell,CalendarClock} from "lucide-react";
import {AdvancedFilters} from "./advanced-filters";
import {DeadlinePicker} from "./date-picker";
type Props={view?:string;kind?:string;title?:string};
// Shared query-filter builder. The parent prefetches with these exact filters
// so the page's only data fetch is not delayed by a suspended boundary.
// URL is the source of truth for list filters. Reading it synchronously keeps
// the list out of the Suspense boundary useSearchParams() needs on static
// routes; that boundary used to reveal ~300ms after the app shell.
const currentSearch=()=>typeof window==="undefined"?new URLSearchParams():new URLSearchParams(window.location.search);
function UrlSync({onChange}:{onChange:(p:Record<string,string>)=>void}){const search=useSearchParams();useEffect(()=>{onChange(Object.fromEntries(search.entries()));},[search,onChange]);return null;}
function listFilters(p:{view?:string;kind?:string},params:Record<string,string>){const view=p.view??"all";const effectiveView=(params.tab==="overdue"||params.due==="overdue")?"overdue":view;return{view:effectiveView,...(view==="upcoming"?{group:"day",sort:"due"}:{}),...(p.kind?{kind:p.kind}:{}),...params,size:"100"}as Record<string,string>;}
export default function WorkList(props:Props){
 // Kick off the list query in the first render pass: the Suspense boundary
 // below is required for useSearchParams() on static routes, but during
 // hydration React resolves suspended boundaries in a low-priority pass,
 // which used to delay the page's only fetch by ~300ms behind the sidebar.
 const initial=typeof window==="undefined"?{}:Object.fromEntries(new URLSearchParams(window.location.search).entries());
 useWorkList(listFilters(props,initial));
 return <Suspense fallback={<div className="workspace skeleton h-64"/>}><List {...props}/></Suspense>;
}
// Matches the .task-grid container queries in globals.css; the probe is an
// in-flow zero-height grid, so its computed columns always mirror real grids.
function useGridLanes(ref:React.RefObject<HTMLDivElement|null>){
 const [lanes,setLanes]=useState(1);
 useEffect(()=>{const el=ref.current;if(!el)return;
  const read=()=>{const n=getComputedStyle(el).gridTemplateColumns.split(" ").filter(Boolean).length||1;setLanes(p=>p===n?p:n);};
  read();const ro=new ResizeObserver(read);ro.observe(el);return()=>ro.disconnect();},[ref]);
 return lanes;
}
function List({view="all",kind,title="Tasks"}:Props){
 const router=useRouter(),path=usePathname();
 const[params,setParams]=useState(()=>Object.fromEntries(currentSearch().entries()));
 const setMany=(u:Record<string,string>)=>{const p=new URLSearchParams(params);for(const[k,v]of Object.entries(u))v?p.set(k,v):p.delete(k);p.delete("page");setParams(Object.fromEntries(p.entries()));window.history.replaceState(null,"",path+(p.size?"?"+p:""));setSelected(new Set());};const set=(key:string,value:string)=>{const p=new URLSearchParams(params);value?p.set(key,value):p.delete(key);setParams(Object.fromEntries(p.entries()));window.history.replaceState(null,"",path+(p.size?"?"+p:""));setSelected(new Set());};
 const[selected,setSelected]=useState<Set<number>>(new Set()),[confirm,setConfirm]=useState(false),[collapsed,setCollapsed]=useState<Set<string>>(new Set()),[reschedule,setReschedule]=useState<number|null>(null);
 const query=useWorkList(listFilters({view,kind},params)),update=useWorkUpdate(),refresh=useRefreshWorkspace(),toast=useToast();
 const labelsQuery=useQuery({queryKey:keys.labels(),queryFn:()=>api.get<{name:string;color:string}[]>("/labels"),staleTime:60_000});


 const group=params.group??(view==="upcoming"?"day":"none"),items=query.data?.items??[];
 function groupName(w:WorkItem){if(w.hardDeadline&&w.status!=="DONE"&&group==="none")return "Critical";if(group==="status")return w.status;if(group==="priority")return "P"+w.priority;if(group==="day")return w.dueAt?localDate(new Date(w.dueAt)):"No date";if(group==="due"){if(!w.dueAt)return "No date";const date=new Date(w.dueAt);if(date<new Date()&&w.status!=="DONE")return "Overdue";if(dueLabel(w.dueAt)==="Today")return "Today";const end=new Date();end.setDate(end.getDate()+7-((end.getDay()+6)%7));end.setHours(0,0,0,0);return date<end?"This week":"Later";}return "All items";}
 const groups=new Map<string,WorkItem[]>();items.forEach(w=>{const g=groupName(w);groups.set(g,[...(groups.get(g)??[]),w]);});
 const groupCount=(g:string,list:WorkItem[])=>query.data?.groupCounts?.find(c=>c.name===g)?.count??list.length;
 // Above ~100 rows the grid is windowed; rows are chunks of `lanes` cards so
 // the CSS container-query columns stay in charge of the actual layout.
 type VRow={heading:string;count:number}|{cards:WorkItem[]};
 const probe=useRef<HTMLDivElement>(null),scroll=useRef<HTMLDivElement>(null),lanes=useGridLanes(probe);
 const vrows:VRow[]=[];groups.forEach((list,g)=>{vrows.push({heading:g,count:groupCount(g,list)});if(!collapsed.has(g))for(let i=0;i<list.length;i+=lanes)vrows.push({cards:list.slice(i,i+lanes)});});
 const virtualized=vrows.length>100;
 const virtual=useVirtualizer({count:vrows.length,getScrollElement:()=>scroll.current,estimateSize:i=>"heading"in vrows[i]?46:200,overscan:6,enabled:virtualized,getItemKey:i=>lanes+":"+("heading"in vrows[i]?"h"+vrows[i].heading:"c"+vrows[i].cards[0]?.id)});

 async function bulk(patch:WorkInput={},operation="update"){try{const ids=[...selected];await api.post("/work-items/bulk",{ids,patch,operation});setSelected(new Set());setConfirm(false);await refresh();toast({message:operation==="archive"?"Items moved to Trash":"Updated "+ids.length+" items",...(operation==="archive"?{label:"Undo",action:async()=>{await api.post("/work-items/bulk",{ids,operation:"restore"});await refresh();}}:{})});}catch(e){toast({message:String(e)});}}
 const patch=(w:WorkItem,p:WorkInput)=>update.mutate({id:w.id,patch:{...p,version:w.version}});
 function heading(r:{heading:string;count:number}){return <button className="group-heading" aria-expanded={!collapsed.has(r.heading)} onClick={()=>setCollapsed(s=>{const n=new Set(s);n.has(r.heading)?n.delete(r.heading):n.add(r.heading);return n;})}>{collapsed.has(r.heading)?"▸":"▾"} {r.heading} <span>{r.count}</span></button>;}
 function card(w:WorkItem){const over=w.dueAt&&w.status!=="DONE"&&new Date(w.dueAt)<new Date();const colors:Record<number,string>={1:"#ef4444",2:"#f59e0b",3:"#3b82f6",4:"#94a3b8"};return (
  <article key={w.id} data-item-id={w.id} className="task-card" tabIndex={0} aria-label={w.title}
   onClick={e=>{if((e.target as HTMLElement).closest("button,a,input,select,textarea,label"))return;setReschedule(null);openWork(w.id);}}
   onKeyDown={e=>{if(e.target!==e.currentTarget)return;if(/^[1-4]$/.test(e.key))patch(w,{priority:Number(e.key)});if(e.key==="Enter"){e.preventDefault();openWork(w.id);}if(e.key==="e"){e.preventDefault();router.replace(path+"?edit="+w.id);}if(e.key==="d"){e.preventDefault();setReschedule(w.id);}if(e.key==="t"||e.key==="m"){e.preventDefault();const d=new Date();if(e.key==="m")d.setDate(d.getDate()+1);d.setHours(w.allDay?0:18,0,0,0);patch(w,{dueAt:d.toISOString()});}if(e.key==="Delete"){setSelected(new Set([w.id]));setConfirm(true);}}}>
   <div className="flex items-start gap-2">
    <input type="checkbox" aria-label={"Select "+w.title} className="mt-1" checked={selected.has(w.id)} onChange={()=>setSelected(s=>{const n=new Set(s);n.has(w.id)?n.delete(w.id):n.add(w.id);return n;})}/>
    <div className="flex-1 min-w-0">
     <div className="flex items-center gap-2">
      <input type="checkbox" aria-label={(w.status==="DONE"?"Reopen ":"Complete ")+w.title} checked={w.status==="DONE"} onChange={e=>patch(w,{status:e.target.checked?"DONE":"TODO"})}/>
      <button type="button" className="min-w-0 flex-1 text-left" onClick={()=>openWork(w.id)}>
       <strong className={"text-sm block truncate "+(w.status==="DONE"?"line-through text-muted-foreground":"")}>{w.title}</strong>
      </button>
     </div>
     <p className="text-sm text-muted-foreground line-clamp-3 mt-1" style={{display:"-webkit-box",WebkitLineClamp:3,overflow:"hidden",WebkitBoxOrient:"vertical"}}>{w.description?w.description:<span className="italic opacity-70">No description</span>}</p>
     <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
      <span className="filter-chip">{({TODO:"To do",IN_PROGRESS:"In progress",DONE:"Done",SOMEDAY:"Someday"} as Record<WorkItem["status"],string>)[w.status]}</span>
      <span className="inline-flex items-center gap-1 rounded-full border px-2 py-1" style={{borderLeftWidth:4,borderLeftColor:colors[w.priority]??"#94a3b8"}}><Flag size={12} style={{color:colors[w.priority]}}/>{({1:"P1 Urgent",2:"P2 High",3:"P3 Medium",4:"P4 Low"} as Record<number,string>)[w.priority]}</span>
      {w.dueAt?<span className={"filter-chip"+(over?" !border-destructive !text-destructive font-semibold":"")}>{over?"Overdue · ":""}{dueLabel(w.dueAt)} · {formatDateTime(w.dueAt,w.timeZone)} · {formatUtcOffset(w.dueAt,w.timeZone)}</span>:<span className="filter-chip text-muted-foreground">No date</span>}
      {w.pageId!=null&&w.pageTitle?<a href={"/pages/"+w.pageId} className="inline-flex items-center gap-1 rounded-full border px-2 py-1"><FileText size={12} className="text-muted-foreground"/>{w.pageTitle}</a>:null}
      {w.labels.map(l=><span key={l} className="filter-chip">#{l}</span>)}
      {w.reminderMinutes.length>0?<Bell size={14} className="text-muted-foreground"/>:null}
      {w.subtaskCount>0?<span className="inline-flex items-center gap-1"><div className="h-1 w-14 rounded-full bg-muted"><div className="h-1 rounded-full bg-primary" style={{width:Math.min(100,Math.round((w.subtasksDone/w.subtaskCount)*100))+"%"}}/></div>{w.subtasksDone}/{w.subtaskCount}</span>:null}
      {w.fileCount>0?<span className="inline-flex items-center gap-1" title={w.fileCount+" files"}><Paperclip size={14} className="text-muted-foreground"/>{w.fileCount}</span>:null}
     </div>
    </div>
    <div className="relative flex flex-col items-end gap-1">
     <button type="button" className="btn" aria-label={"Edit "+w.title} onClick={()=>router.replace(path+"?edit="+w.id)}>Edit</button>
     <button type="button" aria-label={"Reschedule "+w.title} aria-expanded={reschedule===w.id} className="row-edit text-muted-foreground hover:text-foreground" onClick={()=>setReschedule(reschedule===w.id?null:w.id)}><CalendarClock size={16}/></button>
     <button type="button" aria-label={"Delete "+w.title} className="row-edit text-muted-foreground hover:text-destructive" onClick={async()=>{try{await api.post("/work-items/bulk",{ids:[w.id],operation:"archive"});await refresh();toast({message:w.title+" moved to Trash",label:"Undo",action:async()=>{await api.post("/work-items/bulk",{ids:[w.id],operation:"restore"});await refresh();}});}catch(e){toast({message:String(e)});}}}><Trash2 size={16}/></button>
     {reschedule===w.id&&<div role="menu" aria-label={"Reschedule "+w.title} className="panel absolute right-0 top-full z-50 mt-1 grid min-w-40 gap-1 p-2 shadow-lg" onKeyDown={e=>{if(e.key==="Escape"){e.stopPropagation();setReschedule(null);}}}>
      {([["Today",0],["Tomorrow",1],["Next Monday",((8-new Date().getDay())%7)||7],["In a week",7]] as const).map(([name,offset],idx)=><button type="button" role="menuitem" autoFocus={idx===0} className="btn justify-start" key={name} onClick={()=>{const d=new Date();d.setDate(d.getDate()+offset);const old=w.dueAt?new Date(w.dueAt):null;d.setHours(w.allDay?0:old?.getHours()??18,w.allDay?0:old?.getMinutes()??0,0,0);patch(w,{dueAt:d.toISOString()});setReschedule(null);}}>{name}</button>)}
      <button type="button" role="menuitem" className="btn justify-start text-muted-foreground" onClick={()=>{patch(w,{dueAt:null});setReschedule(null);}}>No date</button>
     </div>}
    </div>
   </div>
  </article>);}
 return <><Suspense fallback={null}><UrlSync onChange={setParams}/></Suspense><section className="workspace wide-workspace"><div className="page-heading"><div><p className="eyebrow">Your workspace</p><h1>{title}</h1><p className="mt-1 text-sm text-muted-foreground">{query.data?.total??"…"} items</p></div></div>
 <div className="flex flex-wrap gap-2 justify-between mb-4"><span className="text-xs text-muted-foreground">Focus a card for keyboard shortcuts</span>{view==="upcoming"&&<select className="row-select" aria-label="Upcoming range" value={params.days??"14"} onChange={e=>set("days",e.target.value)}>{[7,14,30].map(d=><option key={d} value={d}>Next {d} days</option>)}</select>}</div>
 <div className="list-toolbar"><input className="input" aria-label="Search list" list="filter-suggestions" placeholder="Search or p1 & due:today & #ml" value={params.q??""} onChange={e=>set("q",e.target.value)}/><select className="input" aria-label="Group by" value={group} onChange={e=>set("group",e.target.value)}>{["none","status","due","day","priority"].map(g=><option key={g} value={g}>Group: {g}</option>)}</select><select className="input" aria-label="Sort" value={params.sort??"smart"} onChange={e=>set("sort",e.target.value)}>{[["due","Due date"],["priority","Priority"],["status","Status"],["created","Created"],["title","Title"]].map(([v,l])=><option key={v} value={v}>Sort: {l}</option>)}</select><select className="input" aria-label="Sort direction" value={params.dir??"asc"} onChange={e=>set("dir",e.target.value)}><option value="asc">Ascending</option><option value="desc">Descending</option></select></div>
 {view==="today"&&items.some(w=>w.hardDeadline&&w.status!=="DONE")&&<div role="status" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">⚑ Hard deadlines need attention. Review the Critical group before planning more work.</div>}
 <datalist id="filter-suggestions">{["p1","p2","p3","p4","due:today","due:tomorrow","due:overdue","due:none","status:done",...(labelsQuery.data?.map(l=>"#"+l.name)??[])].map(term=><option key={term} value={(params.q?.includes("&")?params.q.slice(0,params.q.lastIndexOf("&")+1)+" ":"")+term}/>)}</datalist>
 <AdvancedFilters params={params} set={set} setMany={setMany} counts={query.data?.priorityCounts}/>
 <div className="flex flex-wrap gap-2 mb-4">{[["","All","all"],["TODO","To do","todo"],["IN_PROGRESS","In progress","progress"],["DONE","Done","done"],["overdue","Overdue","overdue"]].map(([value,label,count])=><button key={label} className="filter-chip" aria-pressed={(params.status??params.tab??"")===value} onClick={()=>{const p=currentSearch();p.delete("status");p.delete("tab");p.delete("page");if(value)p.set(value==="overdue"?"tab":"status",value);router.replace(path+"?"+p,{scroll:false});}}>{label} {query.data?.counts[count as keyof NonNullable<typeof query.data>["counts"]]??0}</button>)}
 {currentSearch().size>0&&<button className="btn" onClick={()=>router.replace(path,{scroll:false})}>Clear filters</button>}</div>
 {!!selected.size&&<div className="bulk-toolbar"><strong>{selected.size} selected</strong><select className="input" aria-label="Bulk status" value="" onChange={e=>void bulk({status:e.target.value as WorkItem["status"]})}><option value="" disabled>Status…</option><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option><option value="SOMEDAY">Someday</option></select><select className="input" aria-label="Bulk priority" value="" onChange={e=>void bulk({priority:Number(e.target.value)})}><option value="" disabled>Priority…</option>{[1,2,3,4].map(p=><option key={p} value={p}>P{p}</option>)}</select><DeadlinePicker label="Bulk due date" value={null} onChange={v=>void bulk({dueAt:v})}/><button className="btn btn-danger" onClick={()=>setConfirm(true)}>Delete selected</button></div>}
 <label className="text-xs flex gap-2 mb-2"><input aria-label="Select all visible" type="checkbox" checked={!!items.length&&selected.size===items.length} onChange={e=>setSelected(new Set(e.target.checked?items.map(w=>w.id):[]))}/>Select this page</label>
 {query.isError?<p role="alert">Could not load items. <button className="btn" onClick={()=>void query.refetch()}>Retry</button></p>:query.isPending?<div className="skeleton h-48 rounded-xl"/>:!items.length?<EmptyState title="A clear space" description="Add an item or adjust your filters."/>:virtualized?<div ref={scroll} className="panel" style={{maxHeight:"70vh",overflow:"auto"}}><div style={{height:virtual.getTotalSize(),position:"relative"}}>{virtual.getVirtualItems().map(v=>{const r=vrows[v.index];return <div key={v.key} data-index={v.index} ref={virtual.measureElement} style={{position:"absolute",top:0,left:0,width:"100%",transform:"translateY("+v.start+"px)"}}>{"heading"in r?heading(r):<div className="task-grid" style={{padding:"6px 12px"}}>{r.cards.map(w=>card(w))}</div>}</div>;})}</div></div>:<div className="grid gap-4">{[...groups].map(([g,list])=><section key={g} className="panel overflow-hidden">{heading({heading:g,count:groupCount(g,list)})}{!collapsed.has(g)&&<div className="task-grid p-3">{list.map(w=>card(w))}</div>}</section>)}</div>}
 <div ref={probe} className="task-grid" aria-hidden style={{height:0,visibility:"hidden"}}/>

 <ConfirmDialog open={confirm} onClose={()=>setConfirm(false)} title="Move selected items to Trash?" description="Selected items and their subtasks will be archived. You can restore them for 30 days." onConfirm={()=>bulk({},"archive")}/></section></>;
}
