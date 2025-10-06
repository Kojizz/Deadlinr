"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import { createContext, useContext, useEffect, useState } from "react";

export const ACCENTS = ["indigo", "blue", "emerald", "rose", "amber"] as const;
export type Accent = (typeof ACCENTS)[number];

const ACCENT_KEY = "deadlinr-accent";

interface AccentContextValue {
  density: "comfortable" | "compact";
  setDensity: (value: "comfortable" | "compact") => void;
  accent: Accent;
  setAccent: (accent: Accent) => void;
}

const AccentContext = createContext<AccentContextValue>({
  density: "comfortable", setDensity: () => {},
  accent: "indigo",
  setAccent: () => {},
});

export function useAccent() {
  return useContext(AccentContext);
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [density, setDensity] = useState<"comfortable" | "compact">("comfortable");
  const [accent, setAccent] = useState<Accent>("indigo");

  useEffect(() => {
    setDensity(localStorage.getItem("deadlinr-density") === "compact" ? "compact" : "comfortable");
    const stored = localStorage.getItem(ACCENT_KEY) as Accent | null;
    if (stored && ACCENTS.includes(stored)) setAccent(stored);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.accent = accent;
    localStorage.setItem(ACCENT_KEY, accent);
  }, [accent]);

  useEffect(() => { document.documentElement.dataset.density = density; localStorage.setItem("deadlinr-density", density); }, [density]);
  return (
    <AccentContext.Provider value={{ accent, setAccent, density, setDensity }}>
      <NextThemesProvider
        attribute="class"
        defaultTheme="system"
        enableSystem
        disableTransitionOnChange
      >
        {children}
      </NextThemesProvider>
    </AccentContext.Provider>
  );
}
