import axios from 'axios';

export interface AiReferenceItem {
  id?: number;
  sessionId?: number;
  assetId: number;
  sortOrder?: number;
  purpose: string | null;
  note: string | null;
  strength: number | null;
}

export interface AiReferenceSession {
  id: number;
  name: string | null;
  items: AiReferenceItem[];
  createdAt?: string;
  updatedAt?: string;
}

export interface AiReferenceItemPayload {
  assetId: number;
  purpose: string | null;
  note: string | null;
  strength: number | null;
}

const client = axios.create({ baseURL: '/api' });

export async function getCurrentAiReference(): Promise<AiReferenceSession> {
  const { data } = await client.get<AiReferenceSession>('/ai-reference/current');
  return data;
}

export async function replaceCurrentAiReferenceItems(
  items: AiReferenceItemPayload[],
): Promise<AiReferenceSession> {
  const { data } = await client.put<AiReferenceSession>('/ai-reference/current/items', items);
  return data;
}
