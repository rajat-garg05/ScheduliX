import { useEffect, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Loader2,
  Sparkles,
  XCircle,
} from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

export default function Step4Teachers({ draft, onBack, onContinue }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [suggesting, setSuggesting] = useState(false);
  const [savingKey, setSavingKey] = useState(null);
  const [error, setError] = useState('');
  const autoSuggestedRef = useRef(false);

  async function loadTeachers(allowAutoSuggest) {
    setLoading(true);
    setError('');
    try {
      const res = await api.get(`/timetables/${draft.id}/teachers`);
      const payload = res.data;
      const hasEmpty = (payload.rows || []).some((r) => !r.teacherId);
      if (allowAutoSuggest && hasEmpty && !autoSuggestedRef.current) {
        autoSuggestedRef.current = true;
        const sugRes = await api.post(`/timetables/${draft.id}/teachers/suggest`);
        setData(sugRes.data);
      } else {
        setData(payload);
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load teacher assignments.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    autoSuggestedRef.current = false;
    loadTeachers(true);
  }, [draft.id]);

  async function handleSuggest() {
    setSuggesting(true);
    setError('');
    try {
      const res = await api.post(`/timetables/${draft.id}/teachers/suggest`);
      setData(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not suggest teachers.'));
    } finally {
      setSuggesting(false);
    }
  }

  async function handleChangeTeacher(row, teacherIdValue) {
    const key = `${row.sectionId}|${row.subjectId}`;
    setSavingKey(key);
    setError('');
    try {
      const res = await api.put(`/timetables/${draft.id}/teachers`, {
        sectionId: row.sectionId,
        subjectId: row.subjectId,
        teacherId: teacherIdValue || null,
      });
      setData(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not update teacher choice.'));
    } finally {
      setSavingKey(null);
    }
  }

  const rows = data?.rows || [];
  const allTeachers = data?.allTeachers || [];
  const totals = data?.totals || [];
  const errors = data?.errors || [];
  const warnings = data?.warnings || [];

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Step 4: Teachers</h2>
          <p className="mt-1 text-sm text-slate-600">
            Review who teaches each subject. You can change any teacher—workload and conflicts are
            re-checked after every change.
          </p>
        </div>

        <button
          type="button"
          disabled={loading || suggesting}
          onClick={handleSuggest}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          {suggesting ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <Sparkles className="h-3.5 w-3.5 text-blue-700" />
          )}
          <span>{suggesting ? 'Filling empty rows...' : 'Fill empty rows'}</span>
        </button>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading teacher assignments...</span>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
          {/* Left: Assignment table */}
          <div className="lg:col-span-8 space-y-4">
            {errors.length > 0 && (
              <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-800 space-y-1">
                <div className="font-semibold flex items-center gap-1.5">
                  <XCircle className="h-3.5 w-3.5 text-red-600" />
                  <span>Fix these before continuing:</span>
                </div>
                <ul className="list-disc pl-5 space-y-0.5">
                  {errors.map((msg, idx) => (
                    <li key={idx}>{msg}</li>
                  ))}
                </ul>
              </div>
            )}

            {warnings.length > 0 && (
              <div className="rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-900 space-y-1">
                <div className="font-semibold flex items-center gap-1.5">
                  <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />
                  <span>Warnings (allowed):</span>
                </div>
                <ul className="list-disc pl-5 space-y-0.5">
                  {warnings.map((msg, idx) => (
                    <li key={idx}>{msg}</li>
                  ))}
                </ul>
              </div>
            )}

            <div className="rounded-md border border-slate-200 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-500">
                    <th className="px-3 py-2.5 w-24">Class</th>
                    <th className="px-3 py-2.5">Subject</th>
                    <th className="px-3 py-2.5 w-20 text-center">Lec / wk</th>
                    <th className="px-3 py-2.5 w-64">Teacher</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200">
                  {rows.map((row) => {
                    const rowKey = `${row.sectionId}|${row.subjectId}`;
                    const isEmpty = !row.teacherId;
                    const ableSet = new Set(row.ableTeacherIds || []);
                    const ableList = allTeachers.filter((t) => ableSet.has(t.id));
                    const otherList = allTeachers.filter((t) => !ableSet.has(t.id));
                    const isSaving = savingKey === rowKey;

                    return (
                      <tr
                        key={rowKey}
                        className={isEmpty ? 'bg-amber-50/60' : 'hover:bg-slate-50/50'}
                      >
                        <td className="px-3 py-2.5 font-medium text-slate-900">
                          Section {row.sectionName}
                        </td>
                        <td className="px-3 py-2.5">
                          <div className="text-slate-900">
                            {row.subjectCode} — {row.subjectName}
                          </div>
                          <div className="text-xs text-slate-500">
                            {row.lab ? 'Lab' : 'Theory'}
                            {row.runTogetherGroup ? ` · Runs with ${row.runTogetherGroup}` : ''}
                            {row.notListedAsAble && (
                              <span className="ml-1.5 text-amber-700 font-medium">
                                · Not listed as able
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-2.5 text-center text-slate-700">
                          {row.lecturesPerWeek}
                        </td>
                        <td className="px-3 py-2.5">
                          <div className="flex items-center gap-2">
                            <select
                              value={row.teacherId || ''}
                              disabled={isSaving}
                              onChange={(e) => handleChangeTeacher(row, e.target.value)}
                              className={`block w-full rounded-md border px-2.5 py-1.5 text-xs focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600 ${
                                isEmpty
                                  ? 'border-amber-400 bg-white text-amber-900 font-medium'
                                  : 'border-slate-300 bg-white text-slate-900'
                              }`}
                            >
                              <option value="">Needs a teacher...</option>
                              {ableList.length > 0 && (
                                <optgroup label="Able to teach this">
                                  {ableList.map((t) => (
                                    <option key={t.id} value={t.id}>
                                      {t.fullName} ({t.code})
                                    </option>
                                  ))}
                                </optgroup>
                              )}
                              {otherList.length > 0 && (
                                <optgroup label="Other active teachers">
                                  {otherList.map((t) => (
                                    <option key={t.id} value={t.id}>
                                      {t.fullName} ({t.code}) — Not listed as able to teach this
                                    </option>
                                  ))}
                                </optgroup>
                              )}
                            </select>
                            {isSaving && (
                              <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-700 shrink-0" />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Right: Teacher workload bars */}
          <div className="lg:col-span-4 rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Teacher workload
            </h3>
            {totals.length === 0 ? (
              <p className="text-xs text-slate-500">No teachers assigned yet.</p>
            ) : (
              <div className="space-y-3">
                {totals.map((item) => {
                  const denom = Math.max(1, item.freeSlots);
                  const pct = Math.min(100, Math.round((item.assignedLectures / denom) * 100));
                  return (
                    <div key={item.teacherId} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-medium text-slate-800 truncate pr-2">
                          {item.teacherName}
                        </span>
                        <span
                          className={`shrink-0 font-medium ${
                            item.overloaded ? 'text-red-700' : 'text-slate-600'
                          }`}
                        >
                          {item.assignedLectures} / {item.freeSlots} free
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
                        <div
                          className={`h-full rounded-full ${
                            item.overloaded ? 'bg-red-600' : 'bg-blue-700'
                          }`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      {item.busyLectures > 0 && (
                        <p className="text-[11px] text-slate-500">
                          {item.busyLectures} already busy in other saved timetables
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
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
          disabled={loading || !data?.ready}
          onClick={onContinue}
          className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-40"
        >
          <span>Continue to fixed lectures</span>
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
