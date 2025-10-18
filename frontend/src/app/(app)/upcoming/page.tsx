import {redirect} from "next/navigation";
export default function Page(){
  const start=new Date();start.setHours(0,0,0,0);
  const end=new Date(start);end.setDate(end.getDate()+7);
  redirect("/tasks?from="+encodeURIComponent(start.toISOString())+"&to="+encodeURIComponent(end.toISOString()));
}
