import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import api, { getErrorCode, getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import AuthLayout from '../features/auth/AuthLayout';

function getSafeNextPath(rawNext) {
  if (!rawNext || !rawNext.startsWith('/') || rawNext.startsWith('//')) {
    return '/dashboard';
  }
  return rawNext;
}

export default function LoginPage() {
  const { user, isLoggedIn, login } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resendNotice, setResendNotice] = useState('');
  const [resending, setResending] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const nextPath = getSafeNextPath(searchParams.get('next'));

  if (isLoggedIn) {
    return <Navigate to={user.mustChangePassword ? '/change-password' : nextPath} replace />;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError('');
    setResendNotice('');
    setNeedsVerification(false);

    const errors = {};
    if (!email.trim()) errors.email = 'Please enter your email address.';
    if (!password) errors.password = 'Please enter your password.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const loggedInUser = await login(email.trim(), password);
      if (loggedInUser.mustChangePassword) {
        navigate('/change-password', { replace: true });
      } else {
        navigate(nextPath, { replace: true });
      }
    } catch (err) {
      setFormError(getErrorMessage(err, 'Email or password is wrong.'));
      if (getErrorCode(err) === 'EMAIL_NOT_VERIFIED') {
        setNeedsVerification(true);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setResending(true);
    setResendNotice('');
    try {
      const res = await api.post('/auth/resend-verification', { email: email.trim() });
      setResendNotice(res.data.message);
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthLayout
      title="Log in to ScheduliX"
      subtitle="Sign in with your official email address."
      footer={
        <>
          New department?{' '}
          <Link to="/register" className="font-medium text-blue-700 hover:underline">
            Register here
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700 space-y-2">
            <p>{formError}</p>
            {needsVerification && (
              <button
                type="button"
                onClick={handleResend}
                disabled={resending}
                className="inline-flex items-center gap-1.5 text-xs font-medium text-red-800 underline hover:text-red-900 disabled:opacity-60"
              >
                {resending ? 'Sending...' : 'Send confirmation email again'}
              </button>
            )}
          </div>
        )}

        {resendNotice && (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-sm text-emerald-800">
            {resendNotice}
          </div>
        )}

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="name@college.edu"
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
          {fieldErrors.email && <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>}
        </div>

        <div>
          <div className="flex items-center justify-between">
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Password
            </label>
            <Link to="/forgot-password" className="text-xs font-medium text-blue-700 hover:underline">
              Forgot password?
            </Link>
          </div>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
          {fieldErrors.password && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-blue-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
        >
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          <span>{submitting ? 'Logging in...' : 'Log in'}</span>
        </button>
      </form>
    </AuthLayout>
  );
}