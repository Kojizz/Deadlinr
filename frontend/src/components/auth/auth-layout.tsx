import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { BrandLogo } from "@/components/brand-logo";
import { Button } from "@/components/ui/button";
import { SocialAuth } from "@/components/auth/social-auth";

export function AuthLayout({
  mode,
  title,
  description,
  children,
}: {
  mode: "login" | "register";
  title: string;
  description: string;
  children: ReactNode;
}) {
  const isLogin = mode === "login";

  return (
    <main className="grid min-h-dvh bg-background lg:grid-cols-2">
      <aside className="relative hidden min-w-0 flex-col justify-between overflow-hidden border-r bg-card p-10 lg:flex xl:p-14">
        <BrandLogo className="relative z-10 w-40" />
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 h-full w-full text-muted-foreground/20"
          viewBox="0 0 720 1000"
          preserveAspectRatio="xMidYMid slice"
          fill="none"
        >
          {Array.from({ length: 18 }, (_, index) => (
            <path
              key={index}
              d={`M -160 ${280 + index * 19} C 140 ${460 + index * 11}, 480 ${240 + index * 21}, 860 ${870 + index * 16}`}
              stroke="currentColor"
              strokeWidth="1"
            />
          ))}
        </svg>
        <div className="relative z-10 max-w-md py-8">
          <p className="mb-4 text-xs font-medium uppercase tracking-widest text-muted-foreground">
            Your student workspace
          </p>
          <h2 className="text-4xl font-semibold leading-tight tracking-tight xl:text-5xl">
            Make room for{" "}<br />what matters.
          </h2>
          <p className="mt-5 max-w-sm text-base leading-relaxed text-muted-foreground">
            Keep your tasks, pages, and deadlines together. Plan your next step with Deadlinr.
          </p>
        </div>
      </aside>

      <section aria-labelledby="auth-heading" className="flex min-w-0 flex-col px-6 py-6 sm:px-10 lg:px-12 lg:py-8">
        <header className="flex min-h-11 items-center justify-between gap-3">
          <BrandLogo className="w-28 lg:hidden" />
          {!isLogin && <Button asChild variant="ghost" className="ml-auto h-11 gap-1.5 px-3 text-muted-foreground">
            <Link href="/login">
              Sign in
              <ArrowUpRight aria-hidden="true" />
            </Link>
          </Button>}
        </header>

        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10 lg:py-8">
          <div className="mb-6">
            <h1 id="auth-heading" className="text-3xl font-semibold tracking-tight">{title}</h1>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{description}</p>
          </div>

          <SocialAuth />

          <div className="my-5 flex items-center gap-3" aria-hidden="true">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs text-muted-foreground">or continue with email</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          {children}

          <p className="mt-6 text-center text-sm text-muted-foreground">
            {isLogin ? "New to Deadlinr? " : "Already have an account? "}
            <Link
              href={isLogin ? "/register" : "/login"}
              className="inline-flex min-h-11 items-center font-medium text-foreground underline decoration-border underline-offset-4 hover:decoration-foreground"
            >
              {isLogin ? "Create one" : "Sign in"}
            </Link>
          </p>
        </div>
      </section>
    </main>
  );
}
