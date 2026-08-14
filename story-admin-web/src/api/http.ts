import axios, { type AxiosError } from 'axios';

const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

let handlingUnauthorized = false;

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    const isAuthCall =
      url.includes('/auth/login') || url.includes('/auth/me') || url.includes('/auth/logout');
    if (status === 401 && !isAuthCall && !handlingUnauthorized) {
      handlingUnauthorized = true;
      try {
        const { default: router } = await import('../router');
        const redirect = router.currentRoute.value.fullPath;
        await router.replace({
          name: 'login',
          query: redirect && redirect !== '/login' ? { redirect } : {},
        });
      } finally {
        handlingUnauthorized = false;
      }
    }
    return Promise.reject(error);
  },
);

export default http;
