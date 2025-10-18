import bundleAnalyzer from "@next/bundle-analyzer";
/** @type {import('next').NextConfig} */
const securityHeaders = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
  {
    key: "Content-Security-Policy",
    value: [
      "default-src 'self'",
      // 'unsafe-inline'/'unsafe-eval' keep dev HMR working; production should
      // move to a nonce-based policy before a public deployment.
      "script-src 'self' 'unsafe-eval' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "media-src 'self' blob:",
      "font-src 'self' data:",
      "connect-src 'self' http://localhost:8080 http://127.0.0.1:8080 ws://localhost:3000",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
    ].join("; "),
  },
];

const nextConfig = {
  reactStrictMode: true,
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  outputFileTracingRoot: new URL(".", import.meta.url).pathname,
  // Dev proxy: same-origin /api avoids CORS and keeps auth cookies first-party
  // (SameSite=Lax), so the app works from any hostname (localhost, 127.0.0.1, LAN).
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
  async headers() {
    return [{ source: "/(.*)", headers: securityHeaders }];
  },
};

export default bundleAnalyzer({ enabled: process.env.ANALYZE === "true", openAnalyzer: false })(nextConfig);
