"use client";

import { WorkspaceNav } from "@/components/workspace-nav";
import { PlannerHost } from "@/components/planner-host";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AppSidebar } from "@/components/app-sidebar";
import { Topbar } from "@/components/topbar";
import { PaletteHost } from "@/components/palette-host";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";
import { PageCreationFeedback } from "@/components/page-creation-feedback";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
  }, [loading, user, router]);

  if (loading) {
    return (
      <div className="isolate flex h-screen overflow-hidden">
        <div className="z-sidebar hidden shrink-0 md:block"><AppSidebar /></div>
        <div className="isolate flex min-w-0 flex-1 flex-col">
          <Topbar />
          <main className="flex-1 overflow-y-auto">{children}</main>
        </div>
        <WorkspaceNav mobile />
        <PaletteHost />
        <PlannerHost />
        <PageCreationFeedback />
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="isolate flex h-screen overflow-hidden">
      <div className="z-sidebar hidden shrink-0 md:block">
        <AppSidebar />
      </div>
      <div className="isolate flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
      <WorkspaceNav mobile />
      <PaletteHost />
      <PlannerHost />
      <PageCreationFeedback />
    </div>
  );
}
