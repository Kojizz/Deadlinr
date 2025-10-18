"use client";
import {useState,useEffect,useRef} from "react";
import dynamic from "next/dynamic";
import {useRouter,usePathname,useSearchParams} from "next/navigation";
import {Suspense} from "react";
import type {WorkInput} from "@/lib/work";
const Dialogs=dynamic(()=>import("./planner/work-dialogs"),{ssr:false});
const QuickAdd=dynamic(()=>import("./planner/quick-add"),{ssr:false});
const Notifications=dynamic(()=>import("./planner/notification-center"),{ssr:false});
function Host(){
 const [quick,setQuick]=useState(false);
 const quickReturnFocus=useRef<HTMLElement|null>(null);
 function closeQuick(){setQuick(false);requestAnimationFrame(()=>quickReturnFocus.current?.focus());}
 const [create,setCreate]=useState<WorkInput|null>(null),[notifications,setNotifications]=useState(false);
 const router=useRouter(),path=usePathname(),search=useSearchParams();
 const taskId=search.get("task");
 const editId=search.get("edit");
 function openTask(id:number){const p=new URLSearchParams(search.toString());p.delete("edit");p.set("task",String(id));router.push(path+"?"+p);}
 function closeTask(){const p=new URLSearchParams(search.toString());p.delete("task");p.delete("edit");router.replace(path+(p.size?"?"+p:""));}
 function closeEdit(){const p=new URLSearchParams(search.toString());p.delete("edit");p.delete("section");router.replace(path+(p.size?"?"+p:""));}
 useEffect(()=>{const newItem=(e:Event)=>{setCreate((e as CustomEvent<WorkInput>).detail??{kind:"TASK"});};
 const detail=(e:Event)=>{const id=(e as CustomEvent<{id:number}>).detail.id;const p=new URLSearchParams(window.location.search);p.delete("edit");p.set("task",String(id));router.push(path+"?"+p);};
 const notice=()=>setNotifications(true);
 const quickAdd=()=>{quickReturnFocus.current=document.activeElement instanceof HTMLElement?document.activeElement:null;setQuick(true);};
 const key=(e:KeyboardEvent)=>{
  if(e.defaultPrevented||e.repeat||e.isComposing||document.querySelector('dialog[open],[role="dialog"][data-state="open"]'))return;
  const command=(e.ctrlKey||e.metaKey)&&!e.altKey;
  if(command&&((!e.shiftKey&&e.key.toLowerCase()==="k")||(e.shiftKey&&e.key.toLowerCase()==="a"))){e.preventDefault();quickAdd();return;}
  const el=e.target as HTMLElement;
  if(el?.closest('input,textarea,select,[role="textbox"],[role="combobox"]')||el?.isContentEditable||e.ctrlKey||e.metaKey||e.altKey||e.shiftKey)return;
  if(e.key==="q"){e.preventDefault();quickAdd();}
  if(e.key==="n"){e.preventDefault();setCreate({kind:"TASK"});}
 };
 window.addEventListener("deadlinr:quick-add",quickAdd);window.addEventListener("deadlinr:create",newItem);window.addEventListener("deadlinr:detail",detail);window.addEventListener("deadlinr:notifications",notice);window.addEventListener("keydown",key);
 return()=>{window.removeEventListener("deadlinr:quick-add",quickAdd);window.removeEventListener("deadlinr:create",newItem);window.removeEventListener("deadlinr:detail",detail);window.removeEventListener("deadlinr:notifications",notice);window.removeEventListener("keydown",key);};},[router,path,search]);
 return <>{quick&&<QuickAdd onClose={closeQuick}/>} {create&&<Dialogs initial={create} onClose={()=>{setCreate(null);}}/>}{editId&&path==="/tasks"&&<Dialogs key={"edit"+editId} id={Number(editId)} editing initialTab={search.get("section")==="planning"?"Planning":"Details"} onClose={closeEdit}/>}{taskId&&!editId&&<Dialogs key={taskId} id={Number(taskId)} editing={false} onClose={closeTask}/>}{notifications&&<Notifications onClose={()=>setNotifications(false)}/>}</>;
}
export function PlannerHost(){return <Suspense>{<Host/>}</Suspense>;}



