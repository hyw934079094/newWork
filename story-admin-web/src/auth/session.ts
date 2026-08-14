import { ref } from 'vue';
import { changePassword as changePasswordApi, fetchMe, login as loginApi, logout as logoutApi, type AuthUser } from '../api/auth';

const currentUser = ref<AuthUser | null>(null);
let mePromise: Promise<AuthUser | null> | null = null;

export function useAuthState() {
  return { currentUser };
}

export async function ensureAuth(force = false): Promise<AuthUser | null> {
  if (!force && currentUser.value) {
    return currentUser.value;
  }
  if (!force && mePromise) {
    return mePromise;
  }
  mePromise = fetchMe()
    .then((user) => {
      currentUser.value = user;
      return user;
    })
    .catch(() => {
      currentUser.value = null;
      return null;
    })
    .finally(() => {
      mePromise = null;
    });
  return mePromise;
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const user = await loginApi(username, password);
  currentUser.value = user;
  return user;
}

export async function logout(): Promise<void> {
  try {
    await logoutApi();
  } finally {
    currentUser.value = null;
  }
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await changePasswordApi(currentPassword, newPassword);
}

export function clearAuth() {
  currentUser.value = null;
}
