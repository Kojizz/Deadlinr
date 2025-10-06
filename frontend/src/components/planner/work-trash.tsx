"use client";
import {useState} from "react";
import {useWorkList} from "@/hooks/use-work";
import {api} from "@/lib/api";
import {formatDateTime} from "@/lib/datetime";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {ConfirmDialog,useToast} from "@/components/ui/workspace";
export function WorkTrash(){const[page,setPage]=useState(0),[remove,setRemove]=useState<number|null>(null);const query=useWorkList({view:"trash",page:String(page)}),refresh=useRefreshWorkspace(),toast=useToast();return <section className="workspace pt-0"><h2 className="text-lg font-semibold mb-3">Deleted tasks and events · {query.data?.total??0}</h2>{query.data?.items.map(w=><div className="list-row panel" key={w.id}><div className="flex-1"><strong className="text-sm">{w.title}</strong><p className="text-xs text-muted-foreground">{w.kind} · Deleted {w.archivedAt&&formatDateTime(w.archivedAt)}{w.pageId?" · Page #"+w.pageId:""}</p></div><button className="btn" onClick={async()=>{try{await api.post("/work-items/"+w.id+"/restore");await refresh();}catch(e){toast({message:String(e)});}}}>Restore item</button><button className="btn btn-danger" onClick={()=>setRemove(w.id)}>Delete item forever</button></div>)}<div className="flex justify-end gap-2 mt-3"><button className="btn" disabled={!page} onClick={()=>setPage(p=>p-1)}>Previous items</button><button className="btn" disabled={(page+1)*100>=(query.data?.total??0)} onClick={()=>setPage(p=>p+1)}>Next items</button></div><ConfirmDialog open={!!remove} title="Delete item forever?" description="The item, its subtasks and attachments will be permanently deleted." onClose={()=>setRemove(null)} onConfirm={async()=>{await api.del("/work-items/"+remove+"/permanent");await refresh();}}/></section>;}




