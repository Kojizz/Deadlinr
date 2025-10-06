export const oauthProviders = ["google", "github"] as const;
export type OAuthProvider = typeof oauthProviders[number];
export type OAuthAvailability = Record<OAuthProvider, boolean>;
export const providerNames: Record<OAuthProvider, string> = { google: "Google", github: "GitHub" };

export function oauthErrorMessage(code: string | null): string | null {
  if (!code) return null;
  const messages: Record<string, string> = {
    cancelled: "Sign-in was cancelled. You can try again or continue with email.",
    unavailable: "This sign-in provider is not configured. Continue with email or enable it on the server.",
    expired: "Your sign-in request expired or was already used. Please try again.",
    email_conflict: "This email already has a workspace. Sign in with email, then connect this provider in Settings.",
    email_unverified: "Your provider did not supply a verified email address. Continue with email instead.",
    identity_conflict: "This provider account is already connected to another workspace.",
    session_changed: "Your local session changed. Sign in again before connecting an account.",
    failed: "Social sign-in could not be completed. Please try again or continue with email.",
  };
  return messages[code] ?? messages.failed;
}
