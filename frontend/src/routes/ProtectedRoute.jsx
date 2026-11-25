import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/authContext';

export default function ProtectedRoute() {
  const { isLoggedIn } = useAuth();
  const location = useLocation();

  if (!isLoggedIn) {
    const requestedPath = `${location.pathname}${location.search}`;
    const nextParam = encodeURIComponent(requestedPath);
    return <Navigate to={`/login?next=${nextParam}`} replace />;
  }

  return <Outlet />;
}