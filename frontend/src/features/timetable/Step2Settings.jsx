import { useMemo, useState } from 'react';
import { AlertTriangle, ArrowLeft, ArrowRight, Loader2, Plus, Trash2 } from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

const ALL_DAYS = [
  { key: 'MONDAY', label: 'Mon' },
  { key: 'TUESDAY', label: 'Tue' },
  { key: 'WEDNESDAY', label: 'Wed' },
  { key: 'THURSDAY', label: 'Thu' },
  { key: 'FRIDAY', label: 'Fri' },
  { key: 'SATURDAY', label: 'Sat' },
  { key: 'SUNDAY', label: 'Sun' },
];

const DEFAULT_SETTINGS = {
  workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  startTime: '09:00',
  lectureMinutes: 50,
  lecturesPerDay: 6,
  breaks: [{ afterLecture: 3, minutes: 45, label: 'Lunch' }],
  maxPerTeacherPerDay: 5,
};

function parseInitialSettings(settingsJson) {
  if (!settingsJson) return DEFAULT_SETTINGS;
  try {
    const parsed = typeof settingsJson === 'string' ? JSON.parse(settingsJson) : settingsJson;
    return {
      workingDays: parsed.workingDays || DEFAULT_SETTINGS.workingDays,
      startTime: parsed.startTime || '09:00',
      lectureMinutes: parsed.lectureMinutes ?? 50,
      lecturesPerDay: parsed.lecturesPerDay ?? 6,
      breaks: Array.isArray(parsed.breaks) ? parsed.breaks : [],
      maxPerTeacherPerDay: parsed.maxPerTeacherPerDay ?? 5,
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

function formatMinutes(total) {
  const h = Math.floor(total / 60);
  const m = total % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function buildClientDayPreview(form) {
  const parts = String(form.startTime || '').split(':');
  if (parts.length !== 2) return [];
  const startH = Number(parts[0]);
  const startM = Number(parts[1]);
  const lecMin = Number(form.lectureMinutes);
  const count = Number(form.lecturesPerDay);
  if (
    Number.isNaN(startH) ||
    Number.isNaN(startM) ||
    !Number.isInteger(lecMin) ||
    lecMin < 20 ||
    !Number.isInteger(count) ||
    count < 1 ||
    count > 12
  ) {
    return [];
  }

  const breakMap = new Map();
  for (const b of form.breaks || []) {
    const pos = Number(b.afterLecture);
    const mins = Number(b.minutes);
    if (pos >= 1 && pos < count && mins > 0 && !breakMap.has(pos)) {
      breakMap.set(pos, { minutes: mins, label: b.label?.trim() || 'Break' });
    }
  }

  let cursor = startH * 60 + startM;
  const slots = [];
  for (let i = 1; i <= count; i++) {
    const end = cursor + lecMin;
    slots.push({
      type: 'LECTURE',
      label: `Lecture ${i}`,
      startTime: formatMinutes(cursor),
      endTime: formatMinutes(end),
      minutes: lecMin,
    });
    cursor = end;

    const brk = breakMap.get(i);
    if (brk) {
      const brkEnd = cursor + brk.minutes;
      slots.push({
        type: 'BREAK',
        label: brk.label,
        startTime: formatMinutes(cursor),
        endTime: formatMinutes(brkEnd),
        minutes: brk.minutes,
      });
      cursor = brkEnd;
    }
  }
  return slots;
}

export default function Step2Settings({ draft, onBack, onSaved }) {
  const [form, setForm] = useState(() => parseInitialSettings(draft?.settingsJson));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const preview = useMemo(() => buildClientDayPreview(form), [form]);

  function toggleDay(dayKey) {
    setForm((prev) => {
      const exists = prev.workingDays.includes(dayKey);
      const next = exists
        ? prev.workingDays.filter((d) => d !== dayKey)
        : [...prev.workingDays, dayKey];
      return { ...prev, workingDays: next };
    });
    setError('');
  }

  function addBreak() {
    const used = new Set(form.breaks.map((b) => Number(b.afterLecture)));
    let nextAfter = Math.min(3, Math.max(1, Number(form.lecturesPerDay) - 1));
    for (let i = 1; i < Number(form.lecturesPerDay); i++) {
      if (!used.has(i)) {
        nextAfter = i;
        break;
      }
    }
    setForm((prev) => ({
      ...prev,
      breaks: [...prev.breaks, { afterLecture: nextAfter, minutes: 15, label: 'Break' }],
    }));
  }

  function updateBreak(index, field, value) {
    setForm((prev) => ({
      ...prev,
      breaks: prev.breaks.map((b, i) => (i === index ? { ...b, [field]: value } : b)),
    }));
    setError('');
  }

  function removeBreak(index) {
    setForm((prev) => ({
      ...prev,
      breaks: prev.breaks.filter((_, i) => i !== index),
    }));
  }

  async function handleSave() {
    setSaving(true);
    setError('');
    try {
      const payload = {
        workingDays: form.workingDays,
        startTime: form.startTime,
        lectureMinutes: Number(form.lectureMinutes),
        lecturesPerDay: Number(form.lecturesPerDay),
        breaks: form.breaks.map((b) => ({
          afterLecture: Number(b.afterLecture),
          minutes: Number(b.minutes),
          label: b.label,
        })),
        maxPerTeacherPerDay: Number(form.maxPerTeacherPerDay),
      };
      const res = await api.put(`/timetables/${draft.id}/settings`, payload);
      onSaved(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save time settings.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Step 2: Time settings</h2>
        <p className="mt-1 text-sm text-slate-600">
          Set the working days, lecture timings, breaks, and daily teacher limit for {draft.name}.
        </p>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          {error}
        </div>
      )}

      {(draft?.fixedLectureCount || 0) > 0 && (
        <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-900">
          <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
          <span>
            If you change the time settings, any fixed lectures you already pinned will be cleared.
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: Form controls */}
        <div className="lg:col-span-7 space-y-5">
          {/* Working days chips */}
          <div>
            <span className="block text-sm font-medium text-slate-700">Working days</span>
            <div className="mt-2 flex flex-wrap gap-2">
              {ALL_DAYS.map((day) => {
                const active = form.workingDays.includes(day.key);
                return (
                  <button
                    key={day.key}
                    type="button"
                    onClick={() => toggleDay(day.key)}
                    className={`rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ${
                      active
                        ? 'border-blue-700 bg-blue-700 text-white'
                        : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    {day.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="startTime" className="block text-sm font-medium text-slate-700">
                Day starts at
              </label>
              <input
                id="startTime"
                type="time"
                value={form.startTime}
                onChange={(e) => setForm({ ...form, startTime: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>

            <div>
              <label htmlFor="lecMinutes" className="block text-sm font-medium text-slate-700">
                Lecture length (minutes)
              </label>
              <input
                id="lecMinutes"
                type="number"
                min={20}
                max={180}
                value={form.lectureMinutes}
                onChange={(e) => setForm({ ...form, lectureMinutes: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>

            <div>
              <label htmlFor="lecPerDay" className="block text-sm font-medium text-slate-700">
                Lectures per day
              </label>
              <input
                id="lecPerDay"
                type="number"
                min={1}
                max={12}
                value={form.lecturesPerDay}
                onChange={(e) => setForm({ ...form, lecturesPerDay: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>

            <div>
              <label htmlFor="maxTeacherDay" className="block text-sm font-medium text-slate-700">
                Most lectures one teacher may take in a day
              </label>
              <input
                id="maxTeacherDay"
                type="number"
                min={1}
                max={Math.max(1, Number(form.lecturesPerDay) || 12)}
                value={form.maxPerTeacherPerDay}
                onChange={(e) => setForm({ ...form, maxPerTeacherPerDay: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
          </div>

          {/* Breaks and lunch */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-700">Breaks and lunch</span>
              <button
                type="button"
                disabled={Number(form.lecturesPerDay) <= 1}
                onClick={addBreak}
                className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Add break</span>
              </button>
            </div>

            {form.breaks.length === 0 ? (
              <p className="text-xs text-slate-500">No breaks added.</p>
            ) : (
              <div className="space-y-2">
                {form.breaks.map((brk, idx) => (
                  <div
                    key={idx}
                    className="flex flex-wrap items-center gap-2 rounded-md border border-slate-200 bg-slate-50 p-2.5 text-sm"
                  >
                    <span className="text-xs text-slate-600">After lecture</span>
                    <input
                      type="number"
                      min={1}
                      max={Math.max(1, Number(form.lecturesPerDay) - 1)}
                      value={brk.afterLecture}
                      onChange={(e) => updateBreak(idx, 'afterLecture', e.target.value)}
                      className="w-16 rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900"
                    />
                    <span className="text-xs text-slate-600">for</span>
                    <input
                      type="number"
                      min={5}
                      max={180}
                      value={brk.minutes}
                      onChange={(e) => updateBreak(idx, 'minutes', e.target.value)}
                      className="w-16 rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900"
                    />
                    <span className="text-xs text-slate-600">min —</span>
                    <input
                      type="text"
                      value={brk.label}
                      onChange={(e) => updateBreak(idx, 'label', e.target.value)}
                      placeholder="Lunch"
                      className="flex-1 min-w-[100px] rounded-md border border-slate-300 bg-white px-2.5 py-1 text-sm text-slate-900"
                    />
                    <button
                      type="button"
                      onClick={() => removeBreak(idx)}
                      title="Remove break"
                      className="p-1.5 rounded text-slate-500 hover:bg-red-50 hover:text-red-700"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right: Live day preview */}
        <div className="lg:col-span-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            Day preview
          </h3>
          {preview.length === 0 ? (
            <p className="mt-3 text-xs text-slate-500">
              Enter a valid start time, lecture length, and lectures per day to see the schedule.
            </p>
          ) : (
            <div className="mt-3 space-y-1.5">
              {preview.map((slot, idx) => (
                <div
                  key={idx}
                  className={`flex items-center justify-between rounded-md px-3 py-2 text-xs ${
                    slot.type === 'BREAK'
                      ? 'bg-amber-50 border border-amber-200 text-amber-900 font-medium'
                      : 'bg-white border border-slate-200 text-slate-800'
                  }`}
                >
                  <span>{slot.label}</span>
                  <span className="font-mono text-slate-600">
                    {slot.startTime} – {slot.endTime}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

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
          disabled={saving}
          onClick={handleSave}
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