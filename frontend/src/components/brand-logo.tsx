import Image from "next/image";
import { cn } from "@/lib/utils";

export function BrandLogo({ compact = false, className }: { compact?: boolean; className?: string }) {
  // Clip the supplied image's whitespace in the UI without changing the asset.
  const cropWidth = compact ? 345 : 1135;
  return (
    <span
      role="img"
      aria-label="Deadlinr"
      className={cn("relative block shrink-0 overflow-hidden mix-blend-multiply dark:mix-blend-screen", className)}
      style={{ aspectRatio: `${cropWidth} / 300` }}
    >
      <Image
        src="/brand/deadlinr-logo.png"
        alt=""
        aria-hidden="true"
        width={1254}
        height={1254}
        sizes={compact ? "96px" : "256px"}
        className="absolute max-w-none dark:invert"
        style={{ width: `${1254 / cropWidth * 100}%`, height: "auto", left: `${-55 / cropWidth * 100}%`, top: `${-475 / 300 * 100}%` }}
      />
    </span>
  );
}
