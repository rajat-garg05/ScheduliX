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
      const res = await api.get(`/timetables/${draft.id}/teachers`); const payload = res.data; const hasEmpty = (payload.rows \vert{}\vert{} []).some((r) => !r.teacherId); if (allowAutoSuggest && hasEmpty && !autoSuggestedRef.current) { autoSuggestedRef.current = true; const sugRes = await api.post(`/timetables/${draft.id}/teachers/suggest`);
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
      const res = await api.post(`/timetables/${draft.id}/teachers/suggest`); setData(res.data); } catch (err) { setError(getErrorMessage(err, 'Could not suggest teachers.')); } finally { setSuggesting(false); } }  async function handleChangeTeacher(row, teacherIdValue) { const key = `${row.sectionId}|${row.subjectId}`; setSavingKey(key); setError(''); try { const res = await api.put(`/timetables/${draft.id}/teachers`, {
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
                    const rowKey = `${row.sectionId}\vert{}${row.subjectId}`;
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
                            {row.runTogetherGroup ? ` · Runs with \${row.runTogetherGroup}` : ''}
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
                              className={`block w-full rounded-md border px-2.5 py-1.5 text-xs focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600 ${ isEmpty ? 'border-amber-400 bg-white text-amber-900 font-medium' : 'border-slate-300 bg-white text-slate-900' }`} > <option value="">Needs a teacher...</option> {ableList.length > 0 && ( <optgroup label="Able to teach this"> {ableList.map((t) => ( <option key={t.id} value={t.id}> {t.fullName} ({t.code}) </option> ))} </optgroup> )} {otherList.length > 0 && ( <optgroup label="Other active teachers"> {otherList.map((t) => ( <option key={t.id} value={t.id}> {t.fullName} ({t.code}) — Not listed as able to teach this </option> ))} </optgroup> )} </select> {isSaving && ( <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-700 shrink-0" /> )} </div> </td> </tr> ); })} </tbody> </table> </div> </div>  {/* Right: Teacher workload bars */} <div className="lg:col-span-4 rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-3"> <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500"> Teacher workload </h3> {totals.length === 0 ? ( <p className="text-xs text-slate-500">No teachers assigned yet.</p> ) : ( <div className="space-y-3"> {totals.map((item) => { const denom = Math.max(1, item.freeSlots); const pct = Math.min(100, Math.round((item.assignedLectures / denom) * 100)); return ( <div key={item.teacherId} className="space-y-1"> <div className="flex items-center justify-between text-xs"> <span className="font-medium text-slate-800 truncate pr-2"> {item.teacherName} </span> <span className={`shrink-0 font-medium ${
                            item.overloaded ? 'text-red-700' : 'text-slate-600'
                          }`}
                        >
                          {item.assignedLectures} / {item.freeSlots} free
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
                        <div
                          className={`h-full rounded-full \${
                            item.overloaded ? 'bg-red-600' : 'bg-blue-700'
                          }`}
                          style={{ width: `${pct}\%` }} /> </div> {item.busyLectures > 0 && ( <p className="text-[11px] text-slate-500"> {item.busyLectures} already busy in other saved timetables </p> )} </div> ); })} </div> )} </div> </div> )}  <div className="pt-2 flex items-center justify-between"> <button type="button" onClick={onBack} className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50" > <ArrowLeft className="h-4 w-4" /> <span>Back</span> </button> <button type="button" disabled={loading \vert{}\vert{} !data?.ready} onClick={onContinue} className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-40" > <span>Continue to fixed lectures</span> <ArrowRight className="h-4 w-4" /> </button> </div> </div> ); } '@) ```  ---  #### Step 2: Create `Step5FixedLectures.jsx` File path: `frontend/src/features/timetable/Step5FixedLectures.jsx`  Run this command: ```powershell [IO.File]::WriteAllText("$PWD\frontend\src\features\timetable\Step5FixedLectures.jsx", @' import { useEffect, useMemo, useState } from 'react'; import { ArrowLeft, CalendarCheck2, Loader2, Pin, Plus, Trash2, X, } from 'lucide-react'; import api, { getErrorMessage } from '../../api/client';  const DAY_LABELS = { MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun', };  function parseSettings(settingsJson) { try { return typeof settingsJson === 'string' ? JSON.parse(settingsJson) : settingsJson; } catch { return { workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], lecturesPerDay: 6, breaks: [{ afterLecture: 3, minutes: 45, label: 'Lunch' }], }; } }  export default function Step5FixedLectures({ draft, onBack, onMakeTimetable }) { const settings = useMemo(() => parseSettings(draft?.settingsJson), [draft?.settingsJson]); const sections = draft?.sections \vert{}\vert{} [];  const [activeSectionId, setActiveSectionId] = useState(sections[0]?.id \vert{}\vert{} ''); const [fixedList, setFixedList] = useState([]); const [teacherRows, setTeacherRows] = useState([]); const [loading, setLoading] = useState(true); const [submitting, setSubmitting] = useState(false); const [removingId, setRemovingId] = useState(null); const [error, setError] = useState(''); const [notice, setNotice] = useState('');  // Quick-add form state const [formSubjectId, setFormSubjectId] = useState(''); const [formDay, setFormDay] = useState(settings.workingDays?.[0] \vert{}\vert{} 'MONDAY'); const [formStart, setFormStart] = useState('1');  // Click-on-grid cell picker state: { dayOfWeek, startLecture } const [cellPicker, setCellPicker] = useState(null); const [cellSubjectId, setCellSubjectId] = useState('');  useEffect(() => { if (sections.length > 0 && !sections.some((s) => s.id === activeSectionId)) { setActiveSectionId(sections[0].id); } }, [sections, activeSectionId]);  async function loadAll() { setLoading(true); setError(''); try { const [fixedRes, teachersRes] = await Promise.all([ api.get(`/timetables/${draft.id}/fixed-lectures`),
        api.get(`/timetables/${draft.id}/teachers`), ]); setFixedList(fixedRes.data \vert{}\vert{} []); setTeacherRows(teachersRes.data?.rows \vert{}\vert{} []); } catch (err) { setError(getErrorMessage(err, 'Could not load fixed lectures.')); } finally { setLoading(false); } }  useEffect(() => { loadAll(); }, [draft.id]);  const subjectsForActiveSection = useMemo( () => teacherRows.filter((r) => r.sectionId === activeSectionId), [teacherRows, activeSectionId] );  const fixedForActiveSection = useMemo( () => fixedList.filter((f) => f.sectionId === activeSectionId), [fixedList, activeSectionId] );  const breakAfterMap = useMemo(() => { const map = new Map(); for (const b of settings.breaks \vert{}\vert{} []) { map.set(Number(b.afterLecture), b); } return map; }, [settings.breaks]);  // Map each (day, lecture) cell to the fixed lecture covering it const cellOccupancy = useMemo(() => { const map = new Map(); for (const item of fixedForActiveSection) { for (let off = 0; off < item.length; off++) { map.set(`${item.dayOfWeek}|${item.startLecture + off}`, { item, isStart: off === 0, }); } } return map; }, [fixedForActiveSection]);  async function submitFixed(sectionId, subjectId, dayOfWeek, startLecture) { if (!sectionId \vert{}\vert{} !subjectId \vert{}\vert{} !dayOfWeek) { setError('Please pick a subject, day, and starting lecture.'); return false; } setSubmitting(true); setError(''); setNotice(''); try { const res = await api.post(`/timetables/${draft.id}/fixed-lectures`, {
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
              className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${ sec.id === activeSectionId ? 'border-blue-700 bg-blue-700 text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50' }`} > Section {sec.name} </button> ))} </div> )}  {loading ? ( <div className="py-10 flex items-center justify-center gap-2 text-sm text-slate-600"> <Loader2 className="h-4 w-4 animate-spin text-blue-700" /> <span>Loading grid...</span> </div> ) : ( <div className="space-y-5"> {/* Quick add form */} <form onSubmit={handleFormSubmit} className="flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-slate-50 p-3.5" > <div className="flex-1 min-w-[200px]"> <label htmlFor="fixedSubject" className="block text-xs font-medium text-slate-600"> Subject </label> <select id="fixedSubject" value={formSubjectId} onChange={(e) => setFormSubjectId(e.target.value)} className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900" > <option value="">Choose a subject...</option> {subjectsForActiveSection.map((row) => ( <option key={row.subjectId} value={row.subjectId}> {row.subjectCode} — {row.subjectName} {row.lab ? ' (Lab)' : ''} </option> ))} </select> </div>  <div className="w-32"> <label htmlFor="fixedDay" className="block text-xs font-medium text-slate-600"> Day </label> <select id="fixedDay" value={formDay} onChange={(e) => setFormDay(e.target.value)} className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900" > {workingDays.map((d) => ( <option key={d} value={d}> {DAY_LABELS[d] \vert{}\vert{} d} </option> ))} </select> </div>  <div className="w-28"> <label htmlFor="fixedStart" className="block text-xs font-medium text-slate-600"> Start lecture </label> <select id="fixedStart" value={formStart} onChange={(e) => setFormStart(e.target.value)} className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900" > {lectureNumbers.map((num) => ( <option key={num} value={num}> Lecture {num} </option> ))} </select> </div>  <button type="submit" disabled={submitting \vert{}\vert{} !formSubjectId} className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50" > {submitting ? ( <Loader2 className="h-4 w-4 animate-spin" /> ) : ( <Plus className="h-4 w-4" /> )} <span>Pin lecture</span> </button> </form>  {/* Inline cell picker when user clicks an empty grid cell */} {cellPicker && ( <div className="flex flex-wrap items-center gap-2.5 rounded-md border border-blue-200 bg-blue-50/70 px-3.5 py-2.5 text-xs"> <span className="font-medium text-blue-900"> Pin on {DAY_LABELS[cellPicker.dayOfWeek] \vert{}\vert{} cellPicker.dayOfWeek}, Lecture{' '} {cellPicker.startLecture}: </span> <select value={cellSubjectId} onChange={(e) => setCellSubjectId(e.target.value)} className="rounded-md border border-blue-300 bg-white px-2.5 py-1 text-xs text-slate-900" > <option value="">Choose subject...</option> {subjectsForActiveSection.map((row) => ( <option key={row.subjectId} value={row.subjectId}> {row.subjectCode} — {row.subjectName} </option> ))} </select> <button type="button" disabled={submitting \vert{}\vert{} !cellSubjectId} onClick={() => submitFixed( activeSectionId, cellSubjectId, cellPicker.dayOfWeek, cellPicker.startLecture ) } className="rounded-md bg-blue-700 px-3 py-1 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50" > Pin here </button> <button type="button" onClick={() => { setCellPicker(null); setCellSubjectId(''); }} className="text-slate-600 hover:text-slate-900" > Cancel </button> </div> )}  {/* Clickable preview grid */} <div className="rounded-md border border-slate-200 overflow-x-auto"> <table className="w-full text-left text-xs border-collapse"> <thead> <tr className="border-b border-slate-200 bg-slate-50 text-slate-600"> <th className="px-3 py-2 w-24">Lecture</th> {workingDays.map((d) => ( <th key={d} className="px-3 py-2 text-center font-semibold"> {DAY_LABELS[d] \vert{}\vert{} d} </th> ))} </tr> </thead> <tbody className="divide-y divide-slate-200"> {lectureNumbers.map((num) => { const brk = breakAfterMap.get(num); return ( <> <tr key={`lec-${num}`}>
                        <td className="px-3 py-2.5 font-medium text-slate-600 bg-slate-50/50">
                          Lecture {num}
                        </td>
                        {workingDays.map((day) => {
                          const occ = cellOccupancy.get(`${day}\vert{}${num}`);
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
                                className={`w-full py-2.5 rounded text-[11px] transition-colors \${
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
                        <tr key={`brk-${num}`} className="bg-amber-50/70"> <td colSpan={workingDays.length + 1} className="px-3 py-1.5 text-center text-[11px] font-medium text-amber-900" > {brk.label \vert{}\vert{} 'Break'} ({brk.minutes} min) </td> </tr> )} </> ); })} </tbody> </table> </div> </div> )}  <div className="pt-2 flex items-center justify-between"> <button type="button" onClick={onBack} className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50" > <ArrowLeft className="h-4 w-4" /> <span>Back</span> </button> <button type="button" onClick={() => { if (onMakeTimetable) { onMakeTimetable(); } else { setNotice( 'All 5 steps are saved. Generating, keeping lectures, and saving the final timetable will be wired in Prompt 7.' ); } }} className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800" > <CalendarCheck2 className="h-4 w-4" /> <span>Make Timetable</span> </button> </div> </div> ); } '@) ```  ---  #### Step 3: Wire `Step4Teachers` and `Step5FixedLectures` into `MakeTimetablePage.jsx` File path: `frontend/src/pages/MakeTimetablePage.jsx`  Run this command: ```powershell [IO.File]::WriteAllText("$PWD\frontend\src\pages\MakeTimetablePage.jsx", @' import { useState } from 'react'; import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'; import { ArrowLeft, CalendarPlus, Check, Clock, Loader2, Play, Trash2, } from 'lucide-react'; import api, { getErrorMessage } from '../api/client'; import { useAuth } from '../features/auth/authContext'; import ConfirmDialog from '../components/ConfirmDialog'; import Step1Classes from '../features/timetable/Step1Classes'; import Step2Settings from '../features/timetable/Step2Settings'; import Step3Check from '../features/timetable/Step3Check'; import Step4Teachers from '../features/timetable/Step4Teachers'; import Step5FixedLectures from '../features/timetable/Step5FixedLectures';  const STEPS = [ { number: 1, label: 'Classes' }, { number: 2, label: 'Time settings' }, { number: 3, label: 'Check' }, { number: 4, label: 'Teachers' }, { number: 5, label: 'Fixed lectures' }, ];  export default function MakeTimetablePage() { const { user } = useAuth(); const queryClient = useQueryClient(); const isHod = user?.role === 'HOD';  const [activeDraft, setActiveDraft] = useState(null); const [wizardOpen, setWizardOpen] = useState(false); const [step, setStep] = useState(1); const [loadingDraftId, setLoadingDraftId] = useState(null); const [deleteTarget, setDeleteTarget] = useState(null); const [pageError, setPageError] = useState('');  const draftsQuery = useQuery({ queryKey: ['timetable-drafts'], queryFn: async () => { const res = await api.get('/timetables', { params: { status: 'DRAFT' } }); return res.data \vert{}\vert{} []; }, enabled: isHod, });  const deleteDraftMutation = useMutation({ mutationFn: (id) => api.delete(`/timetables/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
      setDeleteTarget(null);
      setPageError('');
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Could not delete this draft.'));
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
    setWizardOpen(true);
    setPageError('');
  }

  async function continueDraft(draftId) {
    setLoadingDraftId(draftId);
    setPageError('');
    try {
      const res = await api.get(`/timetables/${draftId}`); setActiveDraft(res.data); setStep(2); setWizardOpen(true); } catch (err) { setPageError(getErrorMessage(err, 'Could not open this draft.')); } finally { setLoadingDraftId(null); } }  function closeWizard() { setWizardOpen(false); setActiveDraft(null); setStep(1); queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] }); }  async function refreshDraft(id) { const res = await api.get(`/timetables/${id}`);
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
        <ol className="grid grid-cols-2 sm:grid-cols-5 gap-2">
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
                  className={`w-full flex items-center gap-2 rounded-md border px-3 py-2 text-left text-xs font-medium transition-colors \${
                    current
                      ? 'border-blue-700 bg-blue-50 text-blue-800'
                      : done
                        ? 'border-emerald-200 bg-emerald-50/70 text-emerald-800 hover:bg-emerald-50'
                        : 'border-slate-200 bg-white text-slate-400 cursor-default'
                  }`}
                >
                  <span
                    className={`flex h-5 w-5 items-center justify-center rounded-full text-[11px] shrink-0 \${
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
            Start a new timetable for a semester or continue an unfinished draft.
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
            <span>Loading drafts...</span>
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
            ? `Delete the draft "\${deleteTarget.name}"? This removes its time settings, teacher choices, and fixed lectures.`
            : ''
        }
        confirmLabel="Delete draft"
        danger
        loading={deleteDraftMutation.isPending}
        onConfirm={() => deleteDraftMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}