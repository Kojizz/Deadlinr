"use client";
import {DateRangeFilter} from "./date-range-filter";
export function AdvancedFilters({params,set,counts,setMany}:{params:Record<string,string>;set:(k:string,v:string)=>void;counts?:Record<string,number>;setMany:(u:Record<string,string>)=>void}){
 const priorities=(params.priorities??"").split(",");
 return <div className="flex flex-wrap items-center gap-3 mb-4 text-xs">{[1,2,3,4].map(p=><label key={p} className="filter-chip"><input type="checkbox" aria-label={"Filter P"+p} checked={priorities.includes(String(p))} onChange={e=>set("priorities",(e.target.checked?[...priorities,String(p)]:priorities.filter(v=>v!==String(p))).filter(Boolean).join(","))}/>⚑ P{p} <span>{counts?.["p"+p]??0}</span></label>)}<DateRangeFilter params={params} setMany={setMany}/><input className="input !w-28" aria-label="Label filter" placeholder="#label" value={params.label??""} onChange={e=>set("label",e.target.value.replace(/^#/,""))}/></div>;
}


