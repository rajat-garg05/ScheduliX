import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import AuthLayout from '../features/auth/AuthLayout';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [departmentName, setDepartmentName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const errors = {};
    if (!departmentName.trim()) {
      errors.departmentName = 'Please enter your department name.';
    }
    if (!email.trim()) {
      errors.email = 'Please enter your email address.';
    }
    if (password.length < 10) {
      errors.password = 'Password must be at least 10 characters long.';
    }
    return errors;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError('');
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await api.post('/auth/register', {
        departmentName: departmentName.trim(),
        email: email.trim(),
        password,
      });
      navigate('/check-email', {
        replace: true,
        state: { email: email.trim().toLowerCase() },
      });
    } catch (err) {
      setFormError(getErrorMessage(err, 'Could not register your department. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Register your department"
      subtitle="Create the HOD account to set up teachers, subjects, and timetables."
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="font-medium text-blue-700 hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
            {formError}
          </div>
        )}

        <div>
          <label htmlFor="departmentName" className="block text-sm font-medium text-slate-700">
            Department name
          </label>
          <input
            id="departmentName"
            type="text"
            value={departmentName}
            onChange={(e) => setDepartmentName(e.target.value)}
            placeholder="e.g. Computer Science and Engineering"
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
          {fieldErrors.departmentName && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.departmentName}</p>
          )}
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Official email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="hod.cse@college.edu"
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
          {fieldErrors.email && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>
          )}
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
          <p className="mt-1 text-xs text-slate-500">Use at least 10 characters.</p>
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
          <span>{submitting ? 'Creating account...' : 'Register department'}</span>
        </button>
      </form>
    </AuthLayout>
  );
}