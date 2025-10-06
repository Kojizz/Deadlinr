"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Page, PageTreeNode } from "@/lib/types";

const treeKey = ["pages", "tree"] as const;
const createKey = ["pages", "create"] as const;
let nextTemporaryId = -1;

function insertNode(nodes: PageTreeNode[], parentId: number | null, node: PageTreeNode): PageTreeNode[] {
  if (parentId === null) return [...nodes, node];
  return nodes.map((item) => ({
    ...item,
    children: item.id === parentId
      ? [...item.children, node]
      : insertNode(item.children, parentId, node),
  }));
}

function removeNode(nodes: PageTreeNode[], id: number): PageTreeNode[] {
  return nodes.filter((node) => node.id !== id)
    .map((node) => ({ ...node, children: removeNode(node.children, id) }));
}

export function useCreatePage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: createKey,
    mutationFn: (parentId?: number) =>
      api.post<Page>("/pages", { title: "Untitled", parentId: parentId ?? null }),
    onMutate: async (parentId) => {
      await queryClient.cancelQueries({ queryKey: treeKey, exact: true });
      const id = nextTemporaryId--;
      const node: PageTreeNode = {
        id, title: "Untitled", icon: null, parentId: parentId ?? null,
        favorite: false, position: Number.MAX_SAFE_INTEGER, children: [], openTasks: 0,
      };
      queryClient.setQueryData<PageTreeNode[]>(treeKey, (tree = []) => insertNode(tree, parentId ?? null, node));
      return { id };
    },
    onSuccess: (created, _parentId, context) => {
      // Use the response immediately; a slow/failing tree refetch must not hide the real row.
      const node: PageTreeNode = { ...created, parentId: created.parentId ?? null, children: [], openTasks: 0 };
      queryClient.setQueryData<PageTreeNode[]>(treeKey, (tree = []) =>
        insertNode(removeNode(removeNode(tree, context.id), created.id), created.parentId ?? null, node));
      queryClient.setQueryData(["pages", created.id], created);
      void queryClient.invalidateQueries({ queryKey: ["search"] });
      void queryClient.invalidateQueries({ queryKey: ["workspace"] });
      void queryClient.invalidateQueries({ queryKey: ["pages","roots"] });
      router.push(`/pages/${created.id}`);
    },
    onError: (_error, _parentId, context) => {
      // Remove only this mutation's placeholder; preserve other concurrent creates.
      if (context) queryClient.setQueryData<PageTreeNode[]>(treeKey, (tree = []) => removeNode(tree, context.id));
    },
    onSettled: () => {
      // The current mutation still counts here. Refetch after the last create settles.
      if (queryClient.isMutating({ mutationKey: createKey }) === 1) {
        void queryClient.invalidateQueries({ queryKey: treeKey, exact: true });
      }
    },
  });
}



