"use client";
import dynamic from "next/dynamic";
import {useState} from "react";
import {useAuth} from "@/hooks/use-auth";
const Menu=dynamic(()=>import("./user-menu").then(m=>m.UserMenu),{ssr:false});
export function UserMenuHost(){const[loaded,setLoaded]=useState(false),{user}=useAuth();const initials=user?.fullName.split(" ").map(s=>s[0]).slice(0,2).join("").toUpperCase()??"?";return loaded?<Menu initialOpen/>:<button aria-haspopup="menu" className="h-8 w-8 shrink-0 rounded-full bg-primary text-primary-foreground text-xs font-semibold" onClick={()=>setLoaded(true)} title="Account and appearance">{initials}</button>;}
