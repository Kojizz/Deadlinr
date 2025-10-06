"use client";

import { PageHeaderActions } from "@/components/page-actions";
import { PageTasks } from "@/components/planner/page-tasks";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTheme } from "next-themes";
import { ImagePlus, ImageOff } from "lucide-react";
import type { PartialBlock } from "@blocknote/core";
import dynamic from "next/dynamic";
// BlockNote is the heaviest chunk on the route (~2 MB); load it after the
// page shell (title, tasks, metadata) is already interactive.
const BlockNoteEditor = dynamic(() => import("./block-note-editor").then(m => m.BlockNoteEditor), {
  ssr: false,
  loading: () => <Skeleton className="h-96 w-full rounded-xl" />,
});
import { CoverPicker } from "./cover-picker";
import { IconPicker } from "./icon-picker";
import { SaveStatus, type SaveState } from "./save-status";
import { useDebouncedCallback } from "@/lib/autosave";
import { api } from "@/lib/api";
import { coverCss } from "@/lib/covers";
import type { Page } from "@/lib/types";
import { Skeleton } from "@/components/ui/skeleton";

function EditorSkeleton() {
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-6 py-10 md:px-12">
      <div className="flex items-center gap-3">
        <Skeleton className="h-16 w-16 rounded-xl" />
        <Skeleton className="h-10 w-64" />
      </div>
      <Skeleton className="h-5 w-full" />
      <Skeleton className="h-5 w-4/5" />
      <Skeleton className="h-5 w-3/5" />
    </div>
  );
}

function AutoGrowTextarea({
  value,
  onChange,
  onBlur,
}: {
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
}) {
  return (
    <textarea
      value={value}
      rows={1}
      placeholder="Untitled"
      aria-label="Page title"
      autoFocus
      onChange={(e) => {
        onChange(e.target.value);
        e.target.style.height = "auto";
        e.target.style.height = `${e.target.scrollHeight}px`;
      }}
      onFocus={(e) => {
        // New pages open with "Untitled" pre-selected so typing replaces it.
        if (e.target.value === "Untitled") e.target.select();
      }}
      onBlur={onBlur}
      className="w-full resize-none overflow-hidden bg-transparent text-4xl font-semibold tracking-tight outline-none placeholder:text-muted-foreground/60 focus-visible:ring-2"
    />
  );
}

function EditorContent({ page }: { page: Page }) {
  const queryClient = useQueryClient();
  const { resolvedTheme } = useTheme();

  const [title, setTitle] = useState(page.title);
  const [icon, setIcon] = useState<string | null>(page.icon ?? null);
  const [cover, setCover] = useState<string | null>(page.coverStyle ?? null);
  const [saveState, setSaveState] = useState<SaveState>("idle");

  const saveContent = useMutation({
    mutationFn: (content: PartialBlock[]) =>
      api.put(`/pages/${page.id}/content`, { content }),
    onMutate: () => setSaveState("saving"),
    onSuccess: () => {
      setSaveState("saved");
      void queryClient.invalidateQueries({ queryKey: ["search"] });
    },
    onError: () => setSaveState("error"),
  });

  const saveTitle = useMutation({
    mutationFn: (title: string) => api.patch(`/pages/${page.id}`, { title }),
    onMutate: () => setSaveState("saving"),
    onSuccess: () => {
      setSaveState("saved");
      queryClient.invalidateQueries({ queryKey: ["pages", "tree"] });
      queryClient.invalidateQueries({ queryKey: ["pages", page.id], exact: true });
      queryClient.invalidateQueries({ queryKey: ["search"] });
    },
    onError: () => setSaveState("error"),
  });

  const saveMeta = useMutation({
    mutationFn: (patch: { icon?: string; coverStyle?: string }) =>
      api.patch(`/pages/${page.id}`, patch),
    onMutate: () => setSaveState("saving"),
    onSuccess: () => {
      setSaveState("saved");
      queryClient.invalidateQueries({ queryKey: ["pages", "tree"] });
      queryClient.invalidateQueries({ queryKey: ["pages", page.id], exact: true });
      queryClient.invalidateQueries({ queryKey: ["search"] });
    },
    onError: () => setSaveState("error"),
  });

  const { debounced: debouncedSaveContent } = useDebouncedCallback(
    (content: PartialBlock[]) => saveContent.mutate(content),
    800,
  );
  const { debounced: debouncedSaveTitle, flush: flushTitle } =
    useDebouncedCallback((t: string) => saveTitle.mutate(t), 600);

  const coverCssValue = coverCss(cover);
  const theme = resolvedTheme === "dark" ? ("dark" as const) : ("light" as const);

  return (
    <div className="relative min-h-full">
      <SaveStatus state={saveState} />
      <PageHeaderActions id={page.id} />

      {coverCssValue && (
        <div
          className="group relative h-44"
          style={{ background: coverCssValue }}
        >
          <div className="absolute right-3 top-3 flex gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100">
            <CoverPicker
              value={cover}
              onChange={(token) => {
                setCover(token);
                saveMeta.mutate({ coverStyle: token ?? "" });
              }}
              align="end"
              triggerClassName="rounded-md bg-background/70 px-2.5 py-1 text-xs font-medium transition-colors hover:bg-background/90"
              triggerChildren="Change cover"
            />
            <button
              type="button"
              onClick={() => {
                setCover(null);
                saveMeta.mutate({ coverStyle: "" });
              }}
              aria-label="Remove cover"
              className="flex items-center gap-1 rounded-md bg-background/70 px-2.5 py-1 text-xs font-medium transition-colors hover:bg-background/90"
            >
              <ImageOff className="h-3.5 w-3.5" />
              Remove
            </button>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-3xl px-6 pb-40 pt-8 md:px-12">
        {!coverCssValue && (
          <div className="mb-4">
            <CoverPicker
              value={cover}
              onChange={(token) => {
                setCover(token);
                saveMeta.mutate({ coverStyle: token ?? "" });
              }}
              triggerClassName="flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
              triggerChildren={
                <>
                  <ImagePlus className="h-4 w-4" />
                  Add cover
                </>
              }
            />
          </div>
        )}

        <div className="flex items-start gap-3">
          <IconPicker
            value={icon}
            onChange={(emoji) => {
              setIcon(emoji);
              saveMeta.mutate({ icon: emoji ?? "" });
            }}
          />
          <AutoGrowTextarea
            value={title}
            onChange={(value) => {
              setTitle(value);
              debouncedSaveTitle(value);
            }}
            onBlur={() => {
              flushTitle();
            }}
          />
        </div>

        {page.archivedAt && (
          <p className="mt-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            This page is in the trash. Restore it from the sidebar to keep editing.
          </p>
        )}

        <PageTasks pageId={page.id} />

        <div className="mt-6">
          <BlockNoteEditor
            pageId={page.id}
            initialContent={page.content as unknown as PartialBlock[] | null}
            theme={theme}
            onChange={(content) => debouncedSaveContent(content)}
          />
        </div>
      </div>
    </div>
  );
}

export function EditorPage({ pageId }: { pageId: number }) {
  const { data: page, isLoading, isError } = useQuery({
    queryKey: ["pages", pageId],
    queryFn: () => api.get<Page>(`/pages/${pageId}`),
    retry: 1,
    staleTime: 60_000,
  });

  if (isLoading) return <EditorSkeleton />;

  if (isError || !page) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col items-start gap-3 px-6 py-16 md:px-12">
        <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
        <p className="text-sm text-muted-foreground">
          This page doesn&apos;t exist or you don&apos;t have access to it.
        </p>
      </div>
    );
  }

  return <EditorContent key={page.id} page={page} />;
}
