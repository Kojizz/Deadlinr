import {formatMonthDay} from "@/lib/datetime";

export type WorkKind="TASK"|"EVENT";
export interface WorkItem {
 id:number;ownerId:number;kind:WorkKind;title:string;description:string;pageId:number|null;courseId:number|null;parentId:number|null;
 status:"TODO"|"IN_PROGRESS"|"DONE"|"SOMEDAY";priority:number;dueAt:string|null;startAt:string|null;allDay:boolean;hardDeadline:boolean;
 location:string;link:string;repeatRule:string;labels:string[];reminderMinutes:number[];position:number;completedAt:string|null;archivedAt:string|null;createdAt:string;version:number;timeZone:string;
 fileCount:number;subtaskCount:number;subtasksDone:number;blocked:boolean;pageTitle:string|null;
}
export type WorkInput=Partial<Pick<WorkItem,"kind"|"title"|"description"|"pageId"|"parentId"|"status"|"priority"|"dueAt"|"startAt"|"allDay"|"hardDeadline"|"location"|"link"|"repeatRule"|"labels"|"reminderMinutes"|"position"|"version"|"timeZone">>;
export interface WorkList {items:WorkItem[];total:number;counts:{all:number;todo:number;progress:number;done:number;overdue:number};page:number;size:number;groupCounts:{name:string;count:number}[];priorityCounts:Record<string,number>;}
export const openWork=(id:number)=>window.dispatchEvent(new CustomEvent("deadlinr:detail",{detail:{id}}));
export const createWork=(initial:WorkInput={kind:"TASK"})=>window.dispatchEvent(new CustomEvent("deadlinr:create",{detail:initial}));
export function localDate(d:Date){return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");}
export function dueLabel(value:string|null){if(!value)return "No date";const d=new Date(value),today=new Date();const delta=Math.round((new Date(d.getFullYear(),d.getMonth(),d.getDate()).getTime()-new Date(today.getFullYear(),today.getMonth(),today.getDate()).getTime())/86400000);return delta===0?"Today":delta===1?"Tomorrow":delta===-1?"Yesterday":formatMonthDay(d);}








