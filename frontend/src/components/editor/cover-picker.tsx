"use client";

import { COVER_GRADIENTS, COVER_SOLIDS } from "@/lib/covers";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";

interface CoverPickerProps {
  value: string | null;
  onChange: (token: string | null) => void;
  align?: "start" | "center" | "end";
  triggerClassName?: string;
  triggerChildren?: React.ReactNode;
}

export function CoverPicker({
  value,
  onChange,
  align = "center",
  triggerClassName,
  triggerChildren,
}: CoverPickerProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" className={triggerClassName} aria-label="Change cover">
          {triggerChildren}
        </button>
      </PopoverTrigger>
      <PopoverContent align={align} className="w-72">
        <p className="mb-2 text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Gradients
        </p>
        <div className="grid grid-cols-4 gap-2">
          {COVER_GRADIENTS.map((cover) => (
            <button
              key={cover.id}
              type="button"
              onClick={() => onChange(cover.id)}
              aria-label={`Use ${cover.label} cover`}
              className={cn(
                "h-12 rounded-lg transition-transform hover:scale-[1.04]",
                value === cover.id && "ring-2 ring-ring ring-offset-2 ring-offset-popover",
              )}
              style={{ background: cover.css }}
            />
          ))}
        </div>
        <p className="mb-2 mt-4 text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Solid
        </p>
        <div className="grid grid-cols-4 gap-2">
          {COVER_SOLIDS.map((cover) => (
            <button
              key={cover.id}
              type="button"
              onClick={() => onChange(cover.id)}
              aria-label={`Use ${cover.label} cover`}
              className={cn(
                "h-12 rounded-lg transition-transform hover:scale-[1.04]",
                value === cover.id && "ring-2 ring-ring ring-offset-2 ring-offset-popover",
              )}
              style={{ background: cover.css }}
            />
          ))}
        </div>
        {value && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="mt-4 w-full rounded-md border px-2 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            Remove cover
          </button>
        )}
      </PopoverContent>
    </Popover>
  );
}
