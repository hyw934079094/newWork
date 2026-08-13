import axios from 'axios';
import type { AssetItem } from './asset';

export interface CharacterItem {
  id?: number;
  code?: string;
  name: string;
  alias: string | null;
  gender: string | null;
  ageStage: string | null;
  race: string | null;
  occupation: string | null;
  storyName: string | null;
  publicIntro: string | null;
  internalNote: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export type CharacterPayload = Omit<CharacterItem, 'id' | 'code' | 'createdAt' | 'updatedAt'>;

export interface CharacterListQuery {
  q?: string;
  storyName?: string;
  gender?: string;
  ageStage?: string;
  race?: string;
  occupation?: string;
}

const client = axios.create({ baseURL: '/api' });

export async function listCharacters(query: CharacterListQuery = {}): Promise<CharacterItem[]> {
  const { data } = await client.get<CharacterItem[]>('/characters', { params: query });
  return data;
}

export async function getCharacter(id: number): Promise<CharacterItem> {
  const { data } = await client.get<CharacterItem>(`/characters/${id}`);
  return data;
}

export async function createCharacter(body: CharacterPayload): Promise<CharacterItem> {
  const { data } = await client.post<CharacterItem>('/characters', body);
  return data;
}

export async function updateCharacter(id: number, body: CharacterPayload): Promise<CharacterItem> {
  const { data } = await client.put<CharacterItem>(`/characters/${id}`, body);
  return data;
}

export async function deleteCharacter(id: number): Promise<void> {
  await client.delete(`/characters/${id}`);
}

export async function listCharacterAssets(id: number): Promise<AssetItem[]> {
  const { data } = await client.get<AssetItem[]>(`/characters/${id}/assets`);
  return data;
}

export async function replaceCharacterAssets(
  id: number,
  assetIds: number[],
): Promise<AssetItem[]> {
  const { data } = await client.put<AssetItem[]>(`/characters/${id}/assets`, { assetIds });
  return data;
}

export async function uploadCharacterAssets(
  id: number,
  files: File[],
  categoryId?: number,
): Promise<AssetItem[]> {
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  const { data } = await client.post<AssetItem[]>(`/characters/${id}/assets/upload`, form, {
    params: categoryId != null ? { categoryId } : undefined,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}
