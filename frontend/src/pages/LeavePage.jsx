import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CalendarOff,
  Check,
  CheckCircle2,
  Loader2,
  Plus,
  Trash2,
  X,
  XCircle,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';

const STATUS_BADGES = {
  ASKED: 'bg-amber-50 text-amber-800 border-amber-200',
  ACCEPTED: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  REJECTED: 'bg-red-50 text-red-700 border-red-200',
};

const STATUS_LABELS = {
  ASKED: 'Waiting for HOD',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
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
  const cursor = new Date(`${fromStr}T00:00:00`);
  const end = new Date(`${toStr}T00:00:00`);
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
  const [teacherFilter, setTeacherFilter] = useState('');
  const [showForm, setShowForm] = useState(false);

  // Add / Ask leave form state
  const [teacherId, setTeacherId] = useState('');
  const [fromDate, setFromDate] = useState(todayIso());
  const [toDate, setToDate] = useState(todayIso());
  const [scope, setScope] = useState('WHOLE_DAYS');
  const [reason, setReason] = useState('');
  const [dayLectures, setDayLectures] = useState([]); // [{ date, lectures: [] }]
  const [loadingLectures, setLoadingLectures] = useState(false);
  const [selectedLectures, setSelectedLectures] = useState([]); // ["lectureId|date"]

  // Decision inline state: { leaveId, action: 'accept' | 'reject', note: '' }
  const [decisionTarget, setDecisionTarget] = useState(null);
  const [cancelTarget, setCancelTarget] = useState(null);

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

  const activeTeachers = teachersQuery.data || [];
  const leaves = leavesQuery.data || [];

  // Load teacher's lectures by date when HOD picks SOME_LECTURES
  useEffect(() => {
    if (!isHod || !showForm || scope !== 'SOME_LECTURES' || !teacherId) {
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
      setTeacherId('');
      setFromDate(todayIso());
      setToDate(todayIso());
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
      setDecisionTarget(null);
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
      setCancelTarget(null);
      setError('');
      setSuccess('Leave cancelled.');
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not cancel leave.'));
      setCancelTarget(null);
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
      isHod && scope === 'SOME_LECTURES'
        ? selectedLectures.map((key) => {
            const [lectureId, onDate] = key.split('|');
            return { lectureId, onDate };
          })
        : null;

    if (isHod && scope === 'SOME_LECTURES' && (!lecturesPayload || lecturesPayload.length === 0)) {
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
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Leave</h1>
          <p className="mt-1 text-sm text-slate-600">
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
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
        >
          <Plus className="h-4 w-4" />
          <span>{isHod ? 'Add leave' : 'Ask for leave'}</span>
        </button>
      </div>

      {error && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} className="text-red-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {success && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <span>{success}</span>
          </div>
          <button type="button" onClick={() => setSuccess('')} className="text-emerald-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Add / Ask leave form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="rounded-lg border border-slate-200 bg-white p-5 space-y-4"
        >
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-slate-900">
              {isHod ? 'Record teacher leave' : 'Ask for leave'}
            </h2>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="text-slate-400 hover:text-slate-600"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {isHod && (
              <div>
                <label htmlFor="leaveTeacher" className="block text-xs font-medium text-slate-700">
                  Teacher
                </label>
                <select
                  id="leaveTeacher"
                  value={teacherId}
                  onChange={(e) => setTeacherId(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                >
                  <option value="">Choose a teacher...</option>
                  {activeTeachers.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.fullName} ({t.code})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div>
              <label htmlFor="leaveFrom" className="block text-xs font-medium text-slate-700">
                From date
              </label>
              <input
                id="leaveFrom"
                type="date"
                min={isHod ? undefined : todayIso()}
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  if (e.target.value > toDate) setToDate(e.target.value);
                }}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
              />
            </div>

            <div>
              <label htmlFor="leaveTo" className="block text-xs font-medium text-slate-700">
                To date
              </label>
              <input
                id="leaveTo"
                type="date"
                min={fromDate || (isHod ? undefined : todayIso())}
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
              />
            </div>

            {isHod && (
              <div>
                <label htmlFor="leaveScope" className="block text-xs font-medium text-slate-700">
                  Leave covers
                </label>
                <select
                  id="leaveScope"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                >
                  <option value="WHOLE_DAYS">Whole days</option>
                  <option value="SOME_LECTURES">Some lectures</option>
                </select>
              </div>
            )}
          </div>

          {isHod && scope === 'SOME_LECTURES' && (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-3.5 space-y-3">
              <div className="text-xs font-semibold text-slate-700">
                Pick the lectures to cover
              </div>
              {!teacherId ? (
                <p className="text-xs text-slate-500">Choose a teacher first to see their lectures.</p>
              ) : loadingLectures ? (
                <div className="flex items-center gap-2 text-xs text-slate-500">
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-700" />
                  <span>Loading lectures...</span>
                </div>
              ) : dayLectures.every((d) => d.lectures.length === 0) ? (
                <p className="text-xs text-slate-500">
                  No saved-timetable lectures found for this teacher on the chosen dates.
                </p>
              ) : (
                <div className="space-y-3">
                  {dayLectures.map((day) => (
                    <div key={day.date} className="space-y-1.5">
                      <div className="text-xs font-medium text-slate-700">{day.date}</div>
                      {day.lectures.length === 0 ? (
                        <p className="text-xs text-slate-400">No lectures on this date.</p>
                      ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                          {day.lectures.map((lec) => {
                            const key = `${lec.lectureId}|${day.date}`;
                            const checked = selectedLectures.includes(key);
                            return (
                              <label
                                key={key}
                                className={`flex items-start gap-2 rounded-md border p-2.5 text-xs cursor-pointer ${
                                  checked
                                    ? 'border-blue-600 bg-blue-50/70'
                                    : 'border-slate-200 bg-white'
                                }`}
                              >
                                <input
                                  type="checkbox"
                                  checked={checked}
                                  onChange={() => toggleLectureSelection(lec.lectureId, day.date)}
                                  className="mt-0.5 h-3.5 w-3.5 rounded border-slate-300 text-blue-700"
                                />
                                <div>
                                  <div className="font-medium text-slate-900">
                                    {lec.subjectCode} — {lec.subjectName}
                                  </div>
                                  <div className="text-slate-500">
                                    {lec.semesterName} Section {lec.sectionName} · {lec.startTime}–
                                    {lec.endTime}
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
            <label htmlFor="leaveReason" className="block text-xs font-medium text-slate-700">
              Reason (optional)
            </label>
            <input
              id="leaveReason"
              type="text"
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Medical appointment, conference"
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
            >
              {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              <span>{isHod ? 'Save leave' : 'Send request'}</span>
            </button>
          </div>
        </form>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
        >
          <option value="">All statuses</option>
          <option value="ASKED">Waiting for HOD</option>
          <option value="ACCEPTED">Accepted</option>
          <option value="REJECTED">Rejected</option>
        </select>

        {isHod && (
          <select
            value={teacherFilter}
            onChange={(e) => setTeacherFilter(e.target.value)}
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
          >
            <option value="">All teachers</option>
            {activeTeachers.map((t) => (
              <option key={t.id} value={t.id}>
                {t.fullName} ({t.code})
              </option>
            ))}
          </select>
        )}

        {isHod && pendingCount > 0 && (
          <span className="text-xs font-medium text-amber-800 bg-amber-50 border border-amber-200 rounded-full px-2.5 py-1">
            {pendingCount} waiting for your decision
          </span>
        )}
      </div>

      {/* Leave list */}
      <div className="rounded-lg border border-slate-200 bg-white">
        {leavesQuery.isLoading ? (
          <div className="p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
            <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
            <span>Loading leave records...</span>
          </div>
        ) : leaves.length === 0 ? (
          <div className="p-10 text-center">
            <CalendarOff className="h-7 w-7 text-slate-400 mx-auto" />
            <p className="mt-2.5 text-sm font-medium text-slate-900">No leave records</p>
            <p className="mt-1 text-sm text-slate-500">
              {isHod
                ? 'Recorded leaves and teacher requests will appear here.'
                : 'Click Ask for leave to submit a request to the HOD.'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-slate-200">
            {leaves.map((item) => {
              const isDeciding = decisionTarget?.leaveId === item.id;
              const canCancel = isHod || item.status === 'ASKED';
              const dateRange =
                item.fromDate === item.toDate
                  ? item.fromDate
                  : `${item.fromDate} to ${item.toDate}`;

              return (
                <div key={item.id} className="p-4 sm:px-5 space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold text-slate-900">
                          {item.teacherName}
                        </span>
                        <span
                          className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
                            STATUS_BADGES[item.status] || 'bg-slate-50 text-slate-700 border-slate-200'
                          }`}
                        >
                          {STATUS_LABELS[item.status] || item.status}
                        </span>
                        <span className="text-xs text-slate-500">
                          {item.scope === 'SOME_LECTURES'
                            ? `${item.lectures?.length || 0} selected lecture${
                                (item.lectures?.length || 0) === 1 ? '' : 's'
                              }`
                            : 'Whole days'}
                        </span>
                      </div>

                      <div className="text-xs text-slate-700 font-medium">{dateRange}</div>

                      {item.reason && (
                        <p className="text-xs text-slate-600">Reason: {item.reason}</p>
                      )}
                      {item.decisionNote && (
                        <p className="text-xs text-slate-500">HOD note: {item.decisionNote}</p>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      {isHod && item.status === 'ASKED' && (
                        <>
                          <button
                            type="button"
                            onClick={() =>
                              setDecisionTarget({ leaveId: item.id, action: 'accept', note: '' })
                            }
                            className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700"
                          >
                            <Check className="h-3.5 w-3.5" />
                            <span>Accept</span>
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              setDecisionTarget({ leaveId: item.id, action: 'reject', note: '' })
                            }
                            className="inline-flex items-center gap-1 rounded-md border border-red-200 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50"
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            <span>Reject</span>
                          </button>
                        </>
                      )}

                      {canCancel && (
                        <button
                          type="button"
                          onClick={() => setCancelTarget(item)}
                          className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-red-50 hover:text-red-700 hover:border-red-200"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          <span>Cancel</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {isDeciding && (
                    <div className="flex flex-wrap items-center gap-2 rounded-md border border-slate-200 bg-slate-50 p-3">
                      <input
                        type="text"
                        maxLength={300}
                        value={decisionTarget.note}
                        onChange={(e) =>
                          setDecisionTarget((prev) => ({ ...prev, note: e.target.value }))
                        }
                        placeholder="Optional note for the teacher..."
                        className="flex-1 min-w-[200px] rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-900"
                      />
                      <button
                        type="button"
                        disabled={decisionMutation.isPending}
                        onClick={() => decisionMutation.mutate(decisionTarget)}
                        className={`inline-flex items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50 ${
                          decisionTarget.action === 'accept'
                            ? 'bg-emerald-600 hover:bg-emerald-700'
                            : 'bg-red-600 hover:bg-red-700'
                        }`}
                      >
                        {decisionMutation.isPending && (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        )}
                        <span>
                          Confirm {decisionTarget.action === 'accept' ? 'accept' : 'reject'}
                        </span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setDecisionTarget(null)}
                        className="text-xs text-slate-600 hover:text-slate-900 px-2"
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
        open={Boolean(cancelTarget)}
        title="Cancel leave"
        message={
          cancelTarget
            ? `Cancel leave for ${cancelTarget.teacherName} (${cancelTarget.fromDate} to ${cancelTarget.toDate})? Any substitute arrangements for these dates will be removed.`
            : ''
        }
        confirmLabel="Cancel leave"
        danger
        loading={cancelMutation.isPending}
        onConfirm={() => cancelMutation.mutate(cancelTarget.id)}
        onCancel={() => setCancelTarget(null)}
      />
    </div>
  );
}