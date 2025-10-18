"use client";
import {useQuery,useQueryClient} from "@tanstack/react-query";
import {api} from "@/lib/api";
import {keys} from "@/lib/query-keys";
export interface Summary {totalPages:number;notes:number;tasks:number;events:number;openTasks:number;overdue:number;dueToday:number;dueThisWeek:number;createdThisWeek:number;trashCount:number;storageUsed:number;}
export function useSummary(){return useQuery({queryKey:keys.workspace.summary(),queryFn:()=>api.get<Summary>("/workspace/summary?zone="+encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone)),staleTime:60000,refetchOnWindowFocus:false});}
export type RefreshScope="work"|"pages"|"pagesCascade";
// Targeted invalidation: work mutations never touch page content caches
// (["pages", id] holds the editor document), and plain page renames/moves
// never refetch work lists. "pagesCascade" is for archive/restore/delete,
// which cascade into work items and reminders server-side.
const SCOPES:Record<RefreshScope,readonly (readonly unknown[])[]>={
 work:[["work"],keys.workspace.summary(),keys.pages.tree(),keys.pages.roots(),["notifications"],["stats"],["search"]],
 pages:[["pages"],keys.workspace.summary(),["search"]],
 pagesCascade:[["pages"],keys.workspace.summary(),["search"],["work"],["notifications"],["stats"]],
};
export function useRefreshWorkspace(){const q=useQueryClient();return(scope:RefreshScope="work")=>Promise.all(SCOPES[scope].map(key=>q.invalidateQueries({queryKey:[...key]})));}
