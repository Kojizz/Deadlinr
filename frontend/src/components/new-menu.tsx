"use client";
import {useRef} from "react";
import {Plus,ChevronDown} from "lucide-react";
import {useCreatePage} from "@/hooks/use-create-page";
import {createWork,type WorkKind} from "@/lib/work";
export function NewMenu(){
 const ref=useRef<HTMLDetailsElement>(null),page=useCreatePage();
 return <div className="relative flex shrink-0"><button aria-label="New" className="btn btn-primary rounded-r-none !p-1.5 sm:!px-3 sm:!py-2" onClick={()=>createWork({kind:"TASK"})}><Plus size={16} aria-hidden="true"/><span className="hidden sm:inline">New</span></button>
 <details ref={ref}><summary aria-label="Create options" className="btn btn-primary h-full list-none rounded-l-none border-l border-white/20 px-2"><ChevronDown size={14}/></summary>
 <div className="panel absolute right-0 top-full z-40 mt-2 grid min-w-44 p-2">{["Page","Task","Event"].map(kind=><button className="btn justify-start border-0" key={kind} onClick={()=>{if(ref.current)ref.current.open=false;if(kind==="Page")page.mutate();else createWork({kind:kind.toUpperCase() as WorkKind});}}>{kind}</button>)}</div></details></div>;
}


