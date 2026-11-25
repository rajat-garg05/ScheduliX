import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  CalendarPlus,
  Check,
  Clock,
  Loader2,
  Play,
  Trash2,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';
import Step1Classes from '../features/timetable/Step1Classes';
import Step2Settings from '../features/timetable/Step2Settings';
import Step3Check from '../features/timetable/Step3Check';
import Step4Teachers from '../features/timetable/Step4Teachers';
import Step5FixedLectures from '../features/timetable/Step5FixedLectures';
import Step6Result from '../features/timetable/Step6Result';

const STEPS = [
  { number: 1, label: 'Classes' },
  { number: 2, label: 'Time settings' },
  { number: 3, label: 'Check' },
  { number: 4, label: 'Teachers' },
  { number: 5, label: 'Fixed lectures' },
  { number: 6, label: 'Result' },
];

export default function MakeTimetablePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [activeDraft, setActiveDraft] = useState(null);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [step, setStep] = useState(1);
  const [autoMakeResult, setAutoMakeResult] = useState(false);
  const [loadingDraftId, setLoadingDraftId] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [pageError, setPageError] = useState('');

  const draftsQuery = useQuery({
    queryKey: ['timetable-drafts'],
    queryFn: async () => {
      const res = await api.get('/timetables', { params: { status: 'DRAFT' } });
      return res.data || [];
    },
    enabled: isHod,
  });

  const deleteDraftMutation = useMutation({
    mutationFn: (id) => api.delete(`/timetables/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
      setDeleteTarget(null);
      setPageError('');
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Could not delete this unfinished timetable.'));
      setDeleteTarget(null);
    },
  });

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">Make Timetable</h1>
        <p className="mt-1.5 text-sm text-slate-600">Only the HOD can make timetables.</p>
      </div>
    );
  }

  function startNewWizard() {
    setActiveDraft(null);
    setStep(1);
    setAutoMakeResult(false);
    setWizardOpen(true);
    setPageError('');
  }

  async function continueDraft(draftId) {
    setLoadingDraftId(draftId);
    setPageError('');
    try {
      const res = await api.get(`/timetables/${draftId}`);
      setActiveDraft(res.data);
      setAutoMakeResult(false);
      setStep((res.data?.lectureCount || 0) > 0 ? 6 : 2);
      setWizardOpen(true);
    } catch (err) {
      setPageError(getErrorMessage(err, 'Could not open this unfinished timetable.'));
    } finally {
      setLoadingDraftId(null);
    }
  }

  function closeWizard() {
    setWizardOpen(false);
    setActiveDraft(null);
    setStep(1);
    setAutoMakeResult(false);
    queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
  }

  async function refreshDraft(id) {
    const res = await api.get(`/timetables/${id}`);
    setActiveDraft(res.data);
    return res.data;
  }

  const drafts = draftsQuery.data || [];

  if (wizardOpen) {
    return (
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <button
              type="button"
              onClick={closeWizard}
              className="inline-flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-slate-900"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to unfinished timetables</span>
            </button>
            <h1 className="mt-1 text-2xl font-semibold text-slate-900">
              {activeDraft ? activeDraft.name : 'New timetable'}
            </h1>
          </div>
        </div>

        {/* Step progress bar */}
        <ol className="grid grid-cols-2 sm:grid-cols-6 gap-2">
          {STEPS.map((item) => {
            const done = item.number < step;
            const current = item.number === step;
            const clickable = Boolean(activeDraft) && item.number <= step;
            return (
              <li key={item.number}>
                <button
                  type="button"
                  disabled={!clickable}
                  onClick={() => clickable && setStep(item.number)}
                  className={`w-full flex items-center gap-2 rounded-md border px-3 py-2 text-left text-xs font-medium transition-colors ${
                    current
                      ? 'border-blue-700 bg-blue-50 text-blue-800'
                      : done
                        ? 'border-emerald-200 bg-emerald-50/70 text-emerald-800 hover:bg-emerald-50'
                        : 'border-slate-200 bg-white text-slate-400 cursor-default'
                  }`}
                >
                  <span
                    className={`flex h-5 w-5 items-center justify-center rounded-full text-[11px] shrink-0 ${
                      current
                        ? 'bg-blue-700 text-white'
                        : done
                          ? 'bg-emerald-600 text-white'
                          : 'bg-slate-200 text-slate-600'
                    }`}
                  >
                    {done ? <Check className="h-3 w-3" /> : item.number}
                  </span>
                  <span className="truncate">{item.label}</span>
                </button>
              </li>
            );
          })}
        </ol>

        {/* Active step panel */}
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          {step === 1 && (
            <Step1Classes
              draft={activeDraft}
              onSaved={(savedDraft) => {
                setActiveDraft(savedDraft);
                queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
                setStep(2);
              }}
            />
          )}

          {step === 2 && activeDraft && (
            <Step2Settings
              draft={activeDraft}
              onBack={() => setStep(1)}
              onSaved={async () => {
                await refreshDraft(activeDraft.id);
                setStep(3);
              }}
            />
          )}

          {step === 3 && activeDraft && (
            <Step3Check
              draft={activeDraft}
              onBack={() => setStep(2)}
              onContinue={() => setStep(4)}
            />
          )}

          {step === 4 && activeDraft && (
            <Step4Teachers
              draft={activeDraft}
              onBack={() => setStep(3)}
              onContinue={async () => {
                await refreshDraft(activeDraft.id);
                setStep(5);
              }}
            />
          )}

          {step === 5 && activeDraft && (
            <Step5FixedLectures
              draft={activeDraft}
              onBack={() => setStep(4)}
              onMakeTimetable={() => {
                setAutoMakeResult(true);
                setStep(6);
              }}
            />
          )}

          {step === 6 && activeDraft && (
            <Step6Result
              draft={activeDraft}
              autoMake={autoMakeResult}
              onBackToStep={(targetStep) => {
                setAutoMakeResult(false);
                setStep(targetStep);
              }}
              onSaved={() => {
                queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
                queryClient.invalidateQueries({ queryKey: ['timetable-tree'] });
                navigate('/timetables');
              }}
            />
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Make Timetable</h1>
          <p className="mt-1 text-sm text-slate-600">
            Start a new timetable for a semester or continue an unfinished one.
          </p>
        </div>

        <button
          type="button"
          onClick={startNewWizard}
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
        >
          <CalendarPlus className="h-4 w-4" />
          <span>Start new timetable</span>
        </button>
      </div>

      {pageError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          {pageError}
        </div>
      )}

      <div className="rounded-lg border border-slate-200 bg-white">
        <div className="px-5 py-3.5 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-slate-900">Unfinished timetables</h2>
        </div>

        {draftsQuery.isLoading ? (
          <div className="p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
            <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
            <span>Loading unfinished timetables...</span>
          </div>
        ) : drafts.length === 0 ? (
          <div className="p-10 text-center">
            <Clock className="h-7 w-7 text-slate-400 mx-auto" />
            <p className="mt-2.5 text-sm font-medium text-slate-900">No unfinished timetables</p>
            <p className="mt-1 text-sm text-slate-500">
              Click Start new timetable to choose classes and set up your schedule.
            </p>
          </div>
        ) : (
          <div className="divide-y divide-slate-200">
            {drafts.map((item) => (
              <div
                key={item.id}
                className="px-5 py-4 flex flex-wrap items-center justify-between gap-3 hover:bg-slate-50/60"
              >
                <div>
                  <div className="text-sm font-semibold text-slate-900">{item.name}</div>
                  <div className="mt-0.5 text-xs text-slate-500">
                    Started {new Date(item.createdAt).toLocaleString()}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={loadingDraftId === item.id}
                    onClick={() => continueDraft(item.id)}
                    className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-60"
                  >
                    {loadingDraftId === item.id ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Play className="h-3.5 w-3.5" />
                    )}
                    <span>Continue</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeleteTarget(item)}
                    className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-red-50 hover:text-red-700 hover:border-red-200"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    <span>Delete</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete unfinished timetable"
        message={
          deleteTarget
            ? `Delete "${deleteTarget.name}"? This removes its time settings, teacher choices, and fixed lectures.`
            : ''
        }
        confirmLabel="Delete"
        danger
        loading={deleteDraftMutation.isPending}
        onConfirm={() => deleteDraftMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}