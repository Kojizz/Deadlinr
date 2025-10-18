"use client";
import Link from "next/link";
import {useSummary} from "@/hooks/use-workspace";
import {useAuth} from "@/hooks/use-auth";
export default function Dashboard(){
 const {data:s,isError,refetch}=useSummary();const {user}=useAuth();
 const start=new Date();start.setHours(0,0,0,0);const end=new Date(start);end.setDate(end.getDate()+1);const range="from="+encodeURIComponent(start.toISOString())+"&to="+encodeURIComponent(end.toISOString());const cards=[["Pages",s?.totalPages,"/pages"],["Open tasks",s?.openTasks,"/tasks"],["Due today",s?.dueToday,"/tasks?"+range],["Overdue",s?.overdue,"/tasks?due=overdue"]] as const;
 return <section className="workspace"><div className="page-heading"><div><p className="eyebrow">{new Date().toLocaleDateString("en-US",{weekday:"long",month:"long",day:"numeric"})}</p><h1>Welcome back, {user?.fullName?.split(" ")[0]}</h1><p className="mt-2 text-sm text-muted-foreground">A little clarity for everything ahead.</p></div><Link prefetch={false} href="/today" className="btn btn-primary">Plan today</Link></div>
 {isError?<p role="alert">Could not load workspace counts. <button className="btn" onClick={()=>void refetch()}>Retry</button></p>:<div className="stats-grid">{cards.map(([name,count,url])=><Link prefetch={false} key={name} href={url} className="panel stat"><span className="eyebrow">{name}</span><strong data-testid={"count-"+name.toLowerCase().replaceAll(" ","-")}>{count??"…"}</strong><span className="text-xs text-muted-foreground">View {name.toLowerCase()} →</span></Link>)}</div>}
 <div className="mt-8 grid gap-6 md:grid-cols-2"><div className="panel p-6"><h2 className="mb-4 font-semibold">This week</h2><p className="mb-3 text-sm">{s?.dueThisWeek??"…"} deadlines · {s?.createdThisWeek??"…"} items created</p><Link prefetch={false} className="text-sm text-primary" href="/calendar">Open calendar →</Link></div><div className="panel p-6"><h2 className="mb-4 font-semibold">Workspace</h2><p className="text-sm text-muted-foreground">{s?.notes??0} notes · {s?.tasks??0} tasks · {s?.events??0} events</p><p className="mt-3 text-xs text-muted-foreground">{((s?.storageUsed??0)/1048576).toFixed(1)} MB files · {s?.trashCount??0} items in Trash</p></div></div></section>;
}


