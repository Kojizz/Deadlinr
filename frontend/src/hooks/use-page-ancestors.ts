"use client";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {keys} from "@/lib/query-keys";
import type {components} from "@/lib/api.d";

export type PageAncestor=components["schemas"]["PageAncestor"];
export function usePageAncestors(id:number|null){
 return useQuery({queryKey:keys.pages.ancestors(id),queryFn:({signal})=>api.get<PageAncestor[]>(`/pages/${id}/ancestors`,signal),enabled:id!=null&&Number.isFinite(id),staleTime:60_000});
}
