"use client";

import { useMutationState } from "@tanstack/react-query";
const createKey = ["pages", "create"] as const;

export function PageCreationFeedback() {
  const states = useMutationState({
    filters: { mutationKey: createKey },
    select: (mutation) => mutation.state,
  });
  const latest = states[states.length - 1];
  if (latest?.status !== "error") return null;
  return (
    <p role="alert" className="fixed bottom-4 right-4 z-toast max-w-sm rounded-md border bg-background p-3 text-sm text-destructive shadow-lg">
      Could not create the page. Please try again.
    </p>
  );
}
