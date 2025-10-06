// Same-origin by default: the Next.js dev server rewrites /api/* to the
// backend, so auth cookies are first-party (no CORS, no SameSite issues).
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/** Fired when the session can't be recovered (refresh failed). */
export const SESSION_EXPIRED_EVENT = "deadlinr:session-expired";

/**
 * Legacy bearer token (only used when the backend runs with
 * app.features.refresh-tokens=false). In cookie mode auth rides on httpOnly
 * cookies and this stays null.
 */
function getLegacyToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("token");
}

/** Single-flight silent refresh; true when the session was renewed. */
let refreshPromise: Promise<boolean> | null = null;

function tryRefresh(): Promise<boolean> {
  refreshPromise ??= fetch(`${API_URL}/auth/refresh`, {
    method: "POST",
    credentials: "include",
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

function sessionExpired() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
}

const NO_REFRESH_PATHS = ["/auth/login", "/auth/register", "/auth/refresh"];

async function request<T>(path: string, options: RequestInit = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.body instanceof FormData ? {} : {"Content-Type": "application/json"}),
    ...(options.headers as Record<string, string>),
  };
  const token = getLegacyToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers, credentials: "include" });

  // Silent refresh on 401 (access token expired): rotate and retry once.
  if (res.status === 401 && !retried && !NO_REFRESH_PATHS.includes(path)) {
    if (await tryRefresh()) {
      return request<T>(path, options, true);
    }
    sessionExpired();
    throw new ApiError("Session expired", 401);
  }

  if (res.status === 204) return undefined as T;

  const data = await res.json().catch(() => null);

  if(res.status===409&&data?.message==="This is a hard deadline. Move anyway?"&&typeof window!=="undefined"){
    if(window.confirm(data.message)){const body=JSON.parse(String(options.body??"{}"));if(path.endsWith("/bulk"))body.patch={...body.patch,confirmMove:true};else body.confirmMove=true;return request<T>(path,{...options,body:JSON.stringify(body)},retried);}
    throw new ApiError("Date change cancelled",409);
  }
  if (!res.ok) {
    throw new ApiError(data?.message ?? `Request failed (${res.status})`, res.status);
  }

  return data as T;
}

export const api = {
  download: (path: string, body: unknown) => blobResponse(path,{method:"POST",body:JSON.stringify(body)}),
  file: (path:string) => blobResponse(path,{method:"GET"}),
  upload: <T>(path:string,file:File) => {const body=new FormData();body.append("file",file);return request<T>(path,{method:"POST",body});},
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

async function blobResponse(path:string,options:RequestInit,retried=false):Promise<Blob>{
 const headers:Record<string,string>={"Content-Type":"application/json"};const token=getLegacyToken();if(token)headers.Authorization="Bearer "+token;
 const res=await fetch(API_URL+path,{...options,credentials:"include",headers,redirect:"error"});
 if(res.status===401){
  if(!retried&&await tryRefresh())return blobResponse(path,options,true);
  sessionExpired();throw new ApiError("Session expired",401);
 }
 if(!res.ok){const error=await res.json().catch(()=>null);throw new ApiError(error?.message??`Download failed (${res.status})`,res.status);}
 return res.blob();
}

export async function downloadFile(id:string,name:string){
 const headers:Record<string,string>={};const token=getLegacyToken();if(token)headers.Authorization="Bearer "+token;
 let res=await fetch(API_URL+"/files/"+id,{credentials:"include",headers});
 if(res.status===401&&await tryRefresh())res=await fetch(API_URL+"/files/"+id,{credentials:"include",headers});
 if(!res.ok)throw new Error("Could not download file ("+res.status+")");
 const url=URL.createObjectURL(await res.blob()),a=document.createElement("a");a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);
}
