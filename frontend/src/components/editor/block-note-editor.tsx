"use client";

import "@blocknote/shadcn/style.css";
import "./blocknote-tokens.css";

import { useEffect, useRef, useState } from "react";
import { BlockNoteView } from "@blocknote/shadcn";
import { useCreateBlockNote, FilePanel, FilePanelController, UploadTab, type FilePanelProps } from "@blocknote/react";
import type { PartialBlock } from "@blocknote/core";
import { api } from "@/lib/api";

interface BlockNoteEditorProps {
  pageId: number;
  initialContent?: PartialBlock[] | null;
  theme: "light" | "dark";
  onChange: (content: PartialBlock[]) => void;
}

export function BlockNoteEditor({
  pageId,
  initialContent,
  theme,
  onChange,
}: BlockNoteEditorProps) {
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const [uploads,setUploads]=useState(0),[mediaError,setMediaError]=useState("");
  const resolved=useRef(new Map<string,Promise<string>>()),objectUrls=useRef(new Set<string>()),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;const urls=objectUrls.current,cache=resolved.current;return()=>{mounted.current=false;urls.forEach(url=>URL.revokeObjectURL(url));urls.clear();cache.clear();};},[]);

  const editor = useCreateBlockNote(
    {
      initialContent: initialContent ?? undefined,
      uploadFile: async file=>{
        if(!file.size||file.size>20*1024*1024){setMediaError("Choose a file between 1 byte and 20 MiB.");return "";}
        setUploads(n=>n+1);setMediaError("");
        try{const saved=await api.upload<{id:string}>(`/pages/${pageId}/files`,file);return `/api/files/${saved.id}/content`;}
        // BlockNote's paste/drop path doesn't catch rejected uploads. Leave the
        // empty placeholder and show the error without an unhandled rejection.
        catch(error){setMediaError(error instanceof Error?error.message:"Could not upload file. Try again.");return "";}
        finally{setUploads(n=>n-1);}
      },
      resolveFileUrl: url=>{
        if(!url)return Promise.resolve("");
        if(!/^\/api\/files\/[a-f0-9-]{36}(?:\/content)?$/i.test(url)){setMediaError("This media reference is not a local uploaded file. Upload the original file to keep it available offline.");return Promise.resolve("");}
        const cached=resolved.current.get(url);if(cached)return cached;
        const pending=api.file(url.slice(4)).then(blob=>{if(!mounted.current)return "";const value=URL.createObjectURL(blob);objectUrls.current.add(value);return value;}).catch(error=>{resolved.current.delete(url);if(mounted.current)setMediaError(error instanceof Error?error.message:"Could not load media. Reload to try again.");return "";});
        resolved.current.set(url,pending);return pending;
      },
    },
    [],
  );

  useEffect(() => {
    const unsubscribe = editor.onChange(() => {
      onChangeRef.current(editor.document);
    });
    return unsubscribe;
  }, [editor]);

  return (
    <div className="bn-shadcn" onKeyDownCapture={event=>{
      if(!(event.ctrlKey||event.metaKey)||!event.altKey||event.shiftKey||!["KeyC","Digit0"].includes(event.code))return;
      const target=event.target as HTMLElement;
      if(!target.closest(".bn-editor")||target.closest("input,textarea,select,[role=combobox]"))return;
      // Override the editor's character-key fallback, including for guarded
      // events: AltGr must remain text input, not invoke its legacy keymap.
      event.stopPropagation();
      if(event.defaultPrevented||event.repeat||event.nativeEvent.isComposing||event.getModifierState("AltGraph")||!editor.isEditable||document.querySelector("dialog[open],[role=dialog][data-state=open]"))return;
      const block=editor.getTextCursorPosition().block;
      if(!Array.isArray(block.content))return;
      event.preventDefault();
      editor.updateBlock(block,{type:event.code==="KeyC"?"codeBlock":"paragraph"});
    }}>
      <p className="mb-3 text-xs text-muted-foreground">Upload local images, video, audio or files, or paste/drop them into the document. Up to 20 MiB per file; images up to 20 megapixels. Supported: PNG, JPEG, GIF, MP4, WebM, MP3, WAV, PDF, TXT, CSV, ZIP and Office documents.</p>
      {uploads>0&&<p role="status" className="mb-3 text-sm text-muted-foreground">Uploading {uploads===1?"file":`${uploads} files`}… Keep this page open until saved.</p>}
      {mediaError&&<p role="alert" className="mb-3 text-sm text-destructive">{mediaError}</p>}
      <BlockNoteView
        editor={editor}
        theme={theme}
        className="min-h-[60vh]"
        sideMenu
        slashMenu
        formattingToolbar
        linkToolbar
        tableHandles
        filePanel={false}
        emojiPicker={false}
      ><FilePanelController filePanel={LocalFilePanel}/></BlockNoteView>
    </div>
  );
}

// Local uploads are the persistence path. Remote embeds would require internet.
function LocalFilePanel(props:FilePanelProps){
 const [loading,setLoading]=useState(false);
 return <fieldset disabled={loading} aria-busy={loading}><FilePanel {...props} tabs={[{name:"Upload",tabPanel:<UploadTab block={props.block} setLoading={setLoading}/>}]}/></fieldset>;
}
