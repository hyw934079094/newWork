import http from './http';

export type AssetLinkType = 'NONE' | 'SERIES' | 'ARC' | 'CHARACTER';

export interface AssetItem {
  id: number;
  displayName: string;
  categoryId: number;
  seriesId: number | null;
  sortOrder: number;
  status: string;
  description: string | null;
  originalFilename: string | null;
  storagePath: string;
  contentType: string | null;
  width: number | null;
  height: number | null;
  sizeBytes: number | null;
  checksum: string | null;
  chapterRefPlaceholder: string | null;
  tagNames?: string[];
  characterIds?: number[];
  seriesIds?: number[];
  arcIds?: number[];
  linkType?: AssetLinkType;
  createdAt?: string;
  updatedAt?: string;
  deletedAt?: string | null;
}

export interface AssetUpdatePayload {
  displayName: string;
  description: string | null;
  chapterRefPlaceholder: string | null;
  tagNames: string[];
  linkType: AssetLinkType;
  characterIds?: number[];
  seriesIds?: number[];
  arcIds?: number[];
}

export function assetContentUrl(id: number, bust?: number | string): string {
  const base = `/api/assets/${id}/content`;
  return bust != null && bust !== '' ? `${base}?t=${bust}` : base;
}

export async function replaceAssetContent(id: number, file: File): Promise<AssetItem> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await http.post<AssetItem>(`/assets/${id}/content`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export async function listAssets(params: {
  categoryId?: number;
  status?: string;
  q?: string;
  characterFilter?: 'unlinked' | 'all';
  characterId?: number;
  linkType?: AssetLinkType | '';
  seriesId?: number;
  arcId?: number;
}): Promise<AssetItem[]> {
  const { data } = await http.get<AssetItem[]>('/assets', { params });
  return data;
}

export async function getAsset(id: number): Promise<AssetItem> {
  const { data } = await http.get<AssetItem>(`/assets/${id}`);
  return data;
}

export async function updateAsset(id: number, body: AssetUpdatePayload): Promise<AssetItem> {
  const { data } = await http.put<AssetItem>(`/assets/${id}`, body);
  return data;
}

export async function uploadAssets(
  categoryId: number,
  files: File[],
  link?: {
    linkType?: AssetLinkType;
    seriesIds?: number[];
    arcIds?: number[];
    characterIds?: number[];
  },
): Promise<AssetItem[]> {
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  const params: Record<string, unknown> = { categoryId };
  if (link?.linkType && link.linkType !== 'NONE') {
    params.linkType = link.linkType;
    if (link.seriesIds?.length) params.seriesIds = link.seriesIds;
    if (link.arcIds?.length) params.arcIds = link.arcIds;
    if (link.characterIds?.length) params.characterIds = link.characterIds;
  }
  const { data } = await http.post<AssetItem[]>('/assets/upload', form, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export async function reorderAssets(body: {
  categoryId: number;
  orderedIds: number[];
}): Promise<void> {
  await http.put('/assets/reorder', body);
}

export async function reorderAssetsByScope(body: {
  categoryId: number;
  scope: 'CHARACTER' | 'SERIES' | 'ARC' | 'UNLINKED';
  scopeId?: number | null;
  orderedIds: number[];
}): Promise<void> {
  await http.put('/assets/reorder-by-scope', body);
}

export async function moveAsset(
  id: number,
  body: { targetCategoryId: number; targetIndex: number },
): Promise<AssetItem> {
  const { data } = await http.put<AssetItem>(`/assets/${id}/move`, body);
  return data;
}

export async function recycleAsset(id: number): Promise<AssetItem> {
  const { data } = await http.post<AssetItem>(`/assets/${id}/recycle`);
  return data;
}

export async function restoreAsset(id: number): Promise<AssetItem> {
  const { data } = await http.post<AssetItem>(`/assets/${id}/restore`);
  return data;
}

export async function hardDeleteAsset(id: number): Promise<void> {
  await http.delete(`/assets/${id}`);
}
