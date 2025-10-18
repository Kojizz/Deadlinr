"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import {CalendarDays,CheckSquare,FileText,Trash2,LayoutDashboard,LayoutGrid,BarChart3,Settings,Sun} from "lucide-react";
import {useSummary} from "@/hooks/use-workspace";
export const requestCreate=(kind="TASK")=>window.dispatchEvent(new CustomEvent("deadlinr:create",{detail:{kind}}));
export function WorkspaceNav({mobile=false}:{mobile?:boolean}){
 const path=usePathname(),{data:s}=useSummary();
 const entries=mobile?[["Today","/today",Sun,undefined],["Tasks","/tasks",CheckSquare,s?.openTasks],["Calendar","/calendar",CalendarDays,undefined],["Pages","/pages",FileText,undefined]] as const:
 [["Overview","/dashboard",LayoutDashboard,undefined],["Today","/today",Sun,undefined],["Tasks","/tasks",CheckSquare,s?.openTasks],["Calendar","/calendar",CalendarDays,undefined],["Pages","/pages",FileText,undefined],["Matrix","/matrix",LayoutGrid,undefined],["Statistics","/stats",BarChart3,undefined],["Trash","/trash",Trash2,s?.trashCount],["Settings","/settings",Settings,undefined]] as const;
 return <nav aria-label={mobile?"Mobile navigation":"Workspace"} className={mobile?"mobile-tabs":"grid gap-1 p-2"}>{entries.map(([name,url,Icon,count])=><Link prefetch={false} key={name} href={url} className={(mobile?"":"flex items-center gap-3 rounded-md px-3 py-2 text-sm ")+(path===url?"bg-accent text-foreground font-medium":"text-muted-foreground")}><Icon size={mobile?20:16}/><span className="flex-1">{name}</span>{!mobile&&count!==undefined&&<span className="text-xs tabular-nums">{count}</span>}</Link>)}</nav>;
}






