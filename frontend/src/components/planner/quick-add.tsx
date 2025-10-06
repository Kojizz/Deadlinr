"use client";
import {useEffect,useRef,useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {Modal,useToast} from "@/components/ui/workspace";
import {api} from "@/lib/api";
import {keys} from "@/lib/query-keys";
import {formatUtcOffset} from "@/lib/datetime";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import type {WorkItem} from "@/lib/work";
import {captureInput,draftWarnings,emptyDraft} from "@/lib/capture/draft";
import {localSubtaskProvider} from "@/lib/capture/local-ai-client";
import type {CaptureDraft,LocalAIStatus,SubtaskProvider,TaskParser} from "@/lib/capture/types";

const offlineParser:TaskParser={id:"chrono-offline",async parse(text,context){return (await import("@/lib/capture/chrono-parser")).chronoTaskParser.parse(text,context);}};
type Suggestion={title:string;include:boolean;savedId?:number};

export default function QuickAdd({onClose,parser=offlineParser,subtaskProvider=localSubtaskProvider}:{onClose:()=>void;parser?:TaskParser;subtaskProvider?:SubtaskProvider}){
 const [context]=useState(()=>({now:new Date(),timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone}));
 const [text,setText]=useState(""),[draft,setDraft]=useState(()=>emptyDraft(context.timeZone));
 const [warnings,setWarnings]=useState<string[]>([]),[parsing,setParsing]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const [ai,setAI]=useState<LocalAIStatus>({available:false}),[checking,setChecking]=useState(true),[suggesting,setSuggesting]=useState(false),[aiError,setAIError]=useState("");
 const [suggestions,setSuggestions]=useState<Suggestion[]>([]),[savedId,setSavedId]=useState<number|null>(null);
 const refresh=useRefreshWorkspace(),toast=useToast(),cache=useQueryClient();
 const inFlight=useRef(false),parseVersion=useRef(0),aiRequest=useRef<AbortController|null>(null);
 useEffect(()=>()=>aiRequest.current?.abort(),[]);
 useEffect(()=>{
  const controller=new AbortController();
  subtaskProvider.detect(controller.signal).then(status=>{if(!controller.signal.aborted){setAI(status);setChecking(false);}}).catch(()=>{if(!controller.signal.aborted){setAI({available:false,message:"Local AI unavailable. Task capture still works."});setChecking(false);}});
  return()=>controller.abort();
 },[subtaskProvider]);
 useEffect(()=>{
  const version=++parseVersion.current;
  let active=true;
  if(!text.trim()){setDraft(emptyDraft(context.timeZone));setWarnings([]);setParsing(false);return;}
  const timer=setTimeout(async()=>{
   try{const result=await parser.parse(text,context);if(active&&version===parseVersion.current){setDraft(result.draft);setWarnings(result.warnings);}}
   catch{if(active&&version===parseVersion.current){setDraft({...emptyDraft(context.timeZone),title:text.trim()});setWarnings(["Could not parse this phrase. Fill in the preview manually; your text is still here."]);}}
   finally{if(active&&version===parseVersion.current)setParsing(false);}
  },180);
  return()=>{active=false;clearTimeout(timer);};
 },[text,context,parser]);
 const set=<K extends keyof CaptureDraft>(key:K,value:CaptureDraft[K])=>{
  setDraft(current=>({...current,[key]:value,...(key==="due"||key==="allDay"?{dueInstant:undefined}:{}),...(key==="start"?{startInstant:undefined}:{})}));setError("");
  if(key==="title"){aiRequest.current?.abort();setSuggesting(false);setSuggestions([]);}
 };
 let validation="",previewInstant:string|Date=context.now;
 try{previewInstant=captureInput(draft).dueAt??context.now;}catch(e){validation=e instanceof Error?e.message:"Review the preview.";}
 const notices=[...warnings,...draftWarnings(draft,context.now)];
 async function suggest(){
  const controller=new AbortController();aiRequest.current=controller;setSuggesting(true);setAIError("");
  try{const titles=await subtaskProvider.suggest(draft.title,controller.signal);if(!controller.signal.aborted)setSuggestions(titles.map(title=>({title,include:true})));}
  catch(e){if(!controller.signal.aborted)setAIError(e instanceof Error?e.message:"Local AI unavailable. You can still save.");}
  finally{if(!controller.signal.aborted)setSuggesting(false);}
 }
 async function submit(e:React.FormEvent){
  e.preventDefault();if(inFlight.current||parsing||validation)return;
  if(suggestions.some(s=>s.include&&!s.title.trim())){setError("Enter a title for each selected subtask or uncheck it.");return;}
  inFlight.current=true;setBusy(true);setError("");
  aiRequest.current?.abort();setSuggesting(false);
  let parentId=savedId;
  try{
   const input=captureInput(draft);
   if(parentId===null){const saved=await api.post<WorkItem>("/work-items",input);parentId=saved.id;setSavedId(saved.id);}
   for(let index=0;index<suggestions.length;index++){
    const suggestion=suggestions[index];if(!suggestion.include||suggestion.savedId)continue;
    const saved=await api.post<WorkItem>("/work-items",{kind:"TASK",title:suggestion.title.trim(),parentId,timeZone:draft.timeZone,priority:draft.priority,labels:input.labels});
    setSuggestions(current=>current.map((s,i)=>i===index?{...s,savedId:saved.id}:s));
   }
   await Promise.all([refresh(),cache.invalidateQueries({queryKey:keys.labels()})]);
   toast({message:suggestions.some(s=>s.include)?"Task and selected subtasks created":"Task created"});onClose();
  }catch(e){setError((parentId!==null?"Your task is saved. Retry to finish the remaining subtasks; saved items will not be duplicated. ":"")+(e instanceof Error?e.message:"Could not save. Try again."));await refresh();}
  finally{setBusy(false);inFlight.current=false;}
 }
 return <Modal open title="Quick add" onClose={()=>{if(!busy)onClose();}}><form className="grid gap-4" onSubmit={submit}>
  <label className="field">Task in your words<input autoFocus aria-label="Quick add text" aria-describedby="capture-help" disabled={busy||savedId!==null} maxLength={2000} value={text} onChange={e=>{parseVersion.current++;setParsing(true);setText(e.target.value);setSuggestions([]);setError("");aiRequest.current?.abort();setSuggesting(false);}} onKeyDown={e=>{if(e.key==="Enter"){e.preventDefault();if(!parsing)document.getElementById("capture-title")?.focus();}}} placeholder="Report due Friday 18:00 #work p1"/></label>
  <p id="capture-help" className="text-xs text-muted-foreground">Preview first, then save. Try “Report start tomorrow 09:00 due Friday 18:00”. Changing the phrase replaces the preview.</p>
  <p className="text-sm text-muted-foreground" role="status">{parsing?"Reading your task…":"Edit the preview below. Nothing is saved until you choose Add task."}</p>
  <fieldset className="panel grid min-w-0 gap-3 p-4" aria-label="Parsed task preview" disabled={parsing||busy||savedId!==null}>
   <legend className="px-1 text-sm font-semibold">Preview</legend>
   <label className="field">Title<input id="capture-title" aria-label="Preview title" required maxLength={500} value={draft.title} onChange={e=>set("title",e.target.value)}/></label>
   <div className="form-grid">
    <label className="field">Deadline<input aria-label="Preview deadline" type={draft.allDay?"date":"datetime-local"} value={draft.allDay?draft.due.slice(0,10):draft.due} onChange={e=>set("due",e.target.value&&draft.allDay?e.target.value+"T23:59":e.target.value)}/></label>
    <label className="field">Start time<input aria-label="Preview start time" type="datetime-local" value={draft.start} onChange={e=>set("start",e.target.value)}/></label>
   </div>
   <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={draft.allDay} onChange={e=>{set("allDay",e.target.checked);if(draft.due&&e.target.checked)set("due",draft.due.slice(0,10)+"T23:59");}}/>Date only (due at 23:59)</label>
   <p className="text-xs text-muted-foreground" aria-label="Capture timezone" title={"Timezone ID: "+draft.timeZone}>Timezone: {formatUtcOffset(previewInstant,draft.timeZone)}</p>
   <div className="form-grid">
    <label className="field">Labels<input aria-label="Preview labels" value={draft.labels} placeholder="work, university" onChange={e=>set("labels",e.target.value)}/></label>
    <label className="field">Priority<select aria-label="Preview priority" value={draft.priority} onChange={e=>set("priority",Number(e.target.value))}>{["Urgent","High","Medium","Low"].map((name,i)=><option key={name} value={i+1}>P{i+1} {name}</option>)}</select></label>
   </div>
   <label className="field">Repeat<select value={draft.repeatRule} onChange={e=>set("repeatRule",e.target.value)}>{["NONE","DAILY","WEEKLY","MONTHLY","YEARLY"].map(value=><option key={value} value={value}>{value.toLowerCase()}</option>)}</select></label>
  </fieldset>
  {!!notices.length&&<ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground" aria-live="polite">{notices.map((message,i)=><li key={i}>{message}</li>)}</ul>}
  {!!text&&validation&&<p className="text-sm text-destructive" role="alert">{validation}</p>}
  <div className="rounded-md border p-3 text-sm">
   {checking?<p className="text-muted-foreground">Checking for local AI…</p>:ai.available?<><p className="mb-2 text-xs text-muted-foreground">Local AI · {ai.model}. Suggestions are drafts for you to review.</p><button className="btn" type="button" disabled={suggesting||parsing||busy||!draft.title.trim()||savedId!==null} onClick={suggest}>{suggesting?"Breaking down task…":"Break this task into subtasks"}</button>{suggesting&&<button className="btn ml-2" type="button" onClick={()=>{aiRequest.current?.abort();setSuggesting(false);}}>Cancel AI</button>}</>:<><p className="text-muted-foreground">{ai.message??"Enable local AI: run Ollama with a downloaded local model. Task capture works without it."}</p><button className="mt-2 text-primary underline" type="button" disabled={busy} onClick={async()=>{setChecking(true);try{setAI(await subtaskProvider.detect());}catch{setAI({available:false});}finally{setChecking(false);}}}>Check again</button></>}
   {aiError&&<p role="alert" className="mt-2 text-destructive">{aiError}</p>}
   {!!suggestions.length&&<fieldset className="mt-3 grid gap-2" disabled={busy}><legend className="mb-2 font-medium">Subtasks to include</legend>{suggestions.map((suggestion,index)=><div key={index} className="flex items-center gap-2"><input type="checkbox" aria-label={`Include subtask ${index+1}`} checked={suggestion.include} disabled={!!suggestion.savedId} onChange={e=>setSuggestions(current=>current.map((s,i)=>i===index?{...s,include:e.target.checked}:s))}/><input className="min-w-0 flex-1 rounded border bg-transparent p-2" aria-label={`Subtask ${index+1} title`} value={suggestion.title} maxLength={500} disabled={!!suggestion.savedId} onChange={e=>setSuggestions(current=>current.map((s,i)=>i===index?{...s,title:e.target.value}:s))}/>{suggestion.savedId&&<span>Saved</span>}</div>)}</fieldset>}
  </div>
  {error&&<p role="alert" className="text-sm text-destructive">{error}</p>}
  <div className="flex justify-end gap-2"><button type="button" className="btn" disabled={busy} onClick={onClose}>Cancel</button><button type="submit" className="btn btn-primary" disabled={parsing||busy||!!validation}>{busy?"Saving…":savedId!==null?"Retry remaining subtasks":"Add task"}</button></div>
 </form></Modal>;
}
