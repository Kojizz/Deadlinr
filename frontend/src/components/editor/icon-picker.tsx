"use client";

import { Smile } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";

const EMOJIS = [
  "📄", "📝", "🗒️", "📚", "🎓", "✏️", "📌", "📎", "🗂️", "📁",
  "🧠", "💡", "⚡", "🔥", "⭐", "✅", "🎯", "🚀", "⏰", "📅",
  "🗓️", "📊", "📈", "🧮", "🔬", "🧪", "💻", "🖥️", "⌨️", "🐛",
  "🎨", "🎵", "🎬", "📷", "🌱", "🌿", "🍀", "🌍", "🌈", "☀️",
  "🌙", "❄️", "🌊", "🏔️", "🏠", "🏛️", "🎪", "🧭", "🗺️", "🧩",
  "♟️", "🎲", "🏀", "⚽", "🎾", "🏋️", "🧘", "☕", "🍕", "🍎",
];

interface IconPickerProps {
  value: string | null;
  onChange: (emoji: string | null) => void;
  align?: "start" | "center" | "end";
}

export function IconPicker({ value, onChange, align = "center" }: IconPickerProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label="Change icon"
          className={cn(
            "flex h-16 w-16 shrink-0 items-center justify-center rounded-xl text-3xl transition-colors",
            value
              ? "hover:bg-accent"
              : "border border-dashed text-muted-foreground hover:bg-accent",
          )}
        >
          {value ? (
            value
          ) : (
            <Smile className="h-5 w-5" />
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent align={align} className="w-72">
        <div className="grid grid-cols-6 gap-1">
          {EMOJIS.map((emoji) => (
            <button
              key={emoji}
              type="button"
              onClick={() => onChange(emoji)}
              className={cn(
                "flex h-9 w-9 items-center justify-center rounded-md text-xl transition-colors hover:bg-accent",
                value === emoji && "bg-accent",
              )}
              aria-label={`Use icon ${emoji}`}
            >
              {emoji}
            </button>
          ))}
        </div>
        {value && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="mt-3 w-full rounded-md border px-2 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            Remove icon
          </button>
        )}
      </PopoverContent>
    </Popover>
  );
}
