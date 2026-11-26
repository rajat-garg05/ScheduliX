import axios from 'axios';

let accessToken = null;
let onSessionRefreshed = null;
let onSessionExpired = null;
let refreshPromise = null;

export function setAccessToken(token) {
  accessToken = token || null;
}

export function getAccessToken() {
  return accessToken;
}

export function registerAuthHandlers({ onRefreshed, onExpired }) {
  onSessionRefreshed = onRefreshed;
  onSessionExpired = onExpired;
}

export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/auth/refresh', null, { withCredentials: true })
      .then((res) => res.data)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

const NO_REFRESH_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/refresh',
  '/auth/logout',
];

function shouldSkipRefresh(url = '') {
  return NO_REFRESH_PATHS.some((path) => url.includes(path));
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;

    if (status !== 401 || !original || original._retried || shouldSkipRefresh(original.url)) {
      return Promise.reject(error);
    }

    original._retried = true;

    try {
      const data = await refreshSession();
      setAccessToken(data.accessToken);
      if (onSessionRefreshed) {
        onSessionRefreshed(data.user);
      }

      original.headers.Authorization = `Bearer ${data.accessToken}`;
      return api(original);
    } catch (refreshError) {
      setAccessToken(null);
      if (onSessionExpired) {
        onSessionExpired();
      }
      return Promise.reject(refreshError);
    }
  }
);

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  return error?.response?.data?.message || fallback;
}

export function getErrorCode(error) {
  return error?.response?.data?.code || null;
}

export default api;