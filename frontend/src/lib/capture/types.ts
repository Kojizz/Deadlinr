import type {WorkInput} from "../work";

export interface CaptureDraft {
 title:string;
 due:string;
 start:string;
 allDay:boolean;
 labels:string;
 priority:number;
 repeatRule:string;
 timeZone:string;
 // Preserve explicitly parsed offsets during repeated DST hours. Editing the
 // matching wall-clock input clears the override.
 dueInstant?:string;
 startInstant?:string;
}
export interface CaptureContext {now:Date;timeZone:string;}
export interface CaptureResult {draft:CaptureDraft;warnings:string[];}
export interface TaskParser {
 readonly id:string;
 parse(text:string,context:CaptureContext):CaptureResult|Promise<CaptureResult>;
}
export interface LocalAIStatus {available:boolean;model?:string;message?:string;}
export interface SubtaskProvider {
 readonly id:string;
 detect(signal?:AbortSignal):Promise<LocalAIStatus>;
 suggest(title:string,signal?:AbortSignal):Promise<string[]>;
}
export type CaptureInput=WorkInput&{title:string;timeZone:string};
