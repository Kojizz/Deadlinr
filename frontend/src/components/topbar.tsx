"use client";
import {useEffect,useState} from "react";
import {usePathname} from "next/navigation";
import {Bell,Menu,Search,Zap} from "lucide-react";
import {AppSidebar} from "@/components/app-sidebar";
import {Breadcrumbs} from "@/components/breadcrumbs";
import {openPalette} from "@/lib/palette-events";
import {ThemeToggle} from "@/components/theme-toggle";
import {UserMenuHost} from "@/components/user-menu-host";
import {NewMenu} from "@/components/new-menu";
import {HistoryControls} from "@/components/planner/history-controls";
import {Modal} from "@/components/ui/workspace";
export function Topbar(){const[navigationOpen,setNavigationOpen]=useState(false),pathname=usePathname();useEffect(()=>setNavigationOpen(false),[pathname]);return <header className="z-sticky flex h-12 shrink-0 items-center gap-1 border-b bg-background px-2 sm:gap-2 sm:px-3">
 <button className="btn !p-1.5 md:hidden" aria-label="Open menu" onClick={()=>setNavigationOpen(true)}><Menu size={18}/></button><Modal open={navigationOpen} onClose={()=>setNavigationOpen(false)} title="Navigation"><div className="h-[65vh]"><AppSidebar className="!w-full border-0"/></div></Modal>
 <div className="hidden min-w-0 sm:block"><Breadcrumbs/></div><div className="flex-1"/><button type="button" className="btn !p-1.5 sm:!px-3 sm:!py-2" aria-label="Quick add" aria-keyshortcuts="Control+k Meta+k q" title="Quick add (Cmd/Ctrl+K or q)" onClick={()=>window.dispatchEvent(new Event("deadlinr:quick-add"))}><Zap size={18} className="sm:hidden" aria-hidden="true"/><span className="hidden sm:inline">Quick add</span></button><button type="button" onClick={()=>openPalette()} className="hidden h-8 w-52 lg:w-64 items-center gap-2 rounded-md border bg-transparent px-3 text-sm text-muted-foreground transition-colors hover:bg-accent md:flex" aria-label="Search"><Search size={16}/><span className="flex-1 text-left">Search…</span><kbd className="rounded border bg-muted px-1 text-[10px]">Ctrl Shift K</kbd></button><button className="btn !p-1.5 md:hidden" aria-label="Search" onClick={()=>openPalette()}><Search size={18}/></button>
 <button className="btn !p-1.5" aria-label="Notifications" onClick={()=>window.dispatchEvent(new Event("deadlinr:notifications"))}><Bell size={18}/></button><NewMenu/><div className="history-header"><HistoryControls/></div><ThemeToggle/><UserMenuHost/></header>;}
