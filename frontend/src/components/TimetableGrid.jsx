import { useMemo } from 'react';
import { Lock, Pin } from 'lucide-react';

const DAY_LABELS = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
};

const SUBJECT_PALETTE = [
  'bg-blue-50 border-blue-200 text-blue-950',
  'bg-emerald-50 border-emerald-200 text-emerald-950',
  'bg-violet-50 border-violet-200 text-violet-950',
  'bg-amber-50 border-amber-200 text-amber-950',
  'bg-cyan-50 border-cyan-200 text-cyan-950',
  'bg-rose-50 border-rose-200 text-rose-950',
  'bg-indigo-50 border-indigo-200 text-indigo-950',
  'bg-teal-50 border-teal-200 text-teal-950',
];

function colorForSubject(code) {
  let hash = 0;
  const text = String(code || '');
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) % SUBJECT_PALETTE.length;
  }
  return SUBJECT_PALETTE[Math.abs(hash) % SUBJECT_PALETTE.length];
}

export default function TimetableGrid({
  view,
  mode = 'class',
  selectedSectionId = null,
  selectedTeacherId = null,
  readOnly = true,
  onToggleKeep = null,
  togglingLectureId = null,
}) {
  const workingDays = view?.settings?.workingDays || [];
  const dayPreview = view?.dayPreview || [];

  const { startCells, coveredCells } = useMemo(() => {
    const lectures = (view?.lectures || []).filter((lec) => {
      if (mode === 'class' && selectedSectionId) {
        return lec.sectionId === selectedSectionId;
      }
      if (mode === 'teacher' && selectedTeacherId) {
        return lec.teacherId === selectedTeacherId;
      }
      return true;
    });

    const starts = new Map();
    const covered = new Set();

    for (const lec of lectures) {
      const startKey = `${lec.dayOfWeek}|${lec.startLecture}`;
      if (!starts.has(startKey)) {
        starts.set(startKey, []);
      }
      starts.get(startKey).push(lec);

      for (let off = 1; off < lec.length; off++) {
        covered.add(`${lec.dayOfWeek}|${lec.startLecture + off}`);
      }
    }

    return { startCells: starts, coveredCells: covered };
  }, [view?.lectures, mode, selectedSectionId, selectedTeacherId]);

  if (!view || workingDays.length === 0 || dayPreview.length === 0) {
    return (
      <div className="rounded-md border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        No timetable data to display.
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white overflow-x-auto">
      <table className="w-full min-w-[680px] border-collapse text-left text-xs">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-slate-600">
            <th className="px-3 py-2.5 w-32 font-semibold">Time</th>
            {workingDays.map((day) => (
              <th
                key={day}
                className="px-3 py-2.5 text-center font-semibold border-l border-slate-200"
              >
                {DAY_LABELS[day] || day}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {dayPreview.map((slot, idx) => {
            if (slot.type === 'BREAK') {
              return (
                <tr key={`brk-${idx}`} className="bg-amber-50/70">
                  <td className="px-3 py-1.5 font-mono text-[11px] text-amber-900">
                    {slot.startTime}–{slot.endTime}
                  </td>
                  <td
                    colSpan={workingDays.length}
                    className="px-3 py-1.5 text-center text-[11px] font-medium text-amber-900 border-l border-amber-200/70"
                  >
                    {slot.label} ({slot.minutes} min)
                  </td>
                </tr>
              );
            }

            const lecNum = slot.lectureNumber;
            return (
              <tr key={`lec-${lecNum}`}>
                <td className="px-3 py-2.5 bg-slate-50/60 align-top">
                  <div className="font-medium text-slate-800">{slot.label}</div>
                  <div className="mt-0.5 font-mono text-[11px] text-slate-500">
                    {slot.startTime}–{slot.endTime}
                  </div>
                </td>

                {workingDays.map((day) => {
                  const cellKey = `${day}|${lecNum}`;
                  if (coveredCells.has(cellKey)) {
                    return null;
                  }

                  const items = startCells.get(cellKey);
                  if (!items || items.length === 0) {
                    return (
                      <td
                        key={cellKey}
                        className="px-2 py-2 border-l border-slate-200 text-center text-slate-300"
                      >
                        —
                      </td>
                    );
                  }

                  const primary = items[0];
                  const isPairedLab = items.length > 1;
                  const span = primary.length || 1;
                  const palette = colorForSubject(primary.subjectCode);
                  const isPinned = primary.fixed || primary.kept;
                  const isBusy = togglingLectureId === primary.id;

                  return (
                    <td
                      key={cellKey}
                      rowSpan={span}
                      className="p-1.5 border-l border-slate-200 align-top"
                    >
                      <div
                        className={`h-full rounded-md border p-2 flex flex-col justify-between gap-1.5 ${palette}`}
                      >
                        <div className="space-y-1">
                          {isPairedLab ? (
                            <>
                              <div className="font-semibold leading-snug">
                                {items
                                  .map((item) => `${item.subjectName} (${item.teacherName})`)
                                  .join(' | ')}
                              </div>
                              <div className="text-[11px] opacity-80 leading-snug">
                                Half the class each, groups swap next week.
                              </div>
                            </>
                          ) : (
                            <>
                              <div className="font-semibold leading-snug">
                                {primary.subjectName}
                              </div>
                              <div className="text-[11px] opacity-85">
                                {mode === 'teacher'
                                  ? `Section ${primary.sectionName}`
                                  : primary.teacherName}
                              </div>
                              {primary.sharedPlace && (
                                <div className="text-[10px] opacity-75">
                                  {primary.sharedPlace}
                                </div>
                              )}
                            </>
                          )}
                        </div>

                        <div className="flex items-center justify-between gap-1 pt-0.5">
                          <span className="text-[10px] font-mono opacity-70">
                            {primary.startTime}–{primary.endTime}
                          </span>

                          {primary.fixed ? (
                            <span
                              title="Fixed lecture from Step 5"
                              className="inline-flex items-center gap-0.5 rounded bg-white/70 px-1.5 py-0.5 text-[10px] font-medium text-slate-700"
                            >
                              <Lock className="h-2.5 w-2.5" />
                              <span>Fixed</span>
                            </span>
                          ) : !readOnly && onToggleKeep ? (
                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => onToggleKeep(primary, !primary.kept)}
                              className={`inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] font-medium transition-colors ${
                                primary.kept
                                  ? 'bg-blue-700 text-white hover:bg-blue-800'
                                  : 'bg-white/70 text-slate-700 hover:bg-white'
                              }`}
                            >
                              <Pin className="h-2.5 w-2.5" />
                              <span>{primary.kept ? 'Kept' : 'Keep this lecture'}</span>
                            </button>
                          ) : isPinned ? (
                            <span className="inline-flex items-center gap-0.5 rounded bg-white/70 px-1.5 py-0.5 text-[10px] font-medium text-blue-800">
                              <Pin className="h-2.5 w-2.5" />
                              <span>Kept</span>
                            </span>
                          ) : null}
                        </div>
                      </div>
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}