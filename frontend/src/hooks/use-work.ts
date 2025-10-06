"use client";
import {useQuery,useMutation,useQueryClient} from "@tanstack/react-query";
import {api} from "@/lib/api";
import type {WorkItem,WorkInput,WorkList} from "@/lib/work";
import {useToast} from "@/components/ui/workspace";import {keys} from "@/lib/query-keys";
import {useRefreshWorkspace} from "./use-workspace";
export function useWorkList(filters:Record<string,string>={}){const params=new URLSearchParams({zone:Intl.DateTimeFormat().resolvedOptions().timeZone,...filters});return useQuery({queryKey:keys.work.list(params.toString()),queryFn:({signal})=>api.get<WorkList>("/work-items?"+params,signal),staleTime:15000});}
export function useWorkUpdate(){
 const cache=useQueryClient(),refresh=useRefreshWorkspace(),toast=useToast();
 return useMutation({mutationFn:({id,patch}:{id:number;patch:WorkInput&{confirmMove?:boolean}})=>api.patch<WorkItem>("/work-items/"+id,patch),
 onMutate:async({id,patch})=>{await cache.cancelQueries({queryKey:["work"]});const before=cache.getQueriesData<WorkList>({queryKey:["work","list"]});before.forEach(([key,value])=>{if(value?.items)cache.setQueryData(key,{...value,items:value.items.map(w=>w.id===id?{...w,...patch}:w)});});return {before};},
 onError:(e,_v,context)=>{context?.before.forEach(([key,data])=>cache.setQueryData(key,data));toast({message:e.message});},
 onSettled:()=>refresh()});
}



