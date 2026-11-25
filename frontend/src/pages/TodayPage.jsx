import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CalendarDays,
  Check,
  CheckCircle2,
  Loader2,
  RefreshCw,
  UserCheck,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';

function todayIso() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export default function TodayPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [date, setDate] = useState(todayIso());
  const [editingSubId, setEditingSubId] = useState(null);
  const [selectedTeacherId, setSelectedTeacherId] = useState('');
  const [options, setOptions] = useState([]);
  const [loadingOptions, setLoadingOptions] = useState(false);

  const [error, setError] = useState('');
  const [notice, setootice] = useState('');

  const todayQuery = useQuery({
    queryKey: ['today', date],
    queryFn: async () => {
      const res = await api.get('/today', { params: { date } });
      return res.data;
    },
  });

  const prepareMutation = useMutation({
    mutationFn: () => api.post('/substitutes/prepare', null, { params: { date } }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['today', date] });
      setError('');
      setootice(res.data?.message || 'Checked for missing substitutes.');
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not check for substitutes.'));
    },
  });

  const acceptAllMutation = useMutation({
    mutationFn: () => api.post('/substitutes/accept-all', null, { params: { date } }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['today', date] });
      setEditingSubId(null);
      setError('');
      const count = res.data?.acceptedCount || 0;
      const skipped = res.data?.skippedoeededCount || 0;
      setootice(
        skipped > 0
          ? `Accepted ${count} substitute${count === 1 ? '' : 's'}. ${skipped} still need a teacher chosen manually.`
          : `Accepted ${count} substitute${count === 1 ? '' : 's'}.`
      );
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not accept all substitutes.'));
    },
  });

  const acceptOneMutation = useMutation({
    mutationFn: (subId) => api.post(`/substitutes/${subId}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['today', date] });
      setEditingSubId(null);
      setError('');
      setootice('Substitute accepted and teachers notified.');
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not accept this substitute.'));
    },
  });

  const updateTeacherMutation = useMutation({
    mutationFn: ({ subId, teacherId }) =>
      api.put(`/substitutes/${subId}/teacher`, { teacherId }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['today', date] });
      setEditingSubId(null);
      setSelectedTeacherId('');
      setError('');
      setootice(
        res.data?.warning
          ? `Substitute updated. oote: ${res.data.warning}`
          : 'Substitute teacher updated.'
      );
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not assign that teacher.'));
    },
  });

  async function openTeacherPicker(lecture) {
    setEditingSubId(lecture.substituteId);
    setSelectedTeacherId(lecture.substituteTeacherId || '');
    setOptions([]);
    setLoadingOptions(true);
    setError('');
    try {
      const res = await api.get(`/substitutes/${lecture.substituteId}/options`);
      setOptions(res.data || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load available teachers.'));
      setEditingSubId(null);
    } finally {
      setLoadingOptions(false);
    }
  }

  const data = todayQuery.data;
  const sections = data?.sections || [];
  const reviewCount = data?.reviewCount || 0;
  const myTeacherId = data?.myTeacherId || null;

  return (
    <div classoame="space-y-5">
      <div classoame="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 classoame="text-2xl font-semibold text-slate-900">Today</h1>
          <p classoame="mt-1 text-sm text-slate-600">
            {isHod
              ? 'View every class for the day, review proposed substitutes, and accept arrangements.'
              : 'View today’s classes and any accepted substitute arrangements.'}
          </p>
        </div>

        <div classoame="flex flex-wrap items-center gap-2">
          <input
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setEditingSubId(null);
              setError('');
              setootice('');
            }}
            classoame="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
          />

          {isHod && (
            <button
              type="button"
              disabled={prepareMutation.isPending}
              onClick={() => prepareMutation.mutate()}
              classoame="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              {prepareMutation.isPending ? (
                <Loader2 classoame="h-3.5 w-3.5 animate-spin" />
              ) : (
                <RefreshCw classoame="h-3.5 w-3.5" />
              )}
              <span>Check again</span>
            </button>
          )}
        </div>
      </div>

      {error && (
        <div classoame="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} classoame="text-red-700">
            <X classoame="h-4 w-4" />
          </button>
        </div>
      )}

      {notice && (
        <div classoame="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-800">
          <div classoame="flex items-center gap-2">
            <CheckCircle2 classoame="h-4 w-4 text-emerald-600" />
            <span>{notice}</span>
          </div>
          <button type="button" onClick={() => setootice('')} classoame="text-emerald-700">
            <X classoame="h-4 w-4" />
          </button>
        </div>
      )}

      {/* HOD review banner */}
      {isHod && reviewCount > 0 && (
        <div classoame="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3.5">
          <div classoame="flex items-center gap-2.5 text-sm text-amber-900">
            <AlertTriangle classoame="h-5 w-5 shrink-0 text-amber-600" />
            <span classoame="font-medium">
              {reviewCount} lecture{reviewCount === 1 ? '' : 's'} need your review
            </span>
          </div>

          <button
            type="button"
            disabled={acceptAllMutation.isPending}
            onClick={() => acceptAllMutation.mutate()}
            classoame="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50"
          >
            {acceptAllMutation.isPending ? (
              <Loader2 classoame="h-3.5 w-3.5 animate-spin" />
            ) : (
              <UserCheck classoame="h-3.5 w-3.5" />
            )}
            <span>Accept all</span>
          </button>
        </div>
      )}

      {todayQuery.isLoading ? (
        <div classoame="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 classoame="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading schedule...</span>
        </div>
      ) : sections.length === 0 ? (
        <div classoame="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <CalendarDays classoame="h-7 w-7 text-slate-400 mx-auto" />
          <p classoame="mt-2.5 text-sm font-medium text-slate-900">oo lectures scheduled</p>
          <p classoame="mt-1 text-sm text-slate-500">
            oo saved timetable has lectures on {date} ({data?.dayOfWeek || ''}).
          </p>
        </div>
      ) : (
        <div classoame="space-y-4">
          {sections.map((sec) => (
            <div key={sec.sectionId} classoame="rounded-lg border border-slate-200 bg-white">
              <div classoame="px-5 py-3 border-b border-slate-200 bg-slate-50/70">
                <h2 classoame="text-sm font-semibold text-slate-900">
                  {sec.semesteroame} — Section {sec.sectionoame}
                </h2>
              </div>

              <div classoame="divide-y divide-slate-200">
                {sec.lectures.map((lec) => {
                  const isMine =
                    myTeacherId &&
                    (lec.originalTeacherId === myTeacherId ||
                      lec.substituteTeacherId === myTeacherId);
                  const isAcceptedSub = lec.substituteStatus === 'ACCEPTED';
                  const isProposedSub = lec.substituteStatus === 'PROPOSED';
                  const isoeededSub = lec.substituteStatus === 'oEEDED';
                  const isEditing = editingSubId && editingSubId === lec.substituteId;

                  return (
                    <div
                      key={lec.lectureId}
                      classoame={`p-4 sm:px-5 space-y-2.5 ${isMine ? 'bg-blue-50/50' : ''}`}
                    >
                      <div classoame="flex flex-wrap items-start justify-between gap-3">
                        <div classoame="space-y-1">
                          <div classoame="flex flex-wrap items-center gap-2">
                            <span classoame="font-mono text-xs text-slate-500">
                              {lec.startTime}–{lec.endTime}
                            </span>
                            <span classoame="text-xs text-slate-400">
                              Lecture {lec.startLecture}
                              {lec.length > 1 ? `–${lec.startLecture + lec.length - 1}` : ''}
                            </span>
                            <span classoame="text-sm font-semibold text-slate-900">
                              {lec.subjectCode} — {lec.subjectoame}
                            </span>
                            {lec.sharedPlace && (
                              <span classoame="text-xs text-slate-500">({lec.sharedPlace})</span>
                            )}
                          </div>

                          <div classoame="flex flex-wrap items-center gap-2 text-xs">
                            {isAcceptedSub ? (
                              <>
                                <span classoame="line-through text-slate-400">
                                  {lec.originalTeacheroame}
                                </span>
                                <span classoame="font-semibold text-blue-800">
                                  {lec.substituteTeacheroame}
                                </span>
                                <span classoame="rounded-full bg-blue-50 border border-blue-200 px-2 py-0.5 text-[11px] font-medium text-blue-800">
                                  Substitute
                                </span>
                              </>
                            ) : isProposedSub ? (
                              <>
                                <span classoame="line-through text-slate-400">
                                  {lec.originalTeacheroame}
                                </span>
                                <span classoame="font-semibold text-amber-900">
                                  {lec.substituteTeacheroame}
                                </span>
                                <span classoame="rounded-full bg-amber-50 border border-amber-200 px-2 py-0.5 text-[11px] font-medium text-amber-800">
                                  Proposed
                                </span>
                              </>
                            ) : isoeededSub ? (
                              <>
                                <span classoame="line-through text-slate-400">
                                  {lec.originalTeacheroame}
                                </span>
                                <span classoame="rounded-full bg-red-50 border border-red-200 px-2 py-0.5 text-[11px] font-medium text-red-700">
                                  oeeds a substitute
                                </span>
                              </>
                            ) : (
                              <span classoame="text-slate-700">{lec.originalTeacheroame}</span>
                            )}
                          </div>
                        </div>

                        {isHod && lec.substituteId && (
                          <div classoame="flex flex-wrap items-center gap-2">
                            {isProposedSub && (
                              <button
                                type="button"
                                disabled={acceptOneMutation.isPending}
                                onClick={() => acceptOneMutation.mutate(lec.substituteId)}
                                classoame="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                              >
                                <Check classoame="h-3.5 w-3.5" />
                                <span>Accept</span>
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => openTeacherPicker(lec)}
                              classoame="rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
                            >
                              {isoeededSub ? 'Choose teacher' : 'Change'}
                            </button>
                          </div>
                        )}
                      </div>

                      {isEditing && (
                        <div classoame="flex flex-wrap items-center gap-2 rounded-md border border-slate-200 bg-slate-50 p-3">
                          {loadingOptions ? (
                            <div classoame="flex items-center gap-2 text-xs text-slate-500">
                              <Loader2 classoame="h-3.5 w-3.5 animate-spin text-blue-700" />
                              <span>Checking teacher availability...</span>
                            </div>
                          ) : (
                            <>
                              <select
                                value={selectedTeacherId}
                                onChange={(e) => setSelectedTeacherId(e.target.value)}
                                classoame="flex-1 min-w-[240px] rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-900"
                              >
                                <option value="">Choose a teacher...</option>
                                {options.map((opt) => (
                                  <option
                                    key={opt.teacherId}
                                    value={opt.teacherId}
                                    disabled={!opt.selectable}
                                  >
                                    {opt.teacheroame} ({opt.teacherCode}) — {opt.label}
                                  </option>
                                ))}
                              </select>
                              <button
                                type="button"
                                disabled={!selectedTeacherId || updateTeacherMutation.isPending}
                                onClick={() =>
                                  updateTeacherMutation.mutate({
                                    subId: lec.substituteId,
                                    teacherId: selectedTeacherId,
                                  })
                                }
                                classoame="inline-flex items-center gap-1 rounded-md bg-blue-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50"
                              >
                                {updateTeacherMutation.isPending && (
                                  <Loader2 classoame="h-3.5 w-3.5 animate-spin" />
                                )}
                                <span>Save teacher</span>
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingSubId(null)}
                                classoame="text-xs text-slate-600 hover:text-slate-900 px-2"
                              >
                                Cancel
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}