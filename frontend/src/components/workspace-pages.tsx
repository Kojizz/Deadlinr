"use client";
import {keys} from "@/lib/query-keys";
import {useState} from "react";
import Link from "next/link";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {formatDateTime} from "@/lib/datetime";
import {useSummary,useRefreshWorkspace} from "@/hooks/use-workspace";
import {useCreatePage} from "@/hooks/use-create-page";
import {usePageActions} from "@/components/page-actions";
import {EmptyState,ConfirmDialog,useToast} from "@/components/ui/workspace";
type Entry={id:number;title:string;parentId:number|null;archivedAt?:string;originalLocation:string;archiveBatchId?:string;taskCount?:number;eventCount?:number};
export function WorkspacePages({trash=false}:{trash?:boolean}){
 const [page,setPage]=useState(0),[selected,setSelected]=useState<Set<number>>(new Set()),[confirm,setConfirm]=useState<"empty"|"bulk"|number|null>(null);
 const {data:summary}=useSummary(),refresh=useRefreshWorkspace(),toast=useToast(),create=useCreatePage(),actions=usePageActions();
 const query=useQuery({queryKey:keys.pages.list(trash,page),queryFn:()=>api.get<Entry[]>("/workspace/pages?trash="+trash+"&size=100&page="+page)});
 const items=query.data??[];
 return <section className="workspace"><div className="page-heading"><div><p className="eyebrow">Workspace / {trash?"Recovery":"Notes"}</p><h1>{trash?"Trash":"Pages"}</h1><p className="text-sm text-muted-foreground">{trash?"Restore your pages or permanently remove them. Items are purged after 30 days.":String(summary?.totalPages??"…")+" pages in your workspace"}</p></div>
 {trash?<button className="btn btn-danger" disabled={!summary?.trashCount} onClick={()=>setConfirm("empty")}>Empty trash</button>:<button className="btn btn-primary" onClick={()=>create.mutate()}>New page</button>}</div>
 {!!selected.size&&!trash&&<button className="btn btn-danger mb-4" onClick={()=>setConfirm("bulk")}>Delete {selected.size} selected</button>}
 {query.isError?<div role="alert" className="panel p-6">Could not load pages. <button className="btn" onClick={()=>void query.refetch()}>Retry</button></div>:query.isPending?<div className="skeleton h-36 rounded-xl"/>:!items.length?<EmptyState title={trash?"Trash is empty":"A place for your ideas"} description={trash?"Deleted pages will appear here.":"Create a page for notes, plans, or course work."}/>:<div className="panel long-list">{items.map(p=><div className="list-row" key={p.id}>
 {!trash&&<input aria-label={"Select "+p.title} type="checkbox" checked={selected.has(p.id)} onChange={()=>setSelected(s=>{const n=new Set(s);n.has(p.id)?n.delete(p.id):n.add(p.id);return n;})}/>}
 <div className="min-w-0 flex-1">{trash?<strong className="text-sm">{p.title}{((p.taskCount??0)+(p.eventCount??0))>0?" (+"+(p.taskCount??0)+" tasks"+(((p.eventCount??0)>0)?", "+p.eventCount+" events":"")+")":""}</strong>:<Link className="font-medium text-sm" href={"/pages/"+p.id}>{p.title}</Link>}<p className="text-xs text-muted-foreground">{p.originalLocation}{p.archivedAt?" · Deleted "+formatDateTime(p.archivedAt):""}</p></div>
 {trash?<><button className="btn" onClick={async()=>{await api.post("/workspace/pages/"+p.id+"/restore");await refresh("pagesCascade");toast({message:"Page subtree restored"});}}>Restore</button><button className="btn btn-danger" onClick={()=>setConfirm(p.id)}>Delete forever</button></>:<button className="btn btn-danger" onClick={()=>void actions.remove(p.id)}>Delete</button>}
 </div>)}</div>}
 <div className="mt-4 flex justify-end gap-2"><button className="btn" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><button className="btn" disabled={items.length<100} onClick={()=>setPage(p=>p+1)}>Next</button></div>
 {actions.dialog}<ConfirmDialog open={confirm!==null} onClose={()=>setConfirm(null)} title={confirm==="bulk"?"Move selected pages to Trash?":"Permanently delete?"}
 description={confirm==="bulk"?"Selected pages and all their subpages will move to Trash.":"This cannot be undone. Associated reminders, files and linked tasks/events will also be removed."}
 onConfirm={async()=>{if(confirm==="empty")await api.del("/workspace/trash");else if(confirm==="bulk"){const ids=[...selected];for(const id of ids)await api.post("/workspace/pages/"+id+"/archive");setSelected(new Set());toast({message:"Selected pages moved to Trash",action:async()=>{for(const id of ids)await api.post("/workspace/pages/"+id+"/restore");await refresh("pagesCascade");}});}else await api.del("/workspace/pages/"+confirm);await refresh("pagesCascade");}}/></section>;
}





