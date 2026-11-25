import { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowLeft,
  CalendarCheck2,
  Loader2,
  Users,
} from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';
import TimetableGrid from '../../components/TimetableGrid';

export default function Step6Result({
  draft,
  autoMake = false,
  onBackToStep,
}) {
  const [view, setView] = useState(null);
  const [making, setMaking] = useState(false);
  const [loadingView, setLoadingView] = useState(false);
  const [messages, setMessages] = useState([]);
  const [error, setError] = useState('');

  // Active tab: sectionId or 'TEACHERS'
  const [activeTab, setActiveTab] = useState('');
  const [selectedTeacherId, setSelectedTeacherId] = useState('');

  async function runMake() {
    setMaking(true);
    setError('');
    setMessages([]);
    try {
      const res = await api.post(`/timetables/${draft.id}/make`);
      if (res.data?.ok && res.data?.view) {
        setView(res.data.view);
        if (res.data.view.sections?.length > 0) {
          setActiveTab(res.data.view.sections[0].id);
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
    if (teachersInTimetable.length > 0 && !teachersInTimetable.some((t) => t.id === selectedTeacherId)) {
      setSelectedTeacherId(teachersInTimetable[0].id);
    }
  }, [teachersInTimetable, selectedTeacherId]);

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

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Generated timetable</h2>
          <p className="mt-0.5 text-sm text-slate-600">
            Review each class and teacher schedule below.
          </p>
        </div>

        <button
          type="button"
          onClick={() => onBackToStep(5)}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back to fixed lectures</span>
        </button>
      </div>

      {/* Class tabs + Teachers tab */}
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 pb-3">
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
            readOnly
          />
        </div>
      ) : (
        <TimetableGrid
          view={view}
          mode="class"
          selectedSectionId={activeTab}
          readOnly
        />
      )}
    </div>
  );
}