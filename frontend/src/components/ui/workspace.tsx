"use client";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { Circle, CheckCircle2, Clock3, Flag, Inbox, X } from "lucide-react";

export function StatusPill({status}:{status:string}) {
 const Icon=status==="DONE"?CheckCircle2:status==="IN_PROGRESS"?Clock3:Circle;
 return <span className="pill" style={{color:status==="DONE"?"hsl(var(--status-done))":"hsl(var(--status-progress))"}}><Icon size={12}/>{status.replaceAll("_"," ").toLowerCase()}</span>;
}
export function PriorityPill({priority=3}:{priority?:number}) {
 return <span className="pill" style={{color:"hsl(var(--priority-"+priority+"))"}}><Flag size={12}/>P{priority} {["","Urgent","High","Medium","Low"][priority]}</span>;
}
export function EmptyState({title,description,children}:{title:string;description?:string;children?:React.ReactNode}) {
 return <div className="empty-state"><Inbox size={28}/><h2 className="font-medium text-foreground">{title}</h2>{description&&<p className="max-w-sm text-sm">{description}</p>}{children}</div>;
}
export function Modal({open,onClose,title,children,detail=false,className=""}:{open:boolean;onClose:()=>void;title:string;children:React.ReactNode;detail?:boolean;className?:string}) {
 const ref=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const d=ref.current;if(open&&!d?.open)d?.showModal();if(!open&&d?.open)d.close();},[open]);
 return <dialog ref={ref} className={"modal "+(detail?"detail ":"")+className} aria-label={title} onCancel={e=>{e.preventDefault();onClose();}} onClick={e=>{if(e.target===e.currentTarget){const b=e.currentTarget.getBoundingClientRect();if(e.clientX<b.left||e.clientX>b.right||e.clientY<b.top||e.clientY>b.bottom)onClose();}}}>
  <div className="modal-header mb-6 flex items-center justify-between gap-4"><h2 className="text-lg font-semibold">{title}</h2><button className="btn" aria-label="Close dialog" onClick={onClose}><X size={16}/></button></div>{open&&children}
 </dialog>;
}
type Toast={message:string;action?:()=>void|Promise<void>;label?:string;duration?:number};
const ToastContext=createContext<(t:Toast)=>void>(()=>{});
export const useToast=()=>useContext(ToastContext);
export function ToastProvider({children}:{children:React.ReactNode}){
 const [toast,setToast]=useState<Toast|null>(null);const [busy,setBusy]=useState(false);
 useEffect(()=>{if(!toast)return;const timer=setTimeout(()=>setToast(null),toast.duration??10000);return()=>clearTimeout(timer);},[toast]);
 return <ToastContext.Provider value={setToast}>{children}{toast&&<div className="toast" role="status"><span>{toast.message}</span>{toast.action&&<button disabled={busy} className="font-semibold underline" onClick={async()=>{setBusy(true);try{await toast.action?.();setToast(null);}catch(e){setToast({message:e instanceof Error?e.message:"Action failed"});}finally{setBusy(false);}}}>{toast.label??"Undo"}</button>}<button aria-label="Dismiss notification" onClick={()=>setToast(null)}><X size={16}/></button></div>}</ToastContext.Provider>;
}
export function ConfirmDialog({open,title,description,onClose,onConfirm}:{open:boolean;title:string;description:string;onClose:()=>void;onConfirm:()=>Promise<void>}){
 const [busy,setBusy]=useState(false);const [error,setError]=useState("");
 return <Modal open={open} onClose={()=>{if(!busy)onClose();}} title={title}><p className="mb-6 text-sm text-muted-foreground">{description}</p>{error&&<p role="alert" className="mb-4 text-destructive">{error}</p>}<div className="flex justify-end gap-2"><button className="btn" disabled={busy} onClick={onClose}>Cancel</button><button className="btn btn-danger" disabled={busy} onClick={async()=>{setBusy(true);setError("");try{await onConfirm();onClose();}catch(e){setError(e instanceof Error?e.message:"Operation failed");}finally{setBusy(false);}}}>{busy?"Working…":"Confirm"}</button></div></Modal>;
}
