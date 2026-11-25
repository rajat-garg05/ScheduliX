import { createContext, useContext, useState } from 'react';

const STORAGE_KEY = 'schedulix_placeholder_user';
const AuthContext = createContext(null);

function loadStoredUser() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children, initialUser }) {
  const [user, setUser] = useState(() =>
    initialUser !== undefined ? initialUser : loadStoredUser()
  );

  // Replaced with real JWT login in Prompt 2.
  function loginAsPlaceholder(role = 'HOD') {
    const nextUser =
      role === 'TEACHER'
        ? { displayName: 'Prof. Sharma', role: 'TEACHER' }
        : { displayName: 'HOD of Computer Science', role: 'HOD' };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextUser));
    setUser(nextUser);
  }

  function logout() {
    sessionStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }

  const value = {
    user,
    isLoggedIn: Boolean(user),
    loginAsPlaceholder,
    logout,
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