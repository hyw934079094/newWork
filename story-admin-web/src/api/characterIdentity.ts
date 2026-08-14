import http from './http';

export interface IdentityMemberView {
  characterId: number;
  code: string;
  name: string;
  formLabel: string | null;
  assetCount: number;
}

export interface IdentityAssetView {
  assetId: number;
  displayName: string;
  contentUrl: string;
  contentType: string | null;
}

export interface IdentityDetail {
  id: number;
  code: string;
  name: string;
  storyName: string | null;
  publicIntro: string | null;
  internalNote: string | null;
  createdAt?: string;
  updatedAt?: string;
  memberCount: number;
  members: IdentityMemberView[];
  assets: IdentityAssetView[];
}

export interface IdentityUpsertPayload {
  name: string;
  storyName: string | null;
  publicIntro: string | null;
  internalNote: string | null;
}

export interface IdentityMemberPayload {
  characterId: number;
  formLabel: string | null;
  sortOrder: number | null;
}



export async function listIdentities(): Promise<IdentityDetail[]> {
  const { data } = await http.get<IdentityDetail[]>('/character-identities');
  return data;
}

export async function getIdentity(id: number): Promise<IdentityDetail> {
  const { data } = await http.get<IdentityDetail>(`/character-identities/${id}`);
  return data;
}

export async function createIdentity(body: IdentityUpsertPayload): Promise<IdentityDetail> {
  const { data } = await http.post<IdentityDetail>('/character-identities', body);
  return data;
}

export async function updateIdentity(
  id: number,
  body: IdentityUpsertPayload,
): Promise<IdentityDetail> {
  const { data } = await http.put<IdentityDetail>(`/character-identities/${id}`, body);
  return data;
}

export async function removeIdentity(id: number): Promise<void> {
  await http.delete(`/character-identities/${id}`);
}

export async function setIdentityMembers(
  id: number,
  members: IdentityMemberPayload[],
): Promise<IdentityDetail> {
  const { data } = await http.put<IdentityDetail>(
    `/character-identities/${id}/members`,
    members,
  );
  return data;
}

export async function setIdentityAssets(
  id: number,
  assetIds: number[],
): Promise<IdentityDetail> {
  const { data } = await http.put<IdentityDetail>(`/character-identities/${id}/assets`, {
    assetIds,
  });
  return data;
}
