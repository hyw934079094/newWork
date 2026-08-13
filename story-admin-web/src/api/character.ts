import axios from 'axios';

export interface CharacterItem {
  id?: number;
  code?: string;
  name: string;
  alias: string | null;
  gender: string | null;
  ageStage: string | null;
  race: string | null;
  occupation: string | null;
  publicIntro: string | null;
  internalNote: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export type CharacterPayload = Omit<CharacterItem, 'id' | 'code' | 'createdAt' | 'updatedAt'>;

const client = axios.create({ baseURL: '/api' });

export async function listCharacters(): Promise<CharacterItem[]> {
  const { data } = await client.get<CharacterItem[]>('/characters');
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
