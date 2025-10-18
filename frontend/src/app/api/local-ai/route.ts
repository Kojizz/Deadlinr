import {OllamaSubtaskProvider} from "@/lib/capture/ollama";

export const runtime="nodejs";
export const dynamic="force-dynamic";
const provider=new OllamaSubtaskProvider();
function localRequest(request:Request){
 const url=new URL(request.url);
 if(!["localhost","127.0.0.1","[::1]"].includes(url.hostname))return false;
 const origin=request.headers.get("origin");
 return (!origin||origin===url.origin)&&request.headers.get("sec-fetch-site")!=="cross-site";
}
export async function GET(request:Request){
 if(!localRequest(request))return Response.json({available:false,message:"Local AI is available when Deadlinr is opened on localhost."});
 return Response.json(await provider.detect(request.signal));
}
export async function POST(request:Request){
 if(!localRequest(request))return Response.json({message:"Open Deadlinr on localhost to use local AI."},{status:403});
 try{
  if(!request.headers.get("content-type")?.startsWith("application/json"))return Response.json({message:"Expected JSON."},{status:415});
  const body=await request.text();
  if(body.length>4096)return Response.json({message:"Task is too long."},{status:413});
  const {title}=JSON.parse(body);
  if(typeof title!=="string"||!title.trim()||title.length>500)return Response.json({message:"Enter a title of 1–500 characters."},{status:400});
  return Response.json({subtasks:await provider.suggest(title.trim(),request.signal)});
 }catch(error){return Response.json({message:error instanceof SyntaxError?"Expected valid JSON.":error instanceof Error?error.message:"Local AI is unavailable. You can still save your task."},{status:error instanceof SyntaxError?400:503});}
}
