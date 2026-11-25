import { Pin } from 'lucide-react';

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'];

const ROWS = [
  {
    type: 'lecture',
    number: 1,
    time: '09:00 - 09:50',
    cells: {
      Mon: { subject: 'Data Structures', teacher: 'Prof. Mehta', tone: 'bg-blue-50 border-blue-200 text-blue-900', kept: true },
      Tue: { subject: 'Operating Systems', teacher: 'Prof. Verma', tone: 'bg-emerald-50 border-emerald-200 text-emerald-900' },
      Wed: { subject: 'Data Structures', teacher: 'Prof. Mehta', tone: 'bg-blue-50 border-blue-200 text-blue-900' },
      Thu: { subject: 'Database Systems', teacher: 'Prof. Nair', tone: 'bg-amber-50 border-amber-200 text-amber-900' },
      Fri: { subject: 'Operating Systems', teacher: 'Prof. Verma', tone: 'bg-emerald-50 border-emerald-200 text-emerald-900' },
    },
  },
  {
    type: 'lecture',
    number: 2,
    time: '09:50 - 10:40',
    cells: {
      Mon: { subject: 'Database Systems', teacher: 'Prof. Nair', tone: 'bg-amber-50 border-amber-200 text-amber-900' },
      Tue: {
        subject: 'Computer Networks',
        teacher: 'Prof. Iyer',
        replacedTeacher: 'Prof. Kulkarni',
        tone: 'bg-slate-100 border-slate-300 text-slate-900',
        substitute: true,
      },
      Wed: { subject: 'Discrete Maths', teacher: 'Prof. Joshi', tone: 'bg-violet-50 border-violet-200 text-violet-900' },
      Thu: { subject: 'Data Structures', teacher: 'Prof. Mehta', tone: 'bg-blue-50 border-blue-200 text-blue-900' },
      Fri: { subject: 'Computer Networks', teacher: 'Prof. Kulkarni', tone: 'bg-slate-100 border-slate-300 text-slate-900' },
    },
  },
  {
    type: 'lecture',
    number: 3,
    time: '10:40 - 11:30',
    cells: {
      Mon: { subject: 'Discrete Maths', teacher: 'Prof. Joshi', tone: 'bg-violet-50 border-violet-200 text-violet-900' },
      Tue: { subject: 'Data Structures', teacher: 'Prof. Mehta', tone: 'bg-blue-50 border-blue-200 text-blue-900' },
      Wed: { subject: 'Operating Systems', teacher: 'Prof. Verma', tone: 'bg-emerald-50 border-emerald-200 text-emerald-900' },
      Thu: { subject: 'Discrete Maths', teacher: 'Prof. Joshi', tone: 'bg-violet-50 border-violet-200 text-violet-900' },
      Fri: { subject: 'Database Systems', teacher: 'Prof. Nair', tone: 'bg-amber-50 border-amber-200 text-amber-900', kept: true },
    },
  },
  {
    type: 'break',
    label: 'Lunch break',
    time: '11:30 - 12:15',
  },
  {
    type: 'lecture',
    number: 4,
    time: '12:15 - 13:05',
    cells: {
      Mon: {
        subject: 'DSA Lab / DBMS Lab',
        teacher: 'Prof. Mehta · Prof. Nair',
        note: '2 lectures in a row · groups swap next week',
        tone: 'bg-teal-50 border-teal-200 text-teal-900',
        rowSpan: 2,
      },
      Tue: { subject: 'Database Systems', teacher: 'Prof. Nair', tone: 'bg-amber-50 border-amber-200 text-amber-900' },
      Wed: { subject: 'Computer Networks', teacher: 'Prof. Kulkarni', tone: 'bg-slate-100 border-slate-300 text-slate-900' },
      Thu: { subject: 'Operating Systems', teacher: 'Prof. Verma', tone: 'bg-emerald-50 border-emerald-200 text-emerald-900' },
      Fri: null,
    },
  },
  {
    type: 'lecture',
    number: 5,
    time: '13:05 - 13:55',
    cells: {
      Mon: 'spanned',
      Tue: null,
      Wed: { subject: 'Database Systems', teacher: 'Prof. Nair', tone: 'bg-amber-50 border-amber-200 text-amber-900' },
      Thu: { subject: 'Computer Networks', teacher: 'Prof. Kulkarni', tone: 'bg-slate-100 border-slate-300 text-slate-900' },
      Fri: null,
    },
  },
];

export default function SampleTimetable() {
  return (
    <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
      <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-slate-900">
            Semester 3 — Section A
          </p>
          <p className="text-xs text-slate-500">
            5 working days · 50-minute lectures · paired lab block shown
          </p>
        </div>
        <span className="inline-flex items-center rounded border border-slate-300 bg-white px-2.5 py-0.5 text-xs font-medium text-slate-600">
          Sample
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] border-collapse text-left">
          <thead>
            <tr className="border-b border-slate-200 bg-white text-xs font-semibold text-slate-500">
              <th className="w-32 px-3 py-2.5 border-r border-slate-200">Lecture</th>
              {DAYS.map((day) => (
                <th key={day} className="px-3 py-2.5 border-r last:border-r-0 border-slate-200">
                  {day}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 text-xs">
            {ROWS.map((row, idx) => {
              if (row.type === 'break') {
                return (
                  <tr key={`break-${idx}`} className="bg-slate-50">
                    <td className="px-3 py-2 border-r border-slate-200 text-slate-500 font-medium">
                      {row.time}
                    </td>
                    <td
                      colSpan={DAYS.length}
                      className="px-3 py-2 text-center font-medium text-slate-500 tracking-wide"
                    >
                      {row.label}
                    </td>
                  </tr>
                );
              }

              return (
                <tr key={row.number}>
                  <td className="px-3 py-2.5 border-r border-slate-200 align-top bg-white">
                    <div className="font-semibold text-slate-700">Lecture {row.number}</div>
                    <div className="text-[11px] text-slate-500 mt-0.5">{row.time}</div>
                  </td>
                  {DAYS.map((day) => {
                    const cell = row.cells[day];
                    if (cell === 'spanned') {
                      return null;
                    }
                    if (!cell) {
                      return (
                        <td
                          key={day}
                          className="p-2 border-r last:border-r-0 border-slate-200 bg-slate-50/40 text-slate-400 text-center align-middle"
                        >
                          —
                        </td>
                      );
                    }

                    return (
                      <td
                        key={day}
                        rowSpan={cell.rowSpan || 1}
                        className="p-1.5 border-r last:border-r-0 border-slate-200 align-top"
                      >
                        <div className={`h-full rounded-md border p-2 ${cell.tone}`}>
                          <div className="flex items-start justify-between gap-1">
                            <span className="font-semibold leading-snug">{cell.subject}</span>
                            {cell.kept && (
                              <Pin
                                className="h-3.5 w-3.5 shrink-0 text-slate-600 mt-0.5"
                                aria-label="Kept lecture"
                              />
                            )}
                          </div>

                          {cell.substitute ? (
                            <div className="mt-1 space-y-0.5">
                              <div className="font-medium text-blue-800">{cell.teacher}</div>
                              <div className="text-[11px] text-slate-500 line-through">
                                {cell.replacedTeacher}
                              </div>
                              <span className="inline-block rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-medium text-blue-800">
                                Substitute
                              </span>
                            </div>
                          ) : (
                            <div className="mt-1 text-[11px] opacity-85">{cell.teacher}</div>
                          )}

                          {cell.note && (
                            <div className="mt-1.5 text-[10px] opacity-80 leading-tight">
                              {cell.note}
                            </div>
                          )}
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

      <div className="px-4 py-2.5 border-t border-slate-200 bg-slate-50 flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-slate-500">
        <span className="inline-flex items-center gap-1.5">
          <Pin className="h-3.5 w-3.5 text-slate-600" />
          Fixed or kept lecture
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="inline-block rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-medium text-blue-800">
            Substitute
          </span>
          Daily cover when a teacher is on leave
        </span>
      </div>
    </div>
  );
}