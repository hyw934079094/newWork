import http from './http';

export interface AuthUser {
  username: string;
  displayName: string;
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const { data } = await http.post<AuthUser>('/auth/login', { username, password });
  return data;
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout');
}

export async function fetchMe(): Promise<AuthUser> {
  const { data } = await http.get<AuthUser>('/auth/me');
  return data;
}

export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await http.put('/auth/password', { currentPassword, newPassword });
}
