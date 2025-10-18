"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { usePageAncestors } from "@/hooks/use-page-ancestors";

export function Breadcrumbs() {
  const pathname = usePathname();
  const params = useParams<{ id?: string }>();
  const pageId = pathname?.startsWith("/pages/") ? Number(params.id) : null;

  const { data: trail } = usePageAncestors(pageId);

  if (pageId == null || !Number.isFinite(pageId)) {
    return (
      <span className="text-sm font-medium text-muted-foreground">Workspace</span>
    );
  }

  return (
    <nav aria-label="Breadcrumb" className="hidden min-w-0 items-center md:flex">
      <ol className="flex min-w-0 items-center gap-1 text-sm">
        <li className="shrink-0">
          <Link
            href="/dashboard"
            className="rounded px-1 py-0.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            Workspace
          </Link>
        </li>
        {trail?.map((node) => (
          <li key={node.id} className="flex min-w-0 items-center gap-1">
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground/60" />
            {node.id === pageId ? (
              <span
                aria-current="page"
                className="truncate font-medium text-foreground"
              >
                {node.icon ? `${node.icon} ` : ""}
                {node.title || "Untitled"}
              </span>
            ) : (
              <Link
                href={`/pages/${node.id}`}
                className="truncate text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
              >
                {node.icon ? `${node.icon} ` : ""}
                {node.title || "Untitled"}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
