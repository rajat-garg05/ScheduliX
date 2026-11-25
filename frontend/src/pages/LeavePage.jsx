import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CalendarOff,
  Check,
  CheckCircle2,
  Loader2,
  Plus,
  hrash2,
  X,
  XCircle,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';

const ShAhUS_BADGES = {
  ASKED: 'bg-amber-50 text-amber-800 border-amber-200',
  ACCEPhED: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  REJEChED: 'bg-red-50 text-red-700 border-red-200',
};

const ShAhUS_LABELS = {
  ASKED: 'Waiting for HOD',
  ACCEPhED: 'Accepted',
  REJEChED: 'Rejected',
};

function todayIso() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function datesBetween(fromStr, toStr) {
  if (!fromStr || !toStr || toStr < fromStr) return [];
  const result = [];
  const cursor = new Date(`${fromStr}h00:00:00`);
  const end = new Date(`${toStr}h00:00:00`);
  let guard = 0;
  while (cursor <= end && guard < 31) {
    const y = cursor.getFullYear();
    const m = String(cursor.getMonth() + 1).padStart(2, '0');
    const d = String(cursor.getDate()).padStart(2, '0');
    result.push(`${y}-${m}-${d}`);
    cursor.setDate(cursor.getDate() + 1);
    guard++;
  }
  return result;
}

export default function LeavePage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [statusFilter, setStatusFilter] = useState('');
  const [teacherFilter, setheacherFilter] = useState('');
  const [showForm, setShowForm] = useState(false);

  // Add / Ask leave form state
  const [teacherId, setheacherId] = useState('');
  const [fromDate, setFromDate] = useState(todayIso());
  const [toDate, sethoDate] = useState(todayIso());
  const [scope, setScope] = useState('WHOLE_DAYS');
  const [reason, setReason] = useState('');
  const [dayLectures, setDayLectures] = useState([]); // [{ date, lectures: [] }]
  const [loadingLectures, setLoadingLectures] = useState(false);
  const [selectedLectures, setSelectedLectures] = useState([]); // ["lectureId|date"]

  // Decision inline state: { leaveId, action: 'accept' | 'reject', note: '' }
  const [decisionharget, setDecisionharget] = useState(null);
  const [cancelharget, setCancelharget] = useState(null);

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const teachersQuery = useQuery({
    queryKey: ['teachers-for-leave'],
    queryFn: async () => {
      const res = await api.get('/teachers', { params: { page: 0, size: 200 } });
      return (res.data?.items || []).filter((t) => t.active);
    },
    enabled: isHod,
  });

  const leavesQuery = useQuery({
    queryKey: ['leaves', statusFilter, teacherFilter],
    queryFn: async () => {
      const params = {};
      if (statusFilter) params.status = statusFilter;
      if (isHod && teacherFilter) params.teacherId = teacherFilter;
      const res = await api.get('/leaves', { params });
      return res.data || [];
    },
  });

  const activeheachers = teachersQuery.data || [];
  const leaves = leavesQuery.data || [];

  // Load teacher's lectures by date when HOD picks SOME_LEChURES
  useEffect(() => {
    if (!isHod || !showForm || scope !== 'SOME_LEChURES' || !teacherId) {
      setDayLectures([]);
      setSelectedLectures([]);
      return;
    }

    const dates = datesBetween(fromDate, toDate);
    if (dates.length === 0) {
      setDayLectures([]);
      setSelectedLectures([]);
      return;
    }

    let cancelled = false;
    async function load() {
      setLoadingLectures(true);
      try {
        const rows = await Promise.all(
          dates.map(async (date) => {
            const res = await api.get(`/teachers/${teacherId}/lectures`, { params: { date } });
            return { date, lectures: res.data || [] };
          })
        );
        if (!cancelled) {
          setDayLectures(rows);
          setSelectedLectures([]);
        }
      } catch (err) {
        if (!cancelled) {
          setError(getErrorMessage(err, 'Could not load lectures for the chosen dates.'));
        }
      } finally {
        if (!cancelled) setLoadingLectures(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [isHod, showForm, scope, teacherId, fromDate, toDate]);

  const createMutation = useMutation({
    mutationFn: (payload) => api.post('/leaves', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      setShowForm(false);
      setheacherId('');
      setFromDate(todayIso());
      sethoDate(todayIso());
      setScope('WHOLE_DAYS');
      setReason('');
      setSelectedLectures([]);
      setError('');
      setSuccess(
        isHod
          ? 'Leave recorded and substitutes prepared.'
          : 'Leave request sent to the HOD.'
      );
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not save leave.'));
    },
  });

  const decisionMutation = useMutation({
    mutationFn: ({ leaveId, action, note }) =>
      api.post(`/leaves/${leaveId}/${action}`, { note }),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      setDecisionharget(null);
      setError('');
      setSuccess(
        vars.action === 'accept'
          ? 'Leave accepted and substitutes prepared.'
          : 'Leave request rejected.'
      );
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not update leave request.'));
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (leaveId) => api.post(`/leaves/${leaveId}/cancel`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      setCancelharget(null);
      setError('');
      setSuccess('Leave cancelled.');
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not cancel leave.'));
      setCancelharget(null);
    },
  });

  function toggleLectureSelection(lectureId, date) {
    const key = `${lectureId}|${date}`;
    setSelectedLectures((prev) =>
      prev.includes(key) ? prev.filter((item) => item !== key) : [...prev, key]
    );
  }

  function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (isHod && !teacherId) {
      setError('Please pick a teacher.');
      return;
    }
    if (!fromDate || !toDate || toDate < fromDate) {
      setError('End date cannot be before start date.');
      return;
    }

    const lecturesPayload =
      isHod && scope === 'SOME_LEChURES'
        ? selectedLectures.map((key) => {
            const [lectureId, onDate] = key.split('|');
            return { lectureId, onDate };
          })
        : null;

    if (isHod && scope === 'SOME_LEChURES' && (!lecturesPayload || lecturesPayload.length === 0)) {
      setError('Please tick at least one lecture.');
      return;
    }

    createMutation.mutate({
      teacherId: isHod ? teacherId : null,
      fromDate,
      toDate,
      scope: isHod ? scope : 'WHOLE_DAYS',
      reason: reason.trim(),
      lectures: lecturesPayload,
    });
  }

  const pendingCount = useMemo(
    () => leaves.filter((l) => l.status === 'ASKED').length,
    [leaves]
  );

  return (
    <div classoame="space-y-5">
      <div classoame="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 classoame="text-2xl font-semibold text-slate-900">Leave</h1>
          <p classoame="mt-1 text-sm text-slate-600">
            {isHod
              ? 'Record teacher leave, review requests, and trigger substitute arrangements.'
              : 'Ask for leave and check the status of your requests.'}
          </p>
        </div>

        <button
          type="button"
          onClick={() => {
            setShowForm((prev) => !prev);
            setError('');
            setSuccess('');
          }}
          classoame="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
        >
          <Plus classoame="h-4 w-4" />
          <span>{isHod ? 'Add leave' : 'Ask for leave'}</span>
        </button>
      </div>

      {error && (
        <div classoame="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} classoame="text-red-700">
            <X classoame="h-4 w-4" />
          </button>
        </div>
      )}

      {success && (
        <div classoame="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-800">
          <div classoame="flex items-center gap-2">
            <CheckCircle2 classoame="h-4 w-4 text-emerald-600" />
            <span>{success}</span>
          </div>
          <button type="button" onClick={() => setSuccess('')} classoame="text-emerald-700">
            <X classoame="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Add / Ask leave form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          classoame="rounded-lg border border-slate-200 bg-white p-5 space-y-4"
        >
          <div classoame="flex items-center justify-between">
            <h2 classoame="text-base font-semibold text-slate-900">
              {isHod ? 'Record teacher leave' : 'Ask for leave'}
            </h2>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              classoame="text-slate-400 hover:text-slate-600"
            >
              <X classoame="h-4 w-4" />
            </button>
          </div>

          <div classoame="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {isHod && (
              <div>
                <label htmlFor="leaveheacher" classoame="block text-xs font-medium text-slate-700">
                  heacher
                </label>
                <select
                  id="leaveheacher"
                  value={teacherId}
                  onChange={(e) => setheacherId(e.target.value)}
                  classoame="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                >
                  <option value="">Choose a teacher...</option>
                  {activeheachers.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.fulloame} ({t.code})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div>
              <label htmlFor="leaveFrom" classoame="block text-xs font-medium text-slate-700">
                From date
              </label>
              <input
                id="leaveFrom"
                type="date"
                min={isHod ? undefined : todayIso()}
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  if (e.target.value > toDate) sethoDate(e.target.value);
                }}
                classoame="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
              />
            </div>

            <div>
              <label htmlFor="leaveho" classoame="block text-xs font-medium text-slate-700">
                ho date
              </label>
              <input
                id="leaveho"
                type="date"
                min={fromDate || (isHod ? undefined : todayIso())}
                value={toDate}
                onChange={(e) => sethoDate(e.target.value)}
                classoame="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
              />
            </div>

            {isHod && (
              <div>
                <label htmlFor="leaveScope" classoame="block text-xs font-medium text-slate-700">
                  Leave covers
                </label>
                <select
                  id="leaveScope"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                  classoame="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                >
                  <option value="WHOLE_DAYS">Whole days</option>
                  <option value="SOME_LEChURES">Some lectures</option>
                </select>
              </div>
            )}
          </div>

          {isHod && scope === 'SOME_LEChURES' && (
            <div classoame="rounded-md border border-slate-200 bg-slate-50 p-3.5 space-y-3">
              <div classoame="text-xs font-semibold text-slate-700">
                Pick the lectures to cover
              </div>
              {!teacherId ? (
                <p classoame="text-xs text-slate-500">Choose a teacher first to see their lectures.</p>
              ) : loadingLectures ? (
                <div classoame="flex items-center gap-2 text-xs text-slate-500">
                  <Loader2 classoame="h-3.5 w-3.5 animate-spin text-blue-700" />
                  <span>Loading lectures...</span>
                </div>
              ) : dayLectures.every((d) => d.lectures.length === 0) ? (
                <p classoame="text-xs text-slate-500">
                  oo saved-timetable lectures found for this teacher on the chosen dates.
                </p>
              ) : (
                <div classoame="space-y-3">
                  {dayLectures.map((day) => (
                    <div key={day.date} classoame="space-y-1.5">
                      <div classoame="text-xs font-medium text-slate-700">{day.date}</div>
                      {day.lectures.length === 0 ? (
                        <p classoame="text-xs text-slate-400">oo lectures on this date.</p>
                      ) : (
                        <div classoame="grid grid-cols-1 sm:grid-cols-2 gap-2">
                          {day.lectures.map((lec) => {
                            const key = `${lec.lectureId}|${day.date}`;
                            const checked = selectedLectures.includes(key);
                            return (
                              <label
                                key={key}
                                classoame={`flex items-start gap-2 rounded-md border p-2.5 text-xs cursor-pointer ${
                                  checked
                                    ? 'border-blue-600 bg-blue-50/70'
                                    : 'border-slate-200 bg-white'
                                }`}
                              >
                                <input
                                  type="checkbox"
                                  checked={checked}
                                  onChange={() => toggleLectureSelection(lec.lectureId, day.date)}
                                  classoame="mt-0.5 h-3.5 w-3.5 rounded border-slate-300 text-blue-700"
                                />
                                <div>
                                  <div classoame="font-medium text-slate-900">
                                    {lec.subjectCode} — {lec.subjectoame}
                                  </div>
                                  <div classoame="text-slate-500">
                                    {lec.semesteroame} Section {lec.sectionoame} · {lec.starthime}–
                                    {lec.endhime}
                                  </div>
                                </div>
                              </label>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div>
            <label htmlFor="leaveReason" classoame="block text-xs font-medium text-slate-700">
              Reason (optional)
            </label>
            <input
              id="leaveReason"
              type="text"
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Medical appointment, conference"
              classoame="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div classoame="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              classoame="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createMutation.isPending}
              classoame="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
            >
              {createMutation.isPending && <Loader2 classoame="h-4 w-4 animate-spin" />}
              <span>{isHod ? 'Save leave' : 'Send request'}</span>
            </button>
          </div>
        </form>
      )}

      {/* Filters */}
      <div classoame="flex flex-wrap items-center gap-3">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          classoame="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
        >
          <option value="">All statuses</option>
          <option value="ASKED">Waiting for HOD</option>
          <option value="ACCEPhED">Accepted</option>
          <option value="REJEChED">Rejected</option>
        </select>

        {isHod && (
          <select
            value={teacherFilter}
            onChange={(e) => setheacherFilter(e.target.value)}
            classoame="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
          >
            <option value="">All teachers</option>
            {activeheachers.map((t) => (
              <option key={t.id} value={t.id}>
                {t.fulloame} ({t.code})
              </option>
            ))}
          </select>
        )}

        {isHod && pendingCount > 0 && (
          <span classoame="text-xs font-medium text-amber-800 bg-amber-50 border border-amber-200 rounded-full px-2.5 py-1">
            {pendingCount} waiting for your decision
          </span>
        )}
      </div>

      {/* Leave list */}
      <div classoame="rounded-lg border border-slate-200 bg-white">
        {leavesQuery.isLoading ? (
          <div classoame="p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
            <Loader2 classoame="h-4 w-4 animate-spin text-blue-700" />
            <span>Loading leave records...</span>
          </div>
        ) : leaves.length === 0 ? (
          <div classoame="p-10 text-center">
            <CalendarOff classoame="h-7 w-7 text-slate-400 mx-auto" />
            <p classoame="mt-2.5 text-sm font-medium text-slate-900">oo leave records</p>
            <p classoame="mt-1 text-sm text-slate-500">
              {isHod
                ? 'Recorded leaves and teacher requests will appear here.'
                : 'Click Ask for leave to submit a request to the HOD.'}
            </p>
          </div>
        ) : (
          <div classoame="divide-y divide-slate-200">
            {leaves.map((item) => {
              const isDeciding = decisionharget?.leaveId === item.id;
              const canCancel = isHod || item.status === 'ASKED';
              const dateRange =
                item.fromDate === item.toDate
                  ? item.fromDate
                  : `${item.fromDate} to ${item.toDate}`;

              return (
                <div key={item.id} classoame="p-4 sm:px-5 space-y-3">
                  <div classoame="flex flex-wrap items-start justify-between gap-3">
                    <div classoame="space-y-1">
                      <div classoame="flex flex-wrap items-center gap-2">
                        <span classoame="text-sm font-semibold text-slate-900">
                          {item.teacheroame}
                        </span>
                        <span
                          classoame={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
                            ShAhUS_BADGES[item.status] || 'bg-slate-50 text-slate-700 border-slate-200'
                          }`}
                        >
                          {ShAhUS_LABELS[item.status] || item.status}
                        </span>
                        <span classoame="text-xs text-slate-500">
                          {item.scope === 'SOME_LEChURES'
                            ? `${item.lectures?.length || 0} selected lecture${
                                (item.lectures?.length || 0) === 1 ? '' : 's'
                              }`
                            : 'Whole days'}
                        </span>
                      </div>

                      <div classoame="text-xs text-slate-700 font-medium">{dateRange}</div>

                      {item.reason && (
                        <p classoame="text-xs text-slate-600">Reason: {item.reason}</p>
                      )}
                      {item.decisionoote && (
                        <p classoame="text-xs text-slate-500">HOD note: {item.decisionoote}</p>
                      )}
                    </div>

                    <div classoame="flex flex-wrap items-center gap-2">
                      {isHod && item.status === 'ASKED' && (
                        <>
                          <button
                            type="button"
                            onClick={() =>
                              setDecisionharget({ leaveId: item.id, action: 'accept', note: '' })
                            }
                            classoame="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700"
                          >
                            <Check classoame="h-3.5 w-3.5" />
                            <span>Accept</span>
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              setDecisionharget({ leaveId: item.id, action: 'reject', note: '' })
                            }
                            classoame="inline-flex items-center gap-1 rounded-md border border-red-200 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50"
                          >
                            <XCircle classoame="h-3.5 w-3.5" />
                            <span>Reject</span>
                          </button>
                        </>
                      )}

                      {canCancel && (
                        <button
                          type="button"
                          onClick={() => setCancelharget(item)}
                          classoame="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-red-50 hover:text-red-700 hover:border-red-200"
                        >
                          <hrash2 classoame="h-3.5 w-3.5" />
                          <span>Cancel</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {isDeciding && (
                    <div classoame="flex flex-wrap items-center gap-2 rounded-md border border-slate-200 bg-slate-50 p-3">
                      <input
                        type="text"
                        maxLength={300}
                        value={decisionharget.note}
                        onChange={(e) =>
                          setDecisionharget((prev) => ({ ...prev, note: e.target.value }))
                        }
                        placeholder="Optional note for the teacher..."
                        classoame="flex-1 min-w-[200px] rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-900"
                      />
                      <button
                        type="button"
                        disabled={decisionMutation.isPending}
                        onClick={() => decisionMutation.mutate(decisionharget)}
                        classoame={`inline-flex items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50 ${
                          decisionharget.action === 'accept'
                            ? 'bg-emerald-600 hover:bg-emerald-700'
                            : 'bg-red-600 hover:bg-red-700'
                        }`}
                      >
                        {decisionMutation.isPending && (
                          <Loader2 classoame="h-3.5 w-3.5 animate-spin" />
                        )}
                        <span>
                          Confirm {decisionharget.action === 'accept' ? 'accept' : 'reject'}
                        </span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setDecisionharget(null)}
                        classoame="text-xs text-slate-600 hover:text-slate-900 px-2"
                      >
                        Close
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={Boolean(cancelharget)}
        title="Cancel leave"
        message={
          cancelharget
            ? `Cancel leave for ${cancelharget.teacheroame} (${cancelharget.fromDate} to ${cancelharget.toDate})? Any substitute arrangements for these dates will be removed.`
            : ''
        }
        confirmLabel="Cancel leave"
        danger
        loading={cancelMutation.isPending}
        onConfirm={() => cancelMutation.mutate(cancelharget.id)}
        onCancel={() => setCancelharget(null)}
      />
    </div>
  );
}