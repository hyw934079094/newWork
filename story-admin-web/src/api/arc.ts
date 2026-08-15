import http from './http';

export type ArcStatus = 'DRAFT' | 'WRITING' | 'FINALIZED';

export interface ArcItem {
  id?: number;
  seriesId?: number;
  code?: string;
  title: string;
  status: ArcStatus;
  coverAssetId: number | null;
  summary: string | null;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export type ArcPayload = {
  title: string;
  status?: ArcStatus | null;
  summary?: string | null;
  coverAssetId?: number | null;
};

export interface ArcListQuery {
  q?: string;
}



export async function listArcs(seriesId: number, query: ArcListQuery = {}): Promise<ArcItem[]> {
  const { data } = await http.get<ArcItem[]>(`/series/${seriesId}/arcs`, { params: query });
  return data;
}

export async function getArc(id: number): Promise<ArcItem> {
  const { data } = await http.get<ArcItem>(`/arcs/${id}`);
  return data;
}

export async function createArc(seriesId: number, body: ArcPayload): Promise<ArcItem> {
  const { data } = await http.post<ArcItem>(`/series/${seriesId}/arcs`, body);
  return data;
}

export async function updateArc(id: number, body: ArcPayload): Promise<ArcItem> {
  const { data } = await http.put<ArcItem>(`/arcs/${id}`, body);
  return data;
}

export async function deleteArc(id: number): Promise<void> {
  await http.delete(`/arcs/${id}`);
}

export function arcReadingStreamUrl(arcId: number): string {
  return `/api/arcs/${arcId}/reading-stream`;
}
