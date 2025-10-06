"use client";

import { useParams } from "next/navigation";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { keys } from "@/lib/query-keys";
import type { Page } from "@/lib/types";

const EditorPage = dynamic(
  () => import("@/components/editor/editor-page").then((m) => m.EditorPage),
  {
    ssr: false,
    loading: () => (
      <div className="mx-auto max-w-3xl space-y-4 px-6 py-10 md:px-12">
        <div className="flex items-center gap-3">
          <Skeleton className="h-16 w-16 rounded-xl" />
          <Skeleton className="h-10 w-64" />
        </div>
        <Skeleton className="h-5 w-full" />
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-5 w-3/5" />
      </div>
    ),
  },
);

export default function PageRoute() {
  const params = useParams<{ id: string }>();
  const pageId = Number(params.id);
  // Start the active document request alongside the lazy editor import.
  // EditorPage reads this same cache; ancestors only fetch lightweight metadata.
  useQuery({queryKey:keys.pages.one(pageId),queryFn:()=>api.get<Page>(`/pages/${pageId}`),enabled:Number.isFinite(pageId),retry:1,staleTime:60_000});

  if (!Number.isFinite(pageId)) {
    return (
      <div className="px-6 py-16 md:px-12">
        <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
      </div>
    );
  }

  return <EditorPage pageId={pageId} />;
}
