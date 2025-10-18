export interface CoverPreset {
  id: string;
  label: string;
  css: string;
}

export const COVER_GRADIENTS: CoverPreset[] = [
  {
    id: "grad-aurora",
    label: "Aurora",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-sunset",
    label: "Sunset",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-ocean",
    label: "Ocean",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-forest",
    label: "Forest",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-ember",
    label: "Ember",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-rose",
    label: "Rose",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-slate",
    label: "Slate",
    css: "hsl(240 5% 90%)",
  },
  {
    id: "grad-ink",
    label: "Ink",
    css: "hsl(240 5% 90%)",
  },
];

export const COVER_SOLIDS: CoverPreset[] = [
  { id: "solid-indigo", label: "Indigo", css: "#6366f1" },
  { id: "solid-violet", label: "Violet", css: "#8b5cf6" },
  { id: "solid-sky", label: "Sky", css: "#0ea5e9" },
  { id: "solid-emerald", label: "Emerald", css: "#10b981" },
  { id: "solid-amber", label: "Amber", css: "#f59e0b" },
  { id: "solid-rose", label: "Rose", css: "#f43f5e" },
  { id: "solid-zinc", label: "Zinc", css: "#52525b" },
  { id: "solid-stone", label: "Stone", css: "#44403c" },
];

const ALL_COVERS = [...COVER_GRADIENTS, ...COVER_SOLIDS];

export function coverCss(token: string | null | undefined): string | null {
  if (!token) return null;
  return ALL_COVERS.find((c) => c.id === token)?.css ?? null;
}
