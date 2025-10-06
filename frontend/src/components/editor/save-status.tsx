"use client";

import { Check, CloudOff, LoaderCircle } from "lucide-react";
import { cn } from "@/lib/utils";

export type SaveState = "idle" | "saving" | "saved" | "error";

export function SaveStatus({ state }: { state: SaveState }) {
  if (state === "idle") return null;

  return (
    <div
      className={cn(
        "pointer-events-none fixed bottom-4 right-4 z-40 flex items-center gap-1.5 rounded-full border bg-popover/90 px-3 py-1.5 text-xs backdrop-blur",
        state === "error" ? "text-destructive" : "text-muted-foreground",
      )}
      role="status"
      aria-live="polite"
    >
      {state === "saving" && (
        <>
          <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
          Saving…
        </>
      )}
      {state === "saved" && (
        <>
          <Check className="h-3.5 w-3.5 text-emerald-500" />
          Saved
        </>
      )}
      {state === "error" && (
        <>
          <CloudOff className="h-3.5 w-3.5" />
          Failed to save
        </>
      )}
    </div>
  );
}
