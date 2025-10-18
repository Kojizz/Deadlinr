import type { Metadata } from "next";
import { ThemeProvider } from "@/components/theme-provider";
import { QueryProvider } from "@/components/query-provider";
import { AuthProvider } from "@/hooks/use-auth";
import "./globals.css";
import { ToastProvider } from "@/components/ui/workspace";

export const metadata: Metadata = {
  title: "Deadlinr",
  description: "University workspace — pages, deadlines, calendar, reminders",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <ThemeProvider>
          <QueryProvider>
            <AuthProvider><ToastProvider>{children}</ToastProvider></AuthProvider>
          </QueryProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
