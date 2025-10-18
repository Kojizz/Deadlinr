"use client";
import {keys} from "@/lib/query-keys";
import {useState,useRef} from "react";
import {PlanningPanel} from "./planning-panel";
import {FilesPanel,Subtasks,Reminders} from "./item-extras";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import type {WorkItem,WorkInput} from "@/lib/work";
import {Modal} from "@/components/ui/workspace";
import {WorkForm} from "./work-form";
import {WorkDetails} from "./work-details";
import {usePathname,useRouter} from "next/navigation";

export default function WorkDialogs({initial,id,onClose,editing=true,initialTab="Details"}:{initial?:WorkInput;id?:number;onClose:()=>void;editing?:boolean;initialTab?:"Details"|"Planning"}){
 const [tab,setTab]=useState<string>(initialTab);
 const creationDefault=initial?.kind==="EVENT"?"EVENT":"TASK";
 const [createType,setCreateType]=useState<"TASK"|"EVENT">(creationDefault);
 const item=useQuery({queryKey:keys.work.detail(id??0),queryFn:()=>api.get<WorkItem>("/work-items/"+id),enabled:id!=null});
 const dirtyRef=useRef(false);
 const [createAnother,setCreateAnother]=useState(false);
 const [resetToken,setResetToken]=useState(0);
 const path=usePathname();
 const router=useRouter();
 const isView=id!=null&&editing===false;
 const guardedClose=()=>{if(dirtyRef.current&&!window.confirm("Discard unsaved changes?"))return;dirtyRef.current=false;onClose();};
 return <Modal open onClose={isView?onClose:guardedClose} title={id?(editing?"Edit item":"Item details"):"New "+createType.toLowerCase()} detail={!!id} className={isView?"item-detail-view":""}>
 {!id&&<div className="flex gap-2 mb-5" role="tablist" aria-label="Create type">{(["TASK","EVENT"] as const).map(t=><button className="filter-chip" role="tab" aria-selected={createType===t} key={t} onClick={()=>setCreateType(t)}>{t==="TASK"?"Task":"Event"}</button>)}</div>}
 {id&&editing&&<div className="flex gap-2 mb-5" role="tablist" aria-label="Item sections">{["Details","Subtasks","Files","Reminders","Planning"].map(t=><button className="filter-chip" role="tab" aria-selected={tab===t} key={t} onClick={()=>setTab(t)}>{t}</button>)}</div>}
 {id&&editing? (
   tab==="Planning"&&item.data?<PlanningPanel item={item.data}/>:tab==="Files"?<FilesPanel id={id}/>:tab==="Reminders"?<Reminders id={id}/>:tab==="Subtasks"&&item.data?<Subtasks item={item.data}/>:item.isPending?<div className="skeleton h-48"/>:item.isError?<p role="alert">Could not load item. <button className="btn" onClick={()=>void item.refetch()}>Retry</button></p>:<WorkForm key={id} item={item.data} initial={{...initial,kind:item.data?.kind}} onSaved={()=>{dirtyRef.current=false;onClose();}} onCancel={()=>guardedClose()} dirtyRef={dirtyRef}/>
 ) : !id ? (
   <WorkForm key={createType+resetToken} initial={{...initial,kind:createType}} onSaved={()=>{dirtyRef.current=false;if(createAnother)setResetToken(r=>r+1);else onClose();}} onCancel={onClose} dirtyRef={dirtyRef} createAnother={createAnother} onCreateAnother={setCreateAnother}/>
 ) : item.isPending ? (
   <div className="skeleton h-48"/>
 ) : item.isError ? (
   <p role="alert">Could not load item. <button className="btn" onClick={()=>void item.refetch()}>Retry</button></p>
 ) : item.data ? (
   <WorkDetails item={item.data} onClose={onClose} editLabel={path==="/tasks"?"Edit":"Edit in Tasks"} onEdit={()=>{if(path==="/tasks")router.replace(`/tasks?edit=${id}`);else router.push(`/tasks?edit=${id}`);}}/>
 ) : null }
 </Modal>;
}
