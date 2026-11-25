import { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowLeft,
  CalendarCheck2,
  CheckCircle2,
  Loader2,
  Pin,
  RefreshCw,
  Save,
  Users,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';
import ConfirmDialog from '../../components/ConfirmDialog';
import TimetableGrid from '../../components/TimetableGrid';

export default function Step6Result({
  draft,
  autoMake = false,
  onBackToStep,
  onSaved,
}) {
  const [view, setView] = useState(null);
  const [making, setMaking] = useState(false);
  const [makingAgain, setMakingAgain] = useState(false);
  const [loadingView, setLoadingView] = useState(false);
  const [keepingAll, setKeepingAll] = useState(false);
  const [togglingLectureId, setTogglingLectureId] = useState(null);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const [messages, setMessages] = useState([]);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

  // Active tab: sectionId or 'TEACHERS'
  const [activeTab, setActiveTab] = useState('');
  const [selectedTeacherId, setSelectedTeacherId] = useState('');

  async function runMake() {
    setMaking(true);
    setError('');
    setActionError('');
    setActionSuccess('');
    setMessages([]);
    try {
      const res = await api.post(`/timetables/${draft.id}/make`);
      if (res.data?.ok && res.data?.view) {
        setView(res.data.view);
        if (res.data.view.sections?.length > 0) {
          setActiveTab((prev) => prev || res.data.view.sections[0].id);
        }
      } else {
        setView(null);
        setMessages(res.data?.messages || ['Could not make a timetable with these settings.']);
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Could not make the timetable right now.'));
    } finally {
      setMaking(false);
    }
  }

  async function loadExistingView() {
    setLoadingView(true);
    setError('');
    try {
      const res = await api.get(`/timetables/${draft.id}/view`);
      setView(res.data);
      if (res.data?.sections?.length > 0) {
        setActiveTab(res.data.sections[0].id);
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load the generated timetable.'));
    } finally {
      setLoadingView(false);
    }
  }

  useEffect(() => {
    if (autoMake) {
      runMake();
    } else if ((draft?.lectureCount || 0) > 0) {
      loadExistingView();
    } else {
      runMake();
    }
  }, [draft.id]);

  async function handleToggleKeep(lecture, nextKept) {
    setTogglingLectureId(lecture.id);
    setActionError('');
    setActionSuccess('');
    try {
      const res = await api.put(
        `/timetables/${draft.id}/lectures/${lecture.id}/keep`,
        { kept: nextKept }
      );
      setView(res.data);
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not update this lecture.'));
    } finally {
      setTogglingLectureId(null);
    }
  }

  async function handleKeepAll() {
    setKeepingAll(true);
    setActionError('');
    setActionSuccess('');
    try {
      const res = await api.post(`/timetables/${draft.id}/keep-all`);
      setView(res.data);
      setActionSuccess('All lectures are now pinned.');
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not pin all lectures.'));
    } finally {
      setKeepingAll(false);
    }
  }

  async function handleMakeAgain() {
    setMakingAgain(true);
    setActionError('');
    setActionSuccess('');
    try {
      const res = await api.post(`/timetables/${draft.id}/make-again`);
      if (res.data?.ok && res.data?.view) {
        setView(res.data.view);
        setActionSuccess('Updated all unpinned lectures.');
      } else {
        const reason =
          res.data?.messages?.join(' ') ||
          'Could not find another arrangement with the currently pinned lectures.';
        setActionError(reason);
      }
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not make the timetable again.'));
    } finally {
      setMakingAgain(false);
    }
  }

  async function handleSaveConfirm() {
    setSaving(true);
    setActionError('');
    setActionSuccess('');
    try {
      const res = await api.post(`/timetables/${draft.id}/save`);
      setSaveDialogOpen(false);
      if (onSaved) {
        onSaved(res.data);
      }
    } catch (err) {
      setSaveDialogOpen(false);
      setActionError(getErrorMessage(err, 'Could not save this timetable.'));
    } finally {
      setSaving(false);
    }
  }

  const teachersInTimetable = useMemo(() => {
    const map = new Map();
    for (const lec of view?.lectures || []) {
      if (!map.has(lec.teacherId)) {
        map.set(lec.teacherId, { id: lec.teacherId, name: lec.teacherName, count: 0 });
      }
      map.get(lec.teacherId).count += lec.length;
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [view?.lectures]);

  useEffect(() => {
    if (
      teachersInTimetable.length > 0 &&
      !teachersInTimetable.some((t) => t.id === selectedTeacherId)
    ) {
      setSelectedTeacherId(teachersInTimetable[0].id);
    }
  }, [teachersInTimetable, selectedTeacherId]);

  const keptCount = useMemo(
    () => (view?.lectures || []).filter((l) => l.kept || l.fixed).length,
    [view?.lectures]
  );
  const totalCount = view?.lectures?.length || 0;
  const allPinned = totalCount > 0 && keptCount === totalCount;

  if (making || loadingView) {
    return (
      <div className="py-16 text-center space-y-3">
        <Loader2 className="h-8 w-8 animate-spin text-blue-700 mx-auto" />
        <p className="text-base font-medium text-slate-900">
          {making ? 'Placing lectures... this can take up to a minute' : 'Loading timetable...'}
        </p>
        <p className="text-sm text-slate-500">
          Checking teachers, breaks, and shared labs across all selected classes.
        </p>
      </div>
    );
  }

  if (error || messages.length > 0) {
    return (
      <div className="space-y-5">
        <div className="rounded-lg border border-red-200 bg-red-50 p-5 space-y-3">
          <div className="flex items-start gap-2.5 text-red-900">
            <AlertCircle className="h-5 w-5 shrink-0 text-red-600 mt-0.5" />
            <div>
              <h2 className="text-base font-semibold">Could not make the timetable</h2>
              <p className="mt-0.5 text-sm text-red-800">
                Nothing was lost. Fix the issue below and try again.
              </p>
            </div>
          </div>

          {error && <p className="text-sm text-red-800 pl-7">{error}</p>}

          {messages.length > 0 && (
            <ul className="list-disc pl-12 space-y-1 text-sm text-red-800">
              {messages.map((msg, idx) => (
                <li key={idx}>{msg}</li>
              ))}
            </ul>
          )}

          <div className="pt-2 pl-7 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onBackToStep(2)}
              className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-50"
            >
              Adjust time settings
            </button>
            <button
              type="button"
              onClick={() => onBackToStep(4)}
              className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-50"
            >
              Change teachers
            </button>
            <button
              type="button"
              onClick={() => onBackToStep(5)}
              className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-50"
            >
              Edit fixed lectures
            </button>
            <button
              type="button"
              onClick={runMake}
              className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-800"
            >
              <CalendarCheck2 className="h-3.5 w-3.5" />
              <span>Try again</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  const sections = view?.sections || [];
  const busy = makingAgain || keepingAll || saving;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Review and save</h2>
          <p className="mt-0.5 text-sm text-slate-600">
            Pin the lectures you want to keep, press Make again to rearrange the rest, or save when
            you are happy with it.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={busy || allPinned}
            onClick={handleKeepAll}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {keepingAll ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Pin className="h-3.5 w-3.5" />
            )}
            <span>Keep all</span>
          </button>

          <button
            type="button"
            disabled={busy}
            onClick={handleMakeAgain}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {makingAgain ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <RefreshCw className="h-3.5 w-3.5" />
            )}
            <span>{makingAgain ? 'Making again...' : 'Make again'}</span>
          </button>

          <button
            type="button"
            disabled={busy || totalCount === 0}
            onClick={() => setSaveDialogOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50"
          >
            <Save className="h-3.5 w-3.5" />
            <span>Save timetable</span>
          </button>
        </div>
      </div>

      {actionError && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          <span>{actionError}</span>
          <button type="button" onClick={() => setActionError('')} className="text-red-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {actionSuccess && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <span>{actionSuccess}</span>
          </div>
          <button type="button" onClick={() => setActionSuccess('')} className="text-emerald-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Class tabs + Teachers tab */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-3">
        <div className="flex flex-wrap items-center gap-2">
          {sections.map((sec) => (
            <button
              key={sec.id}
              type="button"
              onClick={() => setActiveTab(sec.id)}
              className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                activeTab === sec.id
                  ? 'border-blue-700 bg-blue-700 text-white'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              Section {sec.name}
            </button>
          ))}

          <button
            type="button"
            onClick={() => setActiveTab('TEACHERS')}
            className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
              activeTab === 'TEACHERS'
                ? 'border-blue-700 bg-blue-700 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            <Users className="h-3.5 w-3.5" />
            <span>Teachers</span>
          </button>
        </div>

        <span className="text-xs text-slate-500">
          {keptCount} of {totalCount} pinned
        </span>
      </div>

      {/* Active tab content */}
      {activeTab === 'TEACHERS' ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <label htmlFor="resultTeacherSelect" className="text-sm font-medium text-slate-700">
              Teacher:
            </label>
            <select
              id="resultTeacherSelect"
              value={selectedTeacherId}
              onChange={(e) => setSelectedTeacherId(e.target.value)}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900"
            >
              {teachersInTimetable.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.count} lectures / week)
                </option>
              ))}
            </select>
          </div>

          <TimetableGrid
            view={view}
            mode="teacher"
            selectedTeacherId={selectedTeacherId}
            readOnly={false}
            onToggleKeep={handleToggleKeep}
            togglingLectureId={togglingLectureId}
          />
        </div>
      ) : (
        <TimetableGrid
          view={view}
          mode="class"
          selectedSectionId={activeTab}
          readOnly={false}
          onToggleKeep={handleToggleKeep}
          togglingLectureId={togglingLectureId}
        />
      )}

      <div className="pt-2 flex items-center justify-between">
        <button
          type="button"
          onClick={() => onBackToStep(5)}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back to fixed lectures</span>
        </button>
      </div>

      <ConfirmDialog
        open={saveDialogOpen}
        title="Save timetable"
        message={`Save "${draft.name}" as the final timetable? Saved timetables cannot be edited—to make changes later, delete it from Classes and Timetables and make it again.`}
        confirmLabel="Save timetable"
        loading={saving}
        onConfirm={handleSaveConfirm}
        onCancel={() => setSaveDialogOpen(false)}
      />
    </div>
  );
}