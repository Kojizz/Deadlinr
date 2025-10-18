"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api";
import { AuthLayout } from "@/components/auth/auth-layout";
import { PasswordInput } from "@/components/auth/password-input";

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
    group: "",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const submitting = useRef(false);

  function onChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    setError("");
    setLoading(true);
    try {
      await register({
        fullName: form.fullName,
        email: form.email,
        password: form.password,
        group: form.group || undefined,
      });
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed");
    } finally {
      submitting.current = false;
      setLoading(false);
    }
  }

  return (
    <AuthLayout mode="register" title="Create your workspace" description="Bring your tasks, pages, and deadlines together.">
      <form
        onSubmit={onSubmit}
        className="space-y-4"
        aria-label="Create an account with email"
        aria-busy={loading}
      >
        {error && (
          <div id="auth-error" role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-foreground">
            {error}
          </div>
        )}
        <div className="space-y-2">
          <Label htmlFor="fullName">Full name</Label>
          <Input
            id="fullName"
            name="fullName"
            className="h-11"
            autoComplete="name"
            value={form.fullName}
            onChange={onChange}
            placeholder="Your name"
            disabled={loading}
            aria-describedby={error ? "auth-error" : undefined}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            name="email"
            type="email"
            className="h-11"
            autoComplete="email"
            value={form.email}
            onChange={onChange}
            placeholder="you@example.com"
            disabled={loading}
            aria-describedby={error ? "auth-error" : undefined}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <PasswordInput
            id="password"
            name="password"
            autoComplete="new-password"
            value={form.password}
            onChange={onChange}
            placeholder="At least 6 characters"
            minLength={6}
            disabled={loading}
            aria-describedby={error ? "auth-error" : undefined}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="group">
            Group <span className="font-normal text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id="group"
            name="group"
            className="h-11"
            value={form.group}
            onChange={onChange}
            placeholder="e.g. CS-101"
            disabled={loading}
          />
        </div>
        <Button type="submit" className="h-11 w-full" disabled={loading}>
          {loading ? "Creating account…" : "Create account"}
        </Button>
      </form>
    </AuthLayout>
  );
}
