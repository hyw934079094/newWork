import http from './http';

export type SeriesStatus = 'DRAFT' | 'SERIALIZING' | 'COMPLETED' | 'PUBLISHED';

export interface SeriesItem {
  id?: number;
  code?: string;
  name: string;
  status: SeriesStatus;
  coverAssetId: number | null;
  summary: string | null;
  tags: string | null;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export type SeriesPayload = {
  name: string;
  status?: SeriesStatus | null;
  summary?: string | null;
  tags?: string | null;
  coverAssetId?: number | null;
};

export interface SeriesListQuery {
  q?: string;
  status?: SeriesStatus;
}



export async function listSeries(query: SeriesListQuery = {}): Promise<SeriesItem[]> {
  const { data } = await http.get<SeriesItem[]>('/series', { params: query });
  return data;
}

export async function getSeries(id: number): Promise<SeriesItem> {
  const { data } = await http.get<SeriesItem>(`/series/${id}`);
  return data;
}

export async function createSeries(body: SeriesPayload): Promise<SeriesItem> {
  const { data } = await http.post<SeriesItem>('/series', body);
  return data;
}

export async function updateSeries(id: number, body: SeriesPayload): Promise<SeriesItem> {
  const { data } = await http.put<SeriesItem>(`/series/${id}`, body);
  return data;
}

export async function deleteSeries(id: number): Promise<void> {
  await http.delete(`/series/${id}`);
}
