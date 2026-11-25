import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import AuthLayout from '../features/auth/AuthLayout';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const verificationKey = searchParams.get('token') || '';
  const hasTriggered = useRef(false);

  const [status, setStatus] = useState(verificationKey ? 'loading' : 'error');
  const [message, setMessage] = useState(
    verificationKey ? '' : 'This confirmation link is incomplete. Please request a new link.'
  );

  useEffect(() => {
    if (!verificationKey || hasTriggered.current) {
      return;
    }
    hasTriggered.current = true;

    api
      .post('/auth/verify-email', { token: verificationKey })
      .then((res) => {
        setStatus('success');
        setMessage(res.data.message || 'Your email is confirmed. You can now log in.');
      })
      .catch((err) => {
        setStatus('error');
        setMessage(getErrorMessage(err, 'This confirmation link is not valid or has expired.'));
      });
  }, [verificationKey]);

  return (
    <AuthLayout title="Email confirmation">
      {status === 'loading' && (
        <div className="flex items-center gap-2.5 text-sm text-slate-600 py-2">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Confirming your email...</span>
        </div>
      )}

      {status === 'success' && (
        <div className="space-y-4">
          <div className="flex items-start gap-2.5 rounded-md border border-emerald-200 bg-emerald-50 p-3.5 text-sm text-emerald-800">
            <CheckCircle2 className="h-5 w-5 shrink-0 mt-0.5" />
            <span>{message}</span>
          </div>
          <Link
            to="/login"
            className="block w-full text-center rounded-md bg-blue-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-800"
          >
            Go to login
          </Link>
        </div>
      )}

      {status === 'error' && (
        <div className="space-y-4">
          <div className="flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3.5 text-sm text-red-700">
            <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
            <span>{message}</span>
          </div>
          <Link
            to="/check-email"
            className="block w-full text-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Request a new confirmation email
          </Link>
        </div>
      )}
    </AuthLayout>
  );
}