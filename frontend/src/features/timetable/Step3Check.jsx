import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ExternalLink,
  Loader2,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

export default function Step3Check({ draft, onBack, onContinue }) {
  const [result, setResult] = useState(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState('');

  async function runCheck() {
    setChecking(true);
    setError('');
    try {
      const res = await api.post(`/timetables/${draft.id}/check`);
      setResult(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not run checks right now.'));
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Step 3: Check before making</h2>
        <p className="mt-1 text-sm text-slate-600">
          Verify that the classes, semester plans, breaks, teachers, and shared labs can all fit
          together.
        </p>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          {error}
        </div>
      )}

      <div>
        <button
          type="button"
          disabled={checking}
          onClick={runCheck}
          className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
        >
          {checking ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <ShieldCheck className="h-4 w-4" />
          )}
          <span>{checking ? 'Checking...' : result ? 'Run check again' : 'Run check'}</span>
        </button>
      </div>

      {result && result.ok && (
        <div className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-600 mt-0.5" />
          <div>
            <p className="font-semibold">Everything looks good</p>
            <p className="mt-0.5 text-emerald-800">
              All classes, lecture counts, breaks, teachers, and shared labs passed the check. You
              can continue to review teachers.
            </p>
          </div>
        </div>
      )}

      {result && !result.ok && (
        <div className="space-y-2.5">
          <p className="text-sm font-medium text-red-700">
            Found {result.problems.length} problem{result.problems.length === 1 ? '' : 's'} to fix
            before continuing:
          </p>
          {result.problems.map((problem, idx) => (
            <div
              key={idx}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
            >
              <div className="flex items-start gap-2.5">
                <XCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
                <span>{problem.text}</span>
              </div>
              {problem.link && (
                <Link
                  to={problem.link}
                  className="inline-flex items-center gap-1 text-xs font-medium text-blue-700 hover:underline shrink-0"
                >
                  <span>Fix this</span>
                  <ExternalLink className="h-3.5 w-3.5" />
                </Link>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="pt-2 flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back</span>
        </button>
        <button
          type="button"
          disabled={!result?.ok}
          onClick={onContinue}
          className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-40"
        >
          <span>Continue to teachers</span>
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}