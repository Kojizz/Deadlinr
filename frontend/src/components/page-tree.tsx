"use client";
import {keys} from "@/lib/query-keys";
import {useEffect,useMemo,useRef,useState} from "react";
import {usePathname,useRouter} from "next/navigation";
import {useInfiniteQuery,useQuery,useQueryClient} from "@tanstack/react-query";
import {useVirtualizer} from "@tanstack/react-virtual";
import {ChevronRight,FileText,Plus,Star,MoreHorizontal} from "lucide-react";
import {api} from "@/lib/api";
import type {PageTreeNode} from "@/lib/types";
import {useCreatePage} from "@/hooks/use-create-page";
import {useSummary,useRefreshWorkspace} from "@/hooks/use-workspace";
import {usePageAncestors} from "@/hooks/use-page-ancestors";
import {usePageActions} from "@/components/page-actions";
import {useToast} from "@/components/ui/workspace";
type Node=PageTreeNode&{childCount?:number;kind?:string;hasDueDate?:boolean};
type Row=Node&{depth:number};
export function PageTree(){
 const {data:summary}=useSummary();const big=(summary?.totalPages??0)>200;
 const router=useRouter(),path=usePathname(),cache=useQueryClient(),toast=useToast(),refresh=useRefreshWorkspace(),create=useCreatePage();
 const [closed,setClosed]=useState<Set<number>>(new Set()),[loaded,setLoaded]=useState<Record<number,Node[]>>({});
 const [menu,setMenu]=useState<Row|null>(null),[selected,setSelected]=useState<number|null>(null);
 const actions=usePageActions(),scroll=useRef<HTMLDivElement>(null),drag=useRef<number|null>(null);
 const tree=useQuery({queryKey:keys.pages.tree(),queryFn:()=>api.get<Node[]>("/pages/tree"),enabled:!!summary&&!big,staleTime:60_000});
 const roots=useInfiniteQuery({queryKey:keys.pages.roots(),initialPageParam:0,queryFn:({pageParam})=>api.get<Node[]>("/workspace/children?size=100&page="+pageParam),getNextPageParam:(last,all)=>last.length===100?all.length:undefined,enabled:big,staleTime:60_000});
 useEffect(()=>{setLoaded({});},[summary?.totalPages,summary?.trashCount]);
 async function expand(n:Node){
  setClosed(s=>{const next=new Set(s);if(next.has(n.id))next.delete(n.id);else next.add(n.id);return next;});
  if(big&&!loaded[n.id]){try{const children:Node[]=[];for(let p=0;;p++){const batch=await api.get<Node[]>("/workspace/children?parentId="+n.id+"&size=200&page="+p);children.push(...batch);if(batch.length<200)break;}setLoaded(m=>({...m,[n.id]:children}));setClosed(s=>{const nset=new Set(s);nset.delete(n.id);return nset;});}catch(e){toast({message:String(e)});}}
 }
 const activeId=Number(path.split("/")[2]);
 const ancestors=usePageAncestors(path.startsWith("/pages/")&&Number.isFinite(activeId)?activeId:null).data;
 useEffect(()=>{
  if(!ancestors)return;
  let cancelled=false;
  const parentIds=ancestors.slice(0,-1).map(n=>n.id);
  setClosed(s=>{const next=new Set(s);parentIds.forEach(id=>next.delete(id));return next;});
  void(async()=>{
   if(big)await Promise.all(parentIds.map(async id=>{
    const children=await api.get<Node[]>("/workspace/children?parentId="+id+"&size=200");
    if(!cancelled)setLoaded(m=>({...m,[id]:children}));
   }));
  })().catch(()=>{});
  return()=>{cancelled=true;};
 },[ancestors,big]);
 const rows=useMemo(()=>{
  const walk=(nodes:Node[],depth=0):Row[]=>nodes.flatMap(n=>[{...n,depth},...(!closed.has(n.id)?walk(big?loaded[n.id]??[]:n.children??[],depth+1):[])]);
  return walk(big?roots.data?.pages.flat()??[]:tree.data??[]);
 },[tree.data,roots.data,closed,loaded,big]);
 // Keep the active page row in view: creating a page deep in a long tree
 // (or reloading it) must scroll the sidebar so the row is actually visible.
 useEffect(()=>{
  if(!Number.isFinite(activeId))return;
  const el=scroll.current?.querySelector(`[data-page-id="${activeId}"]`);
  if(el instanceof HTMLElement)el.scrollIntoView({block:"nearest"});
 },[activeId,rows]);
 const virtual=useVirtualizer({count:rows.length,getScrollElement:()=>scroll.current,estimateSize:()=>40,overscan:6,enabled:rows.length>200});
 const renderRow=(n:Row)=> <div key={n.id} data-page-id={n.id} role="treeitem" aria-selected={activeId===n.id} aria-level={n.depth+1} aria-busy={n.id<0||undefined} tabIndex={0}
  onFocus={()=>setSelected(n.id)} onKeyDown={e=>{if(e.key==="Delete"){e.preventDefault();void actions.remove(n.id);}if(e.key==="Enter"){router.push("/pages/"+n.id);}if(e.key==="ArrowRight")void expand(n);}}
  onContextMenu={e=>{e.preventDefault();setMenu(n);}} draggable={n.id>0} onDragStart={()=>{drag.current=n.id;}} onDragOver={e=>e.preventDefault()} onDrop={async e=>{e.preventDefault();if(!drag.current||drag.current===n.id)return;try{await api.patch("/pages/"+drag.current+"/move",{parentId:e.altKey?n.id:n.parentId,position:n.position+.5});await refresh("pages");}catch(error){toast({message:String(error)});}}}
  className={"list-row group cursor-pointer rounded-md text-sm "+(activeId===n.id?"bg-accent":"")} style={{paddingLeft:8+n.depth*14,minHeight:"var(--row-height)"}}>
  {(n.childCount??n.children?.length??0)>0?<button aria-label={closed.has(n.id)?"Expand page":"Collapse page"} onClick={()=>void expand(n)}><ChevronRight size={14} className={!closed.has(n.id)&&(!big||loaded[n.id])?"rotate-90":""}/></button>:<FileText size={14} className="text-muted-foreground"/>}
  <button className="min-w-0 flex-1 truncate text-left" onMouseEnter={()=>void cache.prefetchQuery({queryKey:keys.pages.one(n.id),queryFn:()=>api.get("/pages/"+n.id),staleTime:60_000})} onClick={()=>router.push("/pages/"+n.id)}>{n.icon} {n.title||"Untitled"}</button>
  {(n.openTasks??0)>0&&<span className="text-[10px] leading-none text-muted-foreground" title={n.openTasks+" open tasks"}>{n.openTasks}</span>}
  <button aria-label="Add child page" onClick={()=>create.mutate(n.id)} className="opacity-0 group-hover:opacity-100 group-focus-within:opacity-100"><Plus size={14}/></button>
  <button aria-label="Toggle favorite" onClick={async()=>{await api.patch("/pages/"+n.id,{favorite:!n.favorite});await refresh("pages");}} className={n.favorite?"text-primary":"opacity-0 group-hover:opacity-100 group-focus-within:opacity-100"}><Star size={14}/></button>
  <button aria-label={"Actions for "+n.title} onClick={()=>setMenu(n)}><MoreHorizontal size={14}/></button>
 </div>;
 return <div className="px-2"><div className="eyebrow px-2 py-3">Pages</div>{(tree.isError||roots.isError)&&<button role="alert" className="btn" onClick={()=>void refresh("pages")}>Could not load pages. Retry</button>}
 <div ref={scroll} role="tree" aria-label="Pages" style={{maxHeight:"55vh",overflow:"auto"}}>
 {rows.length>200?<div style={{height:virtual.getTotalSize(),position:"relative"}}>{virtual.getVirtualItems().map(v=><div key={v.key} ref={virtual.measureElement} data-index={v.index} style={{position:"absolute",top:0,left:0,width:"100%",transform:"translateY("+v.start+"px)"}}>{renderRow(rows[v.index])}</div>)}</div>:rows.map(renderRow)}
 </div>{!rows.length&&!tree.isPending&&!roots.isFetching&&<p className="p-2 text-xs text-muted-foreground">No pages yet</p>}
 {big&&roots.hasNextPage&&<button className="btn my-2 w-full" onClick={()=>void roots.fetchNextPage()}>Load more pages</button>}
 {menu&&<div className="fixed inset-0 z-50" onClick={()=>setMenu(null)} onKeyDown={e=>{if(e.key==="Escape")setMenu(null);}}>
 <div role="menu" aria-label="Page actions" className="panel absolute left-8 top-24 grid min-w-48 p-2" onClick={e=>e.stopPropagation()}>
 <button role="menuitem" autoFocus className="btn" onClick={()=>{create.mutate(menu.id);setMenu(null);}}>Add subpage</button>
 <button role="menuitem" className="btn" onClick={async()=>{await api.patch("/pages/"+menu.id+"/move",{parentId:menu.parentId,position:menu.position-1.5});await refresh("pages");setMenu(null);}}>Move up</button>
 <button role="menuitem" className="btn" onClick={async()=>{await api.patch("/pages/"+menu.id+"/move",{parentId:null,position:menu.position});await refresh("pages");setMenu(null);}}>Move to root</button>
 <button role="menuitem" className="btn btn-danger" onClick={()=>{void actions.remove(menu.id);setMenu(null);}}>Delete page</button>
 </div></div>}{actions.dialog}<span className="sr-only">{selected?"Selected page "+selected:""}</span></div>;
}

