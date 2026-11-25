import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, ArrowRight, Loader2, Lock } from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

export default function Step1Classes({ draft, onSaved }) {
  const isEditingExisting = Boolean(draft?.id);
  const [semesterId, setSemesterId] = useState(draft?.semesterId || '');
  const [selectedIds, setSelectedIds] = useState(
    () => (draft?.sections || []).map((s) => s.id)
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (draft?.semesterId) {
      setSemesterId(draft.semesterId);
      setSelectedIds((draft.sections || []).map((s) => s.id));
    }
  }, [draft?.id]);

  const semestersQuery = useQuery({
    queryKey: ['semesters'],
    queryFn: async () => {
      const res = await api.get('/semesters');
      return res.data || [];
    },
  });

  const semesters = semestersQuery.data || [];

  useEffect(() => {
    if (!semesterId && semesters.length > 0) {
      setSemesterId(semesters[0].id);
    }
  }, [semesters, semesterId]);

  const sectionsWithPlanQuery = useQuery({
    queryKey: ['wizard-sections-status', semesterId],
    queryFn: async () => {
      const secRes = await api.get(`/semesters/${semesterId}/sections`);
      const sections = secRes.data || [];
      const detailed = await Promise.all(
        sections.map(async (sec) => {
          try {
            const planRes = await api.get(`/sections/${sec.id}/plan`);
            return {
              ...sec,
              subjectCount: planRes.data?.subjectCount || 0,
              totalLectures: planRes.data?.totalLecturesPerWeek || 0,
            };
          } catch {
            return { ...sec, subjectCount: 0, totalLectures: 0 };
          }
        })
      );
      return detailed;
    },
    enabled: Boolean(semesterId),
  });

  const sections = sectionsWithPlanQuery.data || [];

  function toggleSection(id) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
    setError('');
  }

  const originalIds = (draft?.sections || []).map((s) => s.id).sort().join(',');
  const currentIds = [...selectedIds].sort().join(',');
  const classesChanged = isEditingExisting && originalIds !== currentIds;
  const willClearDependent =
    classesChanged && ((draft?.teacherChoiceCount || 0) > 0 || (draft?.fixedLectureCount || 0) > 0);

  async function handleContinue() {
    if (!semesterId) {
      setError('Please pick a semester.');
      return;
    }
    if (selectedIds.length === 0) {
      setError('Please tick at least one section.');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const res = isEditingExisting
        ? await api.put(`/timetables/${draft.id}/classes`, { sectionIds: selectedIds })
        : await api.post('/timetables', { semesterId, sectionIds: selectedIds });
      onSaved(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save the chosen classes.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Step 1: Choose classes</h2>
        <p className="mt-1 text-sm text-slate-600">
          Pick one semester and tick the sections you want to build a timetable for together.
        </p>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Semester selector */}
      <div className="max-w-sm">
        <label htmlFor="wizardSemester" className="block text-sm font-medium text-slate-700">
          Semester
        </label>
        {semestersQuery.isLoading ? (
          <div className="mt-2 flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
            <span>Loading semesters...</span>
          </div>
        ) : semesters.length === 0 ? (
          <div className="mt-2 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
            No semesters found.{' '}
            <Link to="/setup/semester-plans" className="font-medium text-blue-700 hover:underline">
              Create a semester first
            </Link>
            .
          </div>
        ) : (
          <select
            id="wizardSemester"
            value={semesterId}
            disabled={isEditingExisting}
            onChange={(e) => {
              setSemesterId(e.target.value);
              setSelectedIds([]);
              setError('');
            }}
            className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600 disabled:bg-slate-100 disabled:text-slate-600"
          >
            {semesters.map((sem) => (
              <option key={sem.id} value={sem.id}>
                {sem.name}
              </option>
            ))}
          </select>
        )}
        {isEditingExisting && (
          <p className="mt-1 text-xs text-slate-500">
            To pick a different semester, start a new timetable from the list.
          </p>
        )}
      </div>

      {/* Sections list */}
      {semesterId && (
        <div className="space-y-2">
          <span className="block text-sm font-medium text-slate-700">Sections</span>
          {sectionsWithPlanQuery.isLoading ? (
            <div className="flex items-center gap-2 py-4 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
              <span>Checking sections...</span>
            </div>
          ) : sections.length === 0 ? (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
              No sections in this semester yet.{' '}
              <Link to="/setup/semester-plans" className="font-medium text-blue-700 hover:underline">
                Add sections in Semester plans
              </Link>
              .
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {sections.map((sec) => {
                const blockedBySaved = sec.locked;
                const blockedByPlan = !blockedBySaved && sec.subjectCount === 0;
                const disabled = blockedBySaved || blockedByPlan;
                const checked = selectedIds.includes(sec.id);

                return (
                  <div
                    key={sec.id}
                    className={`rounded-md border p-3.5 transition-colors ${
                      disabled
                        ? 'border-slate-200 bg-slate-50 text-slate-500'
                        : checked
                          ? 'border-blue-600 bg-blue-50/50'
                          : 'border-slate-200 bg-white hover:border-slate-300'
                    }`}
                  >
                    <label
                      className={`flex items-start gap-2.5 ${
                        disabled ? 'cursor-not-allowed' : 'cursor-pointer'
                      }`}
                    >
                      <input
                        type="checkbox"
                        disabled={disabled}
                        checked={checked}
                        onChange={() => toggleSection(sec.id)}
                        className="mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5 text-sm font-medium text-slate-900">
                          <span>Section {sec.name}</span>
                          {blockedBySaved && <Lock className="h-3.5 w-3.5 text-amber-600" />}
                        </div>

                        {blockedBySaved ? (
                          <p className="mt-1 text-xs text-slate-500">
                            Already has a saved timetable.{' '}
                            <Link
                              to="/timetables"
                              className="font-medium text-blue-700 hover:underline"
                            >
                              Delete its saved timetable first
                            </Link>
                            .
                          </p>
                        ) : blockedByPlan ? (
                          <p className="mt-1 text-xs text-slate-500">
                            No semester plan yet.{' '}
                            <Link
                              to="/setup/semester-plans"
                              className="font-medium text-blue-700 hover:underline"
                            >
                              Add its semester plan first
                            </Link>
                            .
                          </p>
                        ) : (
                          <p className="mt-1 text-xs text-slate-600">
                            {sec.subjectCount} subjects · {sec.totalLectures} lectures / week
                          </p>
                        )}
                      </div>
                    </label>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {willClearDependent && (
        <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-sm text-amber-900">
          <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
          <span>
            Changing the classes will clear the teachers you chose and any fixed lectures in this
            draft.
          </span>
        </div>
      )}

      <div className="pt-2 flex justify-end">
        <button
          type="button"
          disabled={saving || selectedIds.length === 0}
          onClick={handleContinue}
          className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          <span>{saving ? 'Saving...' : 'Save and continue'}</span>
          {!saving && <ArrowRight className="h-4 w-4" />}
        </button>
      </div>
    </div>
  );
}