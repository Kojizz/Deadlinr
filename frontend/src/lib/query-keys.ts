// Single source of truth for TanStack Query keys.
export const keys={
 work:{
  list:(q:string)=>["work","list",q] as const,
  detail:(id:number)=>["work","detail",id] as const,
  calendar:(range:string)=>["work","calendar",range] as const,
  matrix:()=>["work","matrix"] as const,
  deadlineDays:(fromIso:string)=>["work","deadline-days",fromIso] as const,
  files:(id:number)=>["work","files",id] as const,
  reminders:(id:number)=>["work","reminders",id] as const,
  dependencies:(id:number)=>["work","dependencies",id] as const,
  today:(zone:string,view:string,page:number)=>["work","today",zone,view,page] as const,
 },
 workspace:{
  summary:()=>["workspace","summary"] as const,
  history:()=>["workspace","history"] as const,
 },
 pages:{
  tree:()=>["pages","tree"] as const,
  roots:()=>["pages","roots"] as const,
  list:(trash:boolean,page:number)=>["pages","list",trash,page] as const,
  favorites:()=>["pages","favorites"] as const,
  one:(id:number)=>["pages",id] as const,
  ancestors:(id:number|null)=>["pages","ancestors",id] as const,
 },
 notifications:{list:(page:number)=>["notifications",page] as const},
 stats:()=>["stats"] as const,
 settings:()=>["settings"] as const,
 labels:()=>["labels"] as const,
 templates:()=>["templates"] as const,
};
export const invalidateAll=(nets:readonly string[][])=>nets;
