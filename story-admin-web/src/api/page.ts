import http from './http';

export interface PageItem {
  id?: number;
  arcId?: number;
  title: string;
  sortOrder?: number;
  contentJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type PageCreatePayload = {
  title: string;
  sortOrder?: number | null;
};

export type PageUpdatePayload = {
  title: string;
  contentJson: string;
  sortOrder?: number | null;
};



export function toContentJsonString(contentJson: unknown): string {
  if (typeof contentJson === 'string') {
    return contentJson.trim() ? contentJson : '[]';
  }
  if (contentJson == null) {
    return '[]';
  }
  return JSON.stringify(contentJson);
}

export async function listPages(arcId: number): Promise<PageItem[]> {
  const { data } = await http.get<PageItem[]>(`/arcs/${arcId}/pages`);
  return data;
}

export async function getPage(id: number): Promise<PageItem> {
  const { data } = await http.get<PageItem>(`/pages/${id}`);
  return data;
}

export async function createPage(arcId: number, body: PageCreatePayload): Promise<PageItem> {
  const { data } = await http.post<PageItem>(`/arcs/${arcId}/pages`, {
    title: body.title,
    sortOrder: body.sortOrder ?? undefined,
  });
  return data;
}

export async function updatePage(id: number, body: PageUpdatePayload): Promise<PageItem> {
  const { data } = await http.put<PageItem>(`/pages/${id}`, {
    title: body.title,
    contentJson: toContentJsonString(body.contentJson),
    sortOrder: body.sortOrder ?? undefined,
  });
  return data;
}

export async function deletePage(id: number): Promise<void> {
  await http.delete(`/pages/${id}`);
}

export async function reorderPages(arcId: number, orderedIds: number[]): Promise<void> {
  await http.put(`/arcs/${arcId}/pages/reorder`, { orderedIds });
}
