import { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  CalendarCheck2,
  Loader2,
  Pin,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

const DAY_LABELS = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
};

function parseSettings(settingsJson) {
  try {
    return typeof settingsJson === 'string' ? JSON.parse(settingsJson) : settingsJson;
  } catch {
    return {
      workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      lecturesPerDay: 6,
      breaks: [{ afterLecture: 3, minutes: 45, label: 'Lunch' }],
    };
  }
}

export default function Step5FixedLectures({ draft, onBack, onMakeTimetable }) {
  const settings = useMemo(() => parseSettings(draft?.settingsJson), [draft?.settingsJson]);
  const sections = draft?.sections || [];

  const [activeSectionId, setActiveSectionId] = useState(sections[0]?.id || '');
  const [fixedList, setFixedList] = useState([]);
  const [teacherRows, setTeacherRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [removingId, setRemovingId] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // Quick-add form state
  const [formSubjectId, setFormSubjectId] = useState('');
  const [formDay, setFormDay] = useState(settings.workingDays?.[0] || 'MONDAY');
  const [formStart, setFormStart] = useState('1');

  // Click-on-grid cell picker state: { dayOfWeek, startLecture }
  const [cellPicker, setCellPicker] = useState(null);
  const [cellSubjectId, setCellSubjectId] = useState('');

  useEffect(() => {
    if (sections.length > 0 && !sections.some((s) => s.id === activeSectionId)) {
      setActiveSectionId(sections[0].id);
    }
  }, [sections, activeSectionId]);

  async function loadAll() {
    setLoading(true);
    setError('');
    try {
      const [fixedRes, teachersRes] = await Promise.all([
        api.get(`/timetables/${draft.id}/fixed-lectures`),
        api.get(`/timetables/${draft.id}/teachers`),
      ]);
      setFixedList(fixedRes.data || []);
      setTeacherRows(teachersRes.data?.rows || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load fixed lectures.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, [draft.id]);

  const subjectsForActiveSection = useMemo(
    () => teacherRows.filter((r) => r.sectionId === activeSectionId),
    [teacherRows, activeSectionId]
  );

  const fixedForActiveSection = useMemo(
    () => fixedList.filter((f) => f.sectionId === activeSectionId),
    [fixedList, activeSectionId]
  );

  const breakAfterMap = useMemo(() => {
    const map = new Map();
    for (const b of settings.breaks || []) {
      map.set(Number(b.afterLecture), b);
    }
    return map;
  }, [settings.breaks]);

  // Map each (day, lecture) cell to the fixed lecture covering it
  const cellOccupancy = useMemo(() => {
    const map = new Map();
    for (const item of fixedForActiveSection) {
      for (let off = 0; off < item.length; off++) {
        map.set(`${item.dayOfWeek}|${item.startLecture + off}`, {
          item,
          isStart: off === 0,
        });
      }
    }
    return map;
  }, [fixedForActiveSection]);

  async function submitFixed(sectionId, subjectId, dayOfWeek, startLecture) {
    if (!sectionId || !subjectId || !dayOfWeek) {
      setError('Please pick a subject, day, and starting lecture.');
      return false;
    }
    setSubmitting(true);
    setError('');
    setNotice('');
    try {
      const res = await api.post(`/timetables/${draft.id}/fixed-lectures`, {
        sectionId,
        subjectId,
        dayOfWeek,
        startLecture: Number(startLecture),
      });
      setFixedList((prev) => [...prev, res.data]);
      setCellPicker(null);
      setCellSubjectId('');
      return true;
    } catch (err) {
      setError(getErrorMessage(err, 'Could not pin this fixed lecture.'));
      return false;
    } finally {
      setSubmitting(false);
    }
  }

  async function handleFormSubmit(e) {
    e.preventDefault();
    const ok = await submitFixed(activeSectionId, formSubjectId, formDay, formStart);
    if (ok) {
      setFormSubjectId('');
    }
  }

  async function handleRemove(fixedId) {
    setRemovingId(fixedId);
    setError('');
    try {
      await api.delete(`/timetables/${draft.id}/fixed-lectures/${fixedId}`);
      setFixedList((prev) => prev.filter((item) => item.id !== fixedId));
    } catch (err) {
      setError(getErrorMessage(err, 'Could not remove fixed lecture.'));
    } finally {
      setRemovingId(null);
    }
  }

  const workingDays = settings.workingDays || [];
  const lecturesPerDay = Number(settings.lecturesPerDay) || 6;
  const lectureNumbers = Array.from({ length: lecturesPerDay }, (_, i) => i + 1);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Step 5: Fixed lectures (optional)</h2>
        <p className="mt-1 text-sm text-slate-600">
          Pin any lecture that must stay at an exact time. Click an empty cell in the grid or use
          the form below—each pin is checked immediately.
        </p>
      </div>

      {error && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} className="text-red-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {notice && (
        <div className="rounded-md border border-blue-200 bg-blue-50 px-3.5 py-2.5 text-sm text-blue-800">
          {notice}
        </div>
      )}

      {/* Section tabs */}
      {sections.length > 1 && (
        <div className="flex flex-wrap gap-2">
          {sections.map((sec) => (
            <button
              key={sec.id}
              type="button"
              onClick={() => {
                setActiveSectionId(sec.id);
                setCellPicker(null);
                setError('');
              }}
              className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                sec.id === activeSectionId
                  ? 'border-blue-700 bg-blue-700 text-white'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              Section {sec.name}
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <div className="py-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading grid...</span>
        </div>
      ) : (
        <div className="space-y-5">
          {/* Quick add form */}
          <form
            onSubmit={handleFormSubmit}
            className="flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-slate-50 p-3.5"
          >
            <div className="flex-1 min-w-[200px]">
              <label htmlFor="fixedSubject" className="block text-xs font-medium text-slate-600">
                Subject
              </label>
              <select
                id="fixedSubject"
                value={formSubjectId}
                onChange={(e) => setFormSubjectId(e.target.value)}
                className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900"
              >
                <option value="">Choose a subject...</option>
                {subjectsForActiveSection.map((row) => (
                  <option key={row.subjectId} value={row.subjectId}>
                    {row.subjectCode} — {row.subjectName}
                    {row.lab ? ' (Lab)' : ''}
                  </option>
                ))}
              </select>
            </div>

            <div className="w-32">
              <label htmlFor="fixedDay" className="block text-xs font-medium text-slate-600">
                Day
              </label>
              <select
                id="fixedDay"
                value={formDay}
                onChange={(e) => setFormDay(e.target.value)}
                className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900"
              >
                {workingDays.map((d) => (
                  <option key={d} value={d}>
                    {DAY_LABELS[d] || d}
                  </option>
                ))}
              </select>
            </div>

            <div className="w-28">
              <label htmlFor="fixedStart" className="block text-xs font-medium text-slate-600">
                Start lecture
              </label>
              <select
                id="fixedStart"
                value={formStart}
                onChange={(e) => setFormStart(e.target.value)}
                className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900"
              >
                {lectureNumbers.map((num) => (
                  <option key={num} value={num}>
                    Lecture {num}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="submit"
              disabled={submitting || !formSubjectId}
              className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
            >
              {submitting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Plus className="h-4 w-4" />
              )}
              <span>Pin lecture</span>
            </button>
          </form>

          {/* Inline cell picker when user clicks an empty grid cell */}
          {cellPicker && (
            <div className="flex flex-wrap items-center gap-2.5 rounded-md border border-blue-200 bg-blue-50/70 px-3.5 py-2.5 text-xs">
              <span className="font-medium text-blue-900">
                Pin on {DAY_LABELS[cellPicker.dayOfWeek] || cellPicker.dayOfWeek}, Lecture{' '}
                {cellPicker.startLecture}:
              </span>
              <select
                value={cellSubjectId}
                onChange={(e) => setCellSubjectId(e.target.value)}
                className="rounded-md border border-blue-300 bg-white px-2.5 py-1 text-xs text-slate-900"
              >
                <option value="">Choose subject...</option>
                {subjectsForActiveSection.map((row) => (
                  <option key={row.subjectId} value={row.subjectId}>
                    {row.subjectCode} — {row.subjectName}
                  </option>
                ))}
              </select>
              <button
                type="button"
                disabled={submitting || !cellSubjectId}
                onClick={() =>
                  submitFixed(
                    activeSectionId,
                    cellSubjectId,
                    cellPicker.dayOfWeek,
                    cellPicker.startLecture
                  )
                }
                className="rounded-md bg-blue-700 px-3 py-1 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50"
              >
                Pin here
              </button>
              <button
                type="button"
                onClick={() => {
                  setCellPicker(null);
                  setCellSubjectId('');
                }}
                className="text-slate-600 hover:text-slate-900"
              >
                Cancel
              </button>
            </div>
          )}

          {/* Clickable preview grid */}
          <div className="rounded-md border border-slate-200 overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-slate-600">
                  <th className="px-3 py-2 w-24">Lecture</th>
                  {workingDays.map((d) => (
                    <th key={d} className="px-3 py-2 text-center font-semibold">
                      {DAY_LABELS[d] || d}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {lectureNumbers.map((num) => {
                  const brk = breakAfterMap.get(num);
                  return (
                    <>
                      <tr key={`lec-${num}`}>
                        <td className="px-3 py-2.5 font-medium text-slate-600 bg-slate-50/50">
                          Lecture {num}
                        </td>
                        {workingDays.map((day) => {
                          const occ = cellOccupancy.get(`${day}|${num}`);
                          const isPicked =
                            cellPicker?.dayOfWeek === day && cellPicker?.startLecture === num;

                          if (occ) {
                            return (
                              <td
                                key={`${day}-${num}`}
                                className="px-2 py-2 border-l border-slate-200 bg-blue-50/80"
                              >
                                <div className="flex items-start justify-between gap-1">
                                  <div className="min-w-0">
                                    <div className="font-semibold text-blue-900 flex items-center gap-1">
                                      <Pin className="h-3 w-3 shrink-0 text-blue-700" />
                                      <span className="truncate">{occ.item.subjectCode}</span>
                                    </div>
                                    <div className="text-[11px] text-blue-800 truncate">
                                      {occ.item.teacherName}
                                    </div>
                                  </div>
                                  {occ.isStart && (
                                    <button
                                      type="button"
                                      disabled={removingId === occ.item.id}
                                      onClick={() => handleRemove(occ.item.id)}
                                      title="Unpin"
                                      className="p-0.5 rounded text-blue-700 hover:bg-red-50 hover:text-red-700"
                                    >
                                      <Trash2 className="h-3.5 w-3.5" />
                                    </button>
                                  )}
                                </div>
                              </td>
                            );
                          }

                          return (
                            <td
                              key={`${day}-${num}`}
                              className="p-1 border-l border-slate-200 text-center"
                            >
                              <button
                                type="button"
                                onClick={() => {
                                  setCellPicker({ dayOfWeek: day, startLecture: num });
                                  setCellSubjectId('');
                                  setError('');
                                }}
                                className={`w-full py-2.5 rounded text-[11px] transition-colors ${
                                  isPicked
                                    ? 'bg-blue-100 text-blue-800 font-medium'
                                    : 'text-slate-400 hover:bg-slate-100 hover:text-slate-700'
                                }`}
                              >
                                {isPicked ? 'Selected' : '+ Pin'}
                              </button>
                            </td>
                          );
                        })}
                      </tr>
                      {brk && (
                        <tr key={`brk-${num}`} className="bg-amber-50/70">
                          <td
                            colSpan={workingDays.length + 1}
                            className="px-3 py-1.5 text-center text-[11px] font-medium text-amber-900"
                          >
                            {brk.label || 'Break'} ({brk.minutes} min)
                          </td>
                        </tr>
                      )}
                    </>
                  );
                })}
              </tbody>
            </table>
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
          onClick={() => {
            if (onMakeTimetable) {
              onMakeTimetable();
            } else {
              setNotice(
                'All 5 steps are saved. Generating, keeping lectures, and saving the final timetable will be wired in Prompt 7.'
              );
            }
          }}
          className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
        >
          <CalendarCheck2 className="h-4 w-4" />
          <span>Make Timetable</span>
        </button>
      </div>
    </div>
  );
}