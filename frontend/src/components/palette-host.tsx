"use client";
import { useEffect,useRef,useState } from "react";
import dynamic from "next/dynamic";
import { useCreatePage } from "@/hooks/use-create-page";
import { OPEN_PALETTE } from "@/lib/palette-events";
const Palette=dynamic(()=>import("./command-palette").then(m=>m.CommandPalette),{ssr:false});
export function PaletteHost(){
 const [loaded,setLoaded]=useState(false);const create=useCreatePage();
 // The palette mounts after this host's open event, so it can't capture the
 // originally-focused element itself; the host keeps it for the first open
 // and hands it over so closing the dialog restores focus.
 const returnFocus=useRef<{current:HTMLElement|null}>({current:null});
 useEffect(()=>{
  const show=()=>{returnFocus.current.current=document.activeElement instanceof HTMLElement?document.activeElement:null;setLoaded(true);};
  const key=(e:KeyboardEvent)=>{if(e.repeat||e.isComposing)return;
  if((e.ctrlKey||e.metaKey)&&e.shiftKey&&!e.altKey&&e.key.toLowerCase()==="k"&&!document.querySelector("dialog[open]")){e.preventDefault();show();}
  if(e.ctrlKey&&e.altKey&&!e.getModifierState("AltGraph")&&e.key.toLowerCase()==="n"){e.preventDefault();create.mutate();}
  };
  window.addEventListener(OPEN_PALETTE,show);window.addEventListener("keydown",key);
  return()=>{window.removeEventListener(OPEN_PALETTE,show);window.removeEventListener("keydown",key);};
 },[create]);
 return loaded?<Palette initialOpen returnFocusOnClose={returnFocus.current}/>:null;
}
