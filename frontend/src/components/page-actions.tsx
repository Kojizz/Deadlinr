"use client";
import {createWork} from "@/lib/work";
import {useState} from "react";
import {useRouter,usePathname} from "next/navigation";
import {api} from "@/lib/api";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {ConfirmDialog,useToast} from "@/components/ui/workspace";
export function usePageActions(){
 const [pending,setPending]=useState<{id:number;count:number;tasks:number;events:number}|null>(null);
 const toast=useToast(),refresh=useRefreshWorkspace(),router=useRouter(),path=usePathname();
 async function remove(id:number){try{const impact=await api.get<{affected:number;tasks:number;events:number}>("/workspace/pages/"+id+"/impact");setPending({id,count:impact.affected,tasks:impact.tasks,events:impact.events});}catch(e){toast({message:e instanceof Error?e.message:"Could not delete page"});}}
 const dialog=<ConfirmDialog open={!!pending} title="Move to Trash?" description={pending?"This will delete "+pending.count+" page"+(pending.count===1?"":"s")+" including "+(pending.count-1)+" subpages"+((pending.tasks+pending.events)>0?" and "+(pending.tasks+pending.events)+" linked item"+((pending.tasks+pending.events)===1?"":"s")+" ("+pending.tasks+" tasks, "+pending.events+" events)":"")+". You can restore them from Trash.":""}
 onClose={()=>setPending(null)} onConfirm={async()=>{if(!pending)return;const id=pending.id;await api.post("/workspace/pages/"+id+"/archive");await refresh("pagesCascade");if(path.startsWith("/pages/"))router.push("/pages");toast({message:pending.count+" pages moved to Trash",duration:10000,action:async()=>{await api.post("/workspace/pages/"+id+"/restore");await refresh("pagesCascade");}});}}/>;
 return {remove,dialog};
}
export function PageHeaderActions({id}:{id:number}){const {remove,dialog}=usePageActions();return <div className="flex justify-end px-6 pt-4"><button className="btn mr-2" onClick={()=>createWork({kind:"TASK",pageId:id})}>Add task or event</button><button className="btn btn-danger" onClick={()=>void remove(id)}>Delete page</button>{dialog}</div>;}
