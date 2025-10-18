"use client";

import { WorkspaceNav } from "@/components/workspace-nav";
import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PanelLeft, Plus, Star } from "lucide-react";
import { PageTree } from "@/components/page-tree";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import type { Page } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useCreatePage } from "@/hooks/use-create-page";
import { BrandLogo } from "@/components/brand-logo";

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <span className="block px-2 pb-1 pt-4 text-xs font-medium uppercase tracking-wider text-muted-foreground">
      {children}
    </span>
  );
}

function FavoritesSection() {
  const { data } = useQuery({
    queryKey: ["pages", "favorites"],
    queryFn: () => api.get<Page[]>("/pages/favorites"),
  });
  if (!data || data.length === 0) return null;
  return (
    <div className="flex flex-col gap-0.5">
      <SectionLabel>Favorites</SectionLabel>
      {data.map((p) => (
        <div
          key={p.id}
          className="flex items-center gap-2 rounded-md px-2 py-1 text-sm text-muted-foreground hover:bg-accent/60 hover:text-foreground"
        >
          <Star className="h-3.5 w-3.5 shrink-0 fill-current text-amber-500" />
          <Link prefetch={false} href={"/pages/"+p.id} className="truncate">{p.icon} {p.title || "Untitled"}</Link>
        </div>
      ))}
    </div>
  );
}

export function AppSidebar({ className }: { className?: string }) {
  const [collapsed, setCollapsed] = useState(false);
  const createPage = useCreatePage();
  const pathname = usePathname();
  useEffect(() => { if (pathname.startsWith("/pages/")) setCollapsed(false); }, [pathname]);

  return (
    <div
      className={cn(
        "relative isolate flex h-full min-h-0 flex-col border-r bg-surface transition-[width] duration-200",
        collapsed ? "w-14" : "w-64",
        className,
      )}
    >
      <div
        className={cn(
          "flex shrink-0 items-center",
          collapsed ? "flex-col gap-1 py-2" : "h-12",
          collapsed ? "justify-center" : "justify-between px-2",
        )}
      >
        <BrandLogo compact={collapsed} className={collapsed ? "w-6" : "ml-1 w-32"} />
        <div className={cn("flex items-center", collapsed && "flex-col gap-1")}>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => createPage.mutate(undefined, { onSuccess: () => setCollapsed(false) })}
              aria-label="New page"
              title="New page (Ctrl+Alt+N)"
            >
              <Plus className="h-4 w-4" />
            </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={() => setCollapsed((c) => !c)}
            aria-label="Toggle sidebar"
          >
            <PanelLeft className="h-4 w-4" />
          </Button>
        </div>
      </div>
      <div className="border-b"/>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {collapsed ? (
          <div className="flex flex-col items-center gap-1 py-2">
            <Link prefetch={false} href="/tasks" aria-label="Tasks" className="btn !p-2">✓</Link>
            <Link prefetch={false} href="/calendar" aria-label="Calendar" className="btn !p-2">▦</Link>
          </div>
        ) : (
          <div className="pb-4">
            <WorkspaceNav />
            <PageTree />
            <FavoritesSection />

          </div>
        )}
      </div>
    </div>
  );
}





