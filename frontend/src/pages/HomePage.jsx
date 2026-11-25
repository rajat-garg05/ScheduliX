import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/authContext';

export default function HomePage() {
  const { isLoggedIn } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center px-4 text-center">
      <h1 className="text-3xl font-semibold text-slate-900">ScheduliX</h1>
      <p className="mt-2 text-slate-600 max-w-md">
        Public Home page placeholder. The complete Home page is built in Milestone 5.
      </p>
      <div className="mt-6 flex gap-3">
        {isLoggedIn ? (
          <Link
            to="/dashboard"
            className="rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
          >
            Go to my dashboard
          </Link>
        ) : (
          <>
            <Link
              to="/login"
              className="rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
            >
              Login
            </Link>
            <Link
              to="/register"
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Register
            </Link>
          </>
        )}
      </div>
    </div>
  );
}