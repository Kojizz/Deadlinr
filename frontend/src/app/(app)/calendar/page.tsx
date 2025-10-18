"use client";
import dynamic from "next/dynamic";
const Calendar=dynamic(()=>import("@/components/planner/calendar"),{ssr:false,loading:()=> <div className="workspace"><div className="skeleton h-96 rounded-xl"/></div>});
export default Calendar;
