import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import api, { registerAuthHandlers, setAccessToken } from '../../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children, initialUser }) {
  const hasInitialUser = initialUser !== undefined;
  const [user, setUser] = useState(hasInitialUser ? initialUser : null);
  const [loading, setLoading] = useState(!hasInitialUser);

  const handleSessionExpired = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    registerAuthHandlers({
      onRefreshed: (nextUser) => setUser(nextUser),
      onExpired: handleSessionExpired,
    });
  }, [handleSessionExpired]);

  useEffect(() => {
    if (hasInitialUser) {
      return;
    }

    let cancelled = false;
    api
      .post('/auth/refresh')
      .then((res) => {
        if (cancelled) return;
        setAccessToken(res.data.accessToken);
        setUser(res.data.user);
      })
      .catch(() => {
        if (cancelled) return;
        setAccessToken(null);
        setUser(null);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [hasInitialUser]);

  async function login(email, password) {
    const res = await api.post('/auth/login', { email, password });
    setAccessToken(res.data.accessToken);
    setUser(res.data.user);
    return res.data.user;
  }

  async function logout() {
    try {
      await api.post('/auth/logout');
    } catch {
      // Clear local state even if the network request fails.
    }
    setAccessToken(null);
    setUser(null);
  }

  function markPasswordChanged() {
    setUser((prev) => (prev ? { ...prev, mustChangePassword: false } : prev));
  }

  const value = {
    user,
    loading,
    isLoggedIn: Boolean(user),
    login,
    logout,
    markPasswordChanged,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}