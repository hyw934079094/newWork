import axios from 'axios';

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

const client = axios.create({ baseURL: '/api' });

export async function listSeries(query: SeriesListQuery = {}): Promise<SeriesItem[]> {
  const { data } = await client.get<SeriesItem[]>('/series', { params: query });
  return data;
}

export async function getSeries(id: number): Promise<SeriesItem> {
  const { data } = await client.get<SeriesItem>(`/series/${id}`);
  return data;
}

export async function createSeries(body: SeriesPayload): Promise<SeriesItem> {
  const { data } = await client.post<SeriesItem>('/series', body);
  return data;
}

export async function updateSeries(id: number, body: SeriesPayload): Promise<SeriesItem> {
  const { data } = await client.put<SeriesItem>(`/series/${id}`, body);
  return data;
}

export async function deleteSeries(id: number): Promise<void> {
  await client.delete(`/series/${id}`);
}
