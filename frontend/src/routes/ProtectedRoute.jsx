import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '../features/auth/authContext';

export default function ProtectedRoute() {
  const { user, isLoggedIn, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex items-center gap-2.5 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading...</span>
        </div>
      </div>
    );
  }

  if (!isLoggedIn) {
    const requestedPath = `${location.pathname}${location.search}`;
    const nextParam = encodeURIComponent(requestedPath);
    return <Navigate to={`/login?next=${nextParam}`} replace />;
  }

  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  return <Outlet />;
}