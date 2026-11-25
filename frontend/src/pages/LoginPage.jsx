import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../features/auth/authContext';

function getSafeNextPath(rawNext) {
  if (!rawNext || !rawNext.startsWith('/') || rawNext.startsWith('//')) {
    return '/dashboard';
  }
  return rawNext;
}

export default function LoginPage() {
  const { loginAsPlaceholder } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const nextPath = getSafeNextPath(searchParams.get('next'));

  function handlePlaceholderLogin(role) {
    loginAsPlaceholder(role);
    navigate(nextPath, { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6">
        <Link to="/" className="text-lg font-semibold tracking-tight text-slate-900">
          Scheduli<span className="text-blue-700">X</span>
        </Link>
        <h1 className="mt-4 text-xl font-semibold text-slate-900">Sign in</h1>
        <p className="mt-1 text-sm text-slate-600">
          Temporary sign-in for development. Real email and password login will be added in Prompt 2.
        </p>

        <div className="mt-5 space-y-2.5">
          <button
            type="button"
            onClick={() => handlePlaceholderLogin('HOD')}
            className="w-full rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
          >
            Continue as HOD
          </button>
          <button
            type="button"
            onClick={() => handlePlaceholderLogin('TEACHER')}
            className="w-full rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Continue as Teacher
          </button>
        </div>

        <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
          <Link to="/register" className="hover:text-slate-800">
            Register department
          </Link>
          <Link to="/forgot-password" className="hover:text-slate-800">
            Forgot password
          </Link>
        </div>
      </div>
    </div>
  );
}