"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, SESSION_EXPIRED_EVENT } from "@/lib/api";
import type { AuthResponse, User } from "@/lib/types";

interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  group?: string;
}

interface AuthContextValue {
  /** Legacy bearer token; null in cookie mode (the default). */
  token: string | null;
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const clearAuth = useCallback(() => {
    queryClient.clear();
    localStorage.removeItem("token");
    setToken(null);
    setUser(null);
  }, [queryClient]);

  // Set once a login/register succeeds, so a slow in-flight /users/me
  // rejection from page load can't wipe the session it raced with.
  const authenticatedRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    // Cookie mode: the session is validated by calling /users/me with
    // credentials; api.ts silently refreshes an expired access token.
    const legacy = localStorage.getItem("token");
    if (legacy) setToken(legacy);

    api
      .get<User>("/users/me")
      .then((me) => {
        if (!cancelled) {
          authenticatedRef.current = true;
          setUser(me);
        }
      })
      .catch(() => {
        if (!cancelled && !authenticatedRef.current) clearAuth();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [clearAuth]);

  useEffect(() => {
    const onExpired = () => clearAuth();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, [clearAuth]);

  const applyAuth = useCallback((data: AuthResponse) => {
    queryClient.clear();
    authenticatedRef.current = true;
    if (data.token) {
      localStorage.setItem("token", data.token);
      setToken(data.token);
    }
    setUser(data.user);
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const data = await api.post<AuthResponse>("/auth/login", { email, password });
    applyAuth(data);
  }, [applyAuth]);

  const register = useCallback(async (payload: RegisterPayload) => {
    const data = await api.post<AuthResponse>("/auth/register", payload);
    applyAuth(data);
  }, [applyAuth]);

  const logout = useCallback(async () => {
    try {
      await api.post<void>("/auth/logout");
    } catch {
      // Session may already be gone; clearing locally is what matters.
    }
    clearAuth();
  }, [clearAuth]);

  const logoutAll = useCallback(async () => {
    try {
      await api.post<void>("/auth/logout-all");
    } catch {
      // Flag may be off on the backend; still clear locally.
    }
    clearAuth();
  }, [clearAuth]);

  return (
    <AuthContext.Provider value={{ token, user, loading, login, register, logout, logoutAll }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
