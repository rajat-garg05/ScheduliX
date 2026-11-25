import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, CheckCircle2 } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';

export default function ChangePasswordPage() {
  const { user, markPasswordChanged, logout } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isForced = Boolean(user?.mustChangePassword);

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError('');
    setSuccessMessage('');

    const errors = {};
    if (!currentPassword) {
      errors.currentPassword = 'Please enter your current password.';
    }
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
      const res = await api.post('/auth/change-password', {
        currentPassword,
        newPassword,
      });
      markPasswordChanged();
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');

      if (isForced) {
        navigate('/dashboard', { replace: true });
      } else {
        setSuccessMessage(res.data.message || 'Your password has been updated.');
      }
    } catch (err) {
      setFormError(getErrorMessage(err, 'Could not update your password.'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  const formContent = (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {isForced && (
        <div className="rounded-md border border-blue-200 bg-blue-50 px-3.5 py-2.5 text-sm text-blue-900">
          For your security, please replace the default password before opening ScheduliX.
        </div>
      )}

      {formError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          {formError}
        </div>
      )}

      {successMessage && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-sm text-emerald-800">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      <div>
        <label htmlFor="currentPassword" className="block text-sm font-medium text-slate-700">
          Current password
        </label>
        <input
          id="currentPassword"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
        />
        {fieldErrors.currentPassword && (
          <p className="mt-1 text-xs text-red-600">{fieldErrors.currentPassword}</p>
        )}
      </div>

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
        className="inline-flex items-center justify-center gap-2 rounded-md bg-blue-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
      >
        {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
        <span>{submitting ? 'Updating...' : 'Update password'}</span>
      </button>
    </form>
  );

  if (isForced) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col justify-center px-4 py-10">
        <div className="w-full max-w-md mx-auto">
          <div className="mb-6 flex items-center justify-between">
            <span className="text-xl font-semibold tracking-tight text-slate-900">
              Scheduli<span className="text-blue-700">X</span>
            </span>
            <button
              type="button"
              onClick={handleLogout}
              className="text-sm font-medium text-slate-600 hover:text-slate-900"
            >
              Log out
            </button>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-6 sm:p-7">
            <h1 className="text-xl font-semibold text-slate-900">Change your password</h1>
            <p className="mt-1 text-sm text-slate-600">Signed in as {user?.displayName}</p>
            <div className="mt-5">{formContent}</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-semibold text-slate-900">Change password</h1>
      <p className="mt-1 text-sm text-slate-600">
        Update the password you use to sign in to ScheduliX.
      </p>
      <div className="mt-6 rounded-lg border border-slate-200 bg-white p-6">{formContent}</div>
    </div>
  );
}