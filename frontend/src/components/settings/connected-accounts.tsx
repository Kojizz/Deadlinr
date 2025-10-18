"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { useToast } from "@/components/ui/workspace";
import { oauthProviders, providerNames, type OAuthAvailability, type OAuthProvider } from "@/lib/oauth";

type Accounts = { linked: OAuthAvailability; availability: OAuthAvailability; passwordLoginEnabled: boolean };

export function ConnectedAccounts() {
  const [busy, setBusy] = useState<OAuthProvider | null>(null);
  const toast = useToast();
  const query = useQuery({
    queryKey: ["auth", "oauth", "accounts"],
    queryFn: async () => {
      const data = await api.get<Accounts>("/auth/oauth2/accounts");
      if (typeof data?.passwordLoginEnabled !== "boolean" || oauthProviders.some(provider =>
        typeof data.linked?.[provider] !== "boolean" || typeof data.availability?.[provider] !== "boolean")) throw new Error("Could not load connected accounts.");
      return data;
    },
    staleTime: 60_000,
    retry: false,
  });

  async function connect(provider: OAuthProvider) {
    if (busy) return;
    setBusy(provider);
    try {
      const { ticket } = await api.post<{ ticket: string }>(`/auth/oauth2/accounts/${provider}/link`, {});
      if (!/^[A-Za-z0-9_-]{43}$/.test(ticket)) throw new Error("Could not start account connection.");
      window.location.assign(`/api/auth/oauth2/authorization/${provider}?link=${encodeURIComponent(ticket)}`);
    } catch (error) {
      toast({ message: error instanceof Error ? error.message : "Could not connect account." });
      setBusy(null);
    }
  }

  async function disconnect(provider: OAuthProvider) {
    if (busy) return;
    setBusy(provider);
    try {
      await api.del(`/auth/oauth2/accounts/${provider}`);
      await query.refetch();
      toast({ message: `${providerNames[provider]} disconnected` });
    } catch (error) {
      toast({ message: error instanceof Error ? error.message : "Could not disconnect account." });
    } finally { setBusy(null); }
  }

  const onlySocialLogin = query.data && !query.data.passwordLoginEnabled && oauthProviders.filter(provider => query.data!.linked[provider]).length === 1;

  return (
    <section aria-labelledby="connected-accounts-heading" className="panel mt-6 max-w-xl p-5">
      <h2 id="connected-accounts-heading" className="font-semibold">Connected accounts</h2>
      <p className="mt-1 text-sm text-muted-foreground">Connect Google or GitHub to sign in to your existing workspace.</p>
      {query.isPending ? <p role="status" className="mt-4 text-sm">Loading accounts…</p> : query.isError ? (
        <p role="alert" className="mt-4 text-sm">Could not load accounts. <Button variant="outline" className="ml-2 h-11" onClick={() => void query.refetch()}>Retry accounts</Button></p>
      ) : query.data && <>
        <div className="mt-4 divide-y">
          {oauthProviders.map(provider => (
            <div key={provider} className="flex min-h-16 items-center justify-between gap-3 py-2">
              <div><p className="text-sm font-medium">{providerNames[provider]}</p><p className="text-xs text-muted-foreground">{query.data!.linked[provider] ? "Connected" : query.data!.availability[provider] ? "Not connected" : "Not configured"}</p></div>
              <Button
                variant="outline"
                className="h-11"
                disabled={busy !== null || (query.data!.linked[provider] ? !!onlySocialLogin : !query.data!.availability[provider])}
                onClick={() => void (query.data!.linked[provider] ? disconnect(provider) : connect(provider))}
                aria-label={`${query.data!.linked[provider] ? "Disconnect" : "Connect"} ${providerNames[provider]}`}
              >
                {busy === provider ? "Working…" : query.data!.linked[provider] ? "Disconnect" : "Connect"}
              </Button>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs leading-relaxed text-muted-foreground">{query.data.passwordLoginEnabled ? "Your email and password still work with the local server." : "This account uses social sign-in. Keep at least one provider connected; a fresh sign-in requires internet."}</p>
      </>}
    </section>
  );
}
