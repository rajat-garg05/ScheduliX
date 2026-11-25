import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Loader2, CheckCircle2, AlertCircle } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import AuthLayout from '../features/auth/AuthLayout';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const resetKey = searchParams.get('token') || '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (!resetKey) {
    return (
      <AuthLayout title="Reset password">
        <div className="space-y-4">
          <div className="flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3.5 text-sm text-red-700">
            <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
            <span>This password reset link is incomplete. Please request a new one.</span>
          </div>
          <Link
            to="/forgot-password"
            className="block w-full text-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Request a new reset link
          </Link>
        </div>
      </AuthLayout>
    );
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError('');

    const errors = {};
    if (newPassword.length < 10) {
      errors.newPassword = 'Password must be at least 10 characters long.';
    }
    if (confirmPassword !== newPassword) {
      errors.confirmPassword = 'Passwords do not match.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await api.post('/auth/reset-password', {
        token: resetKey,
        newPassword,
      });
      setDone(true);
    } catch (err) {
      setFormError(getErrorMessage(err, 'Could not reset your password. Please request a new link.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="After resetting, you will be signed out of other devices."
    >
      {done ? (
        <div className="space-y-4">
          <div className="flex items-start gap-2.5 rounded-md border border-emerald-200 bg-emerald-50 p-3.5 text-sm text-emerald-800">
            <CheckCircle2 className="h-5 w-5 shrink-0 mt-0.5" />
            <span>Your password has been reset. You can now log in.</span>
          </div>
          <Link
            to="/login"
            className="block w-full text-center rounded-md bg-blue-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-800"
          >
            Go to login
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
              {formError}
            </div>
          )}

          <div>
            <label htmlFor="newPassword" className="block text-sm font-medium text-slate-700">
              New password
            </label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
            <p className="mt-1 text-xs text-slate-500">Use at least 10 characters.</p>
            {fieldErrors.newPassword && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.newPassword}</p>
            )}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700">
              Confirm new password
            </label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
            {fieldErrors.confirmPassword && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.confirmPassword}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-blue-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            <span>{submitting ? 'Saving...' : 'Save new password'}</span>
          </button>
        </form>
      )}
    </AuthLayout>
  );
}