import http from './http';

export interface AssetCategoryItem {
  id: number;
  code: string;
  name: string;
  sortOrder: number;
  systemPreset: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CategoryCreatePayload {
  code: string;
  name: string;
  sortOrder?: number;
}

export interface CategoryUpdatePayload {
  name: string;
  sortOrder?: number;
}



export async function listCategories(): Promise<AssetCategoryItem[]> {
  const { data } = await http.get<AssetCategoryItem[]>('/categories');
  return data;
}

export async function createCategory(body: CategoryCreatePayload): Promise<AssetCategoryItem> {
  const { data } = await http.post<AssetCategoryItem>('/categories', body);
  return data;
}

export async function updateCategory(
  id: number,
  body: CategoryUpdatePayload,
): Promise<AssetCategoryItem> {
  const { data } = await http.put<AssetCategoryItem>(`/categories/${id}`, body);
  return data;
}

export async function deleteCategory(id: number): Promise<void> {
  await http.delete(`/categories/${id}`);
}
