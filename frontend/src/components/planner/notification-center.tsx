"use client";
import {keys} from "@/lib/query-keys";
import {useState} from "react";
import {useQuery,useQueryClient} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {formatDateTime} from "@/lib/datetime";
import {Modal,EmptyState} from "@/components/ui/workspace";
import {openWork} from "@/lib/work";
interface Notification{id:number;itemId:number|null;title:string;body:string;readAt:string|null;createdAt:string}
export default function NotificationCenter({onClose}:{onClose:()=>void}){
 const [page,setPage]=useState(0);const q=useQueryClient();
 const query=useQuery({queryKey:keys.notifications.list(page),queryFn:()=>api.get<Notification[]>("/notifications?page="+page),refetchInterval:15000});
 return <Modal open title="Notifications" onClose={onClose} detail>{query.isError?<p role="alert">Could not load notifications.</p>:query.isPending?<div className="skeleton h-32"/>:!query.data?.length?<EmptyState title="You’re all caught up" description="Deadline reminders and your daily plan appear here."/>:query.data.map(n=><div className="list-row" key={n.id}><button className="min-w-0 flex-1 text-left" onClick={async()=>{await api.post("/notifications/"+n.id+"/read");await q.invalidateQueries({queryKey:["notifications"]});if(n.itemId){onClose();openWork(n.itemId);}}}><strong className={"text-sm "+(!n.readAt?"text-primary":"")}>{!n.readAt?"● ":""}{n.title}</strong><p className="text-sm text-muted-foreground">{n.body}</p><time className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</time></button></div>)}<div className="mt-4 flex justify-between"><button className="btn" disabled={!page} onClick={()=>setPage(p=>p-1)}>Previous</button><button className="btn" disabled={(query.data?.length??0)<50} onClick={()=>setPage(p=>p+1)}>Next</button></div></Modal>;
}





