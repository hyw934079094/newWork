import axios from 'axios';

export interface SysConfigItem {
  id?: number;
  configKey: string;
  configValue: string | null;
  remark: string | null;
  updatedAt?: string;
}

export interface ConfigUpsertBody {
  value: string;
  remark?: string;
}

const client = axios.create({ baseURL: '/api' });

export async function listConfigs(): Promise<SysConfigItem[]> {
  const { data } = await client.get<SysConfigItem[]>('/configs');
  return data;
}

export async function createConfig(key: string, value: string, remark?: string): Promise<SysConfigItem> {
  const { data } = await client.post<SysConfigItem>('/configs', { key, value, remark });
  return data;
}

export async function upsertConfig(key: string, body: ConfigUpsertBody): Promise<SysConfigItem> {
  const { data } = await client.put<SysConfigItem>(`/configs/${encodeURIComponent(key)}`, body);
  return data;
}

export async function deleteConfig(key: string): Promise<void> {
  await client.delete(`/configs/${encodeURIComponent(key)}`);
}
