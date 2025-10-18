"use client";

import { createWork, type WorkKind } from "@/lib/work";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { FileText, Plus, Search } from "lucide-react";
import { api } from "@/lib/api";
import type { SearchResult } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useCreatePage } from "@/hooks/use-create-page";

const OPEN_EVENT = "deadlinr:open-palette";
export function openPalette() {
  window.dispatchEvent(new CustomEvent(OPEN_EVENT));
}

export function CommandPalette({initialOpen=false,returnFocusOnClose}:{initialOpen?:boolean;returnFocusOnClose?:{current:HTMLElement|null}}) {
  const router = useRouter();
  const createPage = useCreatePage();
  const [open, setOpen] = useState(initialOpen);
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);
  const returnFocus = useRef<HTMLElement | null>(null);

  const { data, isFetching, isError, refetch } = useQuery({
    queryKey: ["search", query],
    queryFn: ({ signal }) => api.get<SearchResult[]>(`/search?q=${encodeURIComponent(query)}`, signal),
    enabled: open && query.length > 0,
    staleTime: 0,
    retry: 1,
  });
  const waiting = q.trim() !== query || isFetching;
  const results = q.trim() === query && !isError && query ? data ?? [] : [];
  const selectedIndex = Math.min(index, results.length);

  useEffect(() => {
    const timer = setTimeout(() => setQuery(q.trim()), 180);
    return () => clearTimeout(timer);
  }, [q]);

  useEffect(() => {
    function show() {
      returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setQ("");
      setQuery("");
      setIndex(0);
      setOpen(true);
    }
    function onKey(event: KeyboardEvent) {
      if (event.repeat || event.isComposing) return;
      if ((event.ctrlKey || event.metaKey) && event.shiftKey && !event.altKey && event.key.toLowerCase() === "k" && !document.querySelector("dialog[open]")) {
        event.preventDefault();
        show();

      }
    }
    window.addEventListener("keydown", onKey);
    window.addEventListener(OPEN_EVENT, show);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(OPEN_EVENT, show);
    };
  }, [createPage]);

  function choose(result?: SearchResult) {
    setOpen(false);
    if (result) router.push(`/${result.type === "WORK" ? "tasks" : "pages"}/${result.id}`);
    else createPage.mutate();
  }

  useEffect(() => {
    if (open) document.getElementById(`search-option-${selectedIndex}`)?.scrollIntoView({ block: "nearest" });
  }, [open, selectedIndex]);

  const groups = [...new Set(results.map((result) => result.type))];
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-modal bg-black/40" />
        <Dialog.Content data-testid="palette" aria-describedby={undefined}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            (returnFocusOnClose?.current ?? returnFocus.current)?.focus();
          }}
          className="fixed left-1/2 top-[15vh] z-modal w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 overflow-hidden rounded-xl border bg-popover shadow-lg">
          <Dialog.Title className="sr-only">Search workspace</Dialog.Title>
          <div className="flex items-center gap-2 border-b px-3">
            <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
            <input value={q} onChange={(event) => { setQ(event.target.value); setIndex(0); }}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                  event.preventDefault();
                  setIndex(Math.max(0, Math.min(results.length, selectedIndex + (event.key === "ArrowDown" ? 1 : -1))));
                } else if (event.key === "Enter") {
                  event.preventDefault();
                  // Never turn Enter during a pending search into an accidental create.
                  if (!waiting) choose(results[selectedIndex]);
                }
              }}
              className="h-11 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              placeholder="Search pages and tasks…" aria-label="Search" role="combobox" aria-expanded="true"
              aria-controls="workspace-search-results" aria-autocomplete="list"
              aria-activedescendant={`search-option-${selectedIndex}`} />
            <Dialog.Close className="rounded border px-1.5 py-0.5 text-[10px] text-muted-foreground" aria-label="Close search">Esc</Dialog.Close>
          </div>
          <div className="max-h-72 overflow-y-auto p-1">
            {waiting && <p role="status" className="px-3 py-2 text-xs text-muted-foreground">Searching…</p>}
            {isError && !waiting && query && <p role="alert" className="px-3 py-2 text-sm text-destructive">
              Search is unavailable. <button type="button" className="underline" onClick={() => void refetch()}>Retry</button>
            </p>}
            {!waiting && !isError && query && results.length === 0 && <p role="status" className="px-3 py-2 text-xs text-muted-foreground">No results</p>}
            <div id="workspace-search-results" role="listbox" aria-label="Workspace results">
              {groups.map((type) => <div key={type} role="group" aria-label={type === "PAGE" ? "Pages" : type}>
                <p className="px-2 pt-2 text-xs font-medium text-muted-foreground">{type === "PAGE" ? "Pages" : type}</p>
                {results.map((result, i) => result.type !== type ? null : (
                  <button key={`${result.type}-${result.id}`} id={`search-option-${i}`} role="option"
                    aria-selected={i === selectedIndex} type="button" onClick={() => choose(result)} onMouseEnter={() => setIndex(i)}
                    className={cn("flex w-full items-start gap-2 rounded-md px-2 py-2 text-left text-sm", i === selectedIndex && "bg-accent")}>
                    <FileText className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{result.icon ? `${result.icon} ` : ""}{result.title || "Untitled"}</span>
                      {result.snippet && <span className="block truncate text-xs text-muted-foreground">{result.snippet}</span>}
                    </span>
                  </button>
                ))}
              </div>)}
              <button id={`search-option-${results.length}`} role="option" aria-selected={selectedIndex === results.length}
                type="button" onMouseEnter={() => setIndex(results.length)} onClick={() => choose()}
                className={cn("flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm", selectedIndex === results.length && "bg-accent")}>
                <Plus className="h-4 w-4" /> New page <span className="ml-auto text-xs text-muted-foreground">Ctrl+Alt+N</span>
              </button>
            </div>
          </div>
          <div className="flex flex-wrap gap-2 border-t p-2">{["Task","Event"].map(kind=><button key={kind} className="filter-chip" onClick={()=>{setOpen(false);createWork({kind:kind.toUpperCase() as WorkKind});}}>New {kind.toLowerCase()}</button>)}</div>
          <div className="flex gap-3 border-t px-3 py-1.5 text-[10px] text-muted-foreground">
            <span>↑↓ navigate</span><span>↵ open</span><span>esc close</span>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
