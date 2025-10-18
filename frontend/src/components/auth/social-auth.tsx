"use client";

import { useEffect, useState } from "react";
import { Github } from "lucide-react";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { oauthErrorMessage, providerNames, oauthProviders, type OAuthAvailability, type OAuthProvider } from "@/lib/oauth";

export function SocialAuth() {
  const [availability, setAvailability] = useState<OAuthAvailability | null>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState<OAuthProvider | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    setError(oauthErrorMessage(new URLSearchParams(window.location.search).get("oauthError")));
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    api.get<OAuthAvailability>("/auth/oauth2/providers", controller.signal)
      .then(data => {
        if (typeof data?.google !== "boolean" || typeof data?.github !== "boolean") throw new Error("Invalid provider response");
        if (!controller.signal.aborted) setAvailability(data);
      })
      .catch(() => { if (!controller.signal.aborted) setAvailability(null); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [retry]);

  function start(provider: OAuthProvider) {
    if (starting) return;
    if (!availability?.[provider]) {
      setError(`${providerNames[provider]} sign-in is not configured on this server. Continue with email or follow the OAuth setup guide.`);
      return;
    }
    setError(null);
    setStarting(provider);
    // Full navigation lets the server handle state, provider redirects and cookies.
    localStorage.removeItem("token");
    window.location.assign(`/api/auth/oauth2/authorization/${provider}`);
  }

  return (
    <div aria-label="Social sign-in" className="space-y-3">
      {oauthProviders.map(provider => (
        <Button
          key={provider}
          type="button"
          disabled={loading || starting !== null}
          aria-describedby="social-auth-hint"
          className="h-12 w-full bg-foreground text-background hover:bg-foreground/90 disabled:opacity-80"
          onClick={() => start(provider)}
        >
          {provider === "google" ? <span aria-hidden="true" className="text-lg font-semibold leading-none">G</span> : <Github aria-hidden="true" />}
          {starting === provider ? `Opening ${providerNames[provider]}…` : `Continue with ${providerNames[provider]}`}
        </Button>
      ))}
      <p id="social-auth-hint" aria-live="polite" className="min-h-10 text-xs leading-relaxed text-muted-foreground">
        {loading ? "Checking sign-in options…" : availability?.google || availability?.github
          ? "Social sign-in requires an internet connection. Email sign-in works with your local server."
          : availability ? "Google and GitHub need to be configured on this server. Email sign-in is available."
          : "Could not check social sign-in. Email sign-in is still available."}
      </p>
      {!loading && !availability && <Button type="button" variant="outline" className="h-11" onClick={() => setRetry(retry + 1)}>Retry sign-in options</Button>}
      {error && <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm">{error}</p>}
    </div>
  );
}
