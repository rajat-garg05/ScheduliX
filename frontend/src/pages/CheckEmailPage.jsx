import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MailCheck, Loader2 } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import AuthLayout from '../features/auth/AuthLayout';

export default function CheckEmailPage() {
  const location = useLocation();
  const initialEmail = location.state?.email || '';
  const [email, setEmail] = useState(initialEmail);
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  async function handleResend() {
    if (!email.trim()) {
      setError('Please enter your email address first.');
      return;
    }
    setError('');
    setNotice('');
    setSending(true);
    try {
      const res = await api.post('/auth/resend-verification', { email: email.trim() });
      setNotice(res.data.message);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSending(false);
    }
  }

  return (
    <AuthLayout
      title="Check your email"
      subtitle="We sent a confirmation link to activate your department account."
      footer={
        <Link to="/login" className="font-medium text-blue-700 hover:underline">
          Back to login
        </Link>
      }
    >
      <div className="flex items-start gap-3 rounded-md border border-blue-200 bg-blue-50 p-3.5 text-sm text-blue-900">
        <MailCheck className="h-5 w-5 shrink-0 text-blue-700 mt-0.5" />
        <div>
          {initialEmail ? (
            <p>
              Open the email sent to <span className="font-medium">{initialEmail}</span> and click
              the confirmation link. The link works for 24 hours.
            </p>
          ) : (
            <p>
              Open the confirmation email we sent and click the link inside. The link works for 24
              hours.
            </p>
          )}
        </div>
      </div>

      <div className="mt-5 pt-4 border-t border-slate-100 space-y-3">
        <p className="text-xs text-slate-600">Did not receive the email?</p>

        {!initialEmail && (
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Enter your registered email"
            className="block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
        )}

        {notice && (
          <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-md px-3 py-2">
            {notice}
          </p>
        )}
        {error && (
          <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={handleResend}
          disabled={sending}
          className="w-full inline-flex items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
        >
          {sending && <Loader2 className="h-4 w-4 animate-spin" />}
          <span>{sending ? 'Sending...' : 'Send confirmation email again'}</span>
        </button>
      </div>
    </AuthLayout>
  );
}