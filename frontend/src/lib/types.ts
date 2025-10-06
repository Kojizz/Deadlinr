/**
 * API DTO types are generated from the backend OpenAPI spec into api.d.ts.
 * Regenerate whenever the backend changes:
 *
 *   npm run api:generate   (backend must be running on :8080)
 *
 * Hand-written API types are gone; the aliases below keep call sites stable
 * and paper over two spec gaps: JsonNode content (typed as Record<string,
 * never> by the generator) and the Role union (inlined on UserResponse).
 */
import type { components } from "./api.d";

export type User = components["schemas"]["UserResponse"];
export type AuthResponse = components["schemas"]["AuthResponse"];
export type Page = Omit<components["schemas"]["PageResponse"], "content"> & {
  /** BlockNote document JSON. */
  content?: unknown;
};
export type PageTreeNode = Omit<components["schemas"]["PageTreeNode"], "children" | "kind" | "childCount" | "hasDueDate"> & { children: PageTreeNode[]; kind?: string; childCount?: number; hasDueDate?: boolean; };
export type Role = NonNullable<components["schemas"]["UserResponse"]["role"]>;
export type PageMoveRequest = components["schemas"]["PageMoveRequest"];
export type PageUpdateRequest = components["schemas"]["PageUpdateRequest"];
export type PageRequest = components["schemas"]["PageRequest"];
export type SearchResult = components["schemas"]["SearchResult"];

