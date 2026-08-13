import axios from 'axios';

export interface ComboMemberView {
  memberNo: number;
  assetId: number;
  displayName: string;
  contentUrl: string;
  contentType: string | null;
}

export interface ComboStepHoldView {
  stepIndex: number;
  holdSeconds: number;
}

export interface ComboDetail {
  id: number;
  name: string;
  playSequence: string;
  defaultIntervalSec: number;
  loopEnabled: boolean;
  remark: string | null;
  createdAt?: string;
  updatedAt?: string;
  members: ComboMemberView[];
  stepHolds: ComboStepHoldView[];
}

export interface ComboMemberPayload {
  assetId: number;
  memberNo: number;
}

export interface ComboStepHoldPayload {
  stepIndex: number;
  holdSeconds: number;
}

export interface ComboUpsertPayload {
  name: string;
  playSequence: string;
  defaultIntervalSec: number;
  loopEnabled: boolean;
  remark: string | null;
  members: ComboMemberPayload[];
  stepHolds: ComboStepHoldPayload[];
}

const client = axios.create({ baseURL: '/api' });

export async function listCombos(): Promise<ComboDetail[]> {
  const { data } = await client.get<ComboDetail[]>('/combos');
  return data;
}

export async function getCombo(id: number): Promise<ComboDetail> {
  const { data } = await client.get<ComboDetail>(`/combos/${id}`);
  return data;
}

export async function createCombo(body: ComboUpsertPayload): Promise<ComboDetail> {
  const { data } = await client.post<ComboDetail>('/combos', body);
  return data;
}

export async function updateCombo(id: number, body: ComboUpsertPayload): Promise<ComboDetail> {
  const { data } = await client.put<ComboDetail>(`/combos/${id}`, body);
  return data;
}

export async function removeCombo(id: number): Promise<void> {
  await client.delete(`/combos/${id}`);
}
