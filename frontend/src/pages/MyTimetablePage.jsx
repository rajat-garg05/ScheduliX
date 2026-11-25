import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Calendar, CalendarClock, Loader2, UserCheck } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';

const DAY_ORDER = [
  { key: 'MONDAY', label: 'Monday' },
  { key: 'TUESDAY', label: 'Tuesday' },
  { key: 'WEDNESDAY', label: 'Wednesday' },
  { key: 'THURSDAY', label: 'Thursday' },
  { key: 'FRIDAY', label: 'Friday' },
  { key: 'SATURDAY', label: 'Saturday' },
  { key: 'SUNDAY', label: 'Sunday' },
];

export default function MyTimetablePage() {
  const { user } = useAuth();
  const isTeacher = user?.role === 'TEACHER';

  const myQuery = useQuery({
    queryKey: ['my-timetable'],
    queryFn: async () => {
      const res = await api.get('/my/timetable');
      return res.data;
    },
    enabled: isTeacher,
  });

  const data = myQuery.data;
  const weekly = data?.weeklyLectures || [];
  const coveringForOthers = data?.coveringForOthers || [];
  const othersCoveringForMe = data?.othersCoveringForMe || [];

  const lecturesByDay = useMemo(() => {
    const map = new Map();
    for (const day of DAY_ORDER) {
      map.set(day.key, []);
    }
    for (const lec of weekly) {
      if (map.has(lec.dayOfWeek)) {
        map.get(lec.dayOfWeek).push(lec);
      }
    }
    return DAY_ORDER.filter((d) => map.get(d.key).length > 0).map((d) => ({
      ...d,
      lectures: map.get(d.key),
    }));
  }, [weekly]);

  if (!isTeacher) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">My timetable</h1>
        <p className="mt-1.5 text-sm text-slate-600">
          This page is for teacher accounts. Use Classes and Timetables to view department
          schedules.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My timetable</h1>
        <p className="mt-1 text-sm text-slate-600">
          Your regular weekly lectures across all saved timetables and any upcoming substitute
          arrangements.
        </p>
      </div>

      {myQuery.isError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          {getErrorMessage(myQuery.error, 'Could not load your timetable.')}
        </div>
      )}

      {myQuery.isLoading ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700"/>
          <span>Loading your schedule...</span>
        </div>
      ) : (
        <>
          {/* Weekly schedule */}
          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">Weekly schedule</h2>
            {lecturesByDay.length === 0 ? (
              <div className="rounded-lg border border-slate-200 bg-white p-8 text-center">
                <Calendar className="h-7 w-7 text-slate-400 mx-auto"/>
                <p className="mt-2 text-sm font-medium text-slate-900">No lectures assigned yet</p>
                <p className="mt-1 text-xs text-slate-500">
                  Your lectures will appear here once timetables are saved.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {lecturesByDay.map((day) => (
                  <div
                    key={day.key}
                    className="rounded-lg border border-slate-200 bg-white overflow-hidden"
                  >
                    <div className="px-4 py-2.5 border-b border-slate-200 bg-slate-50/70">
                      <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                        {day.label}
                      </h3>
                    </div>
                    <div className="divide-y divide-slate-200">
                      {day.lectures.map((lec) => (
                        <div key={lec.lectureId} className="px-4 py-3 space-y-1">
                          <div className="flex items-center justify-between gap-2 text-xs">
                            <span className="font-mono text-slate-500">
                              {lec.startTime}–{lec.endTime}
                            </span>
                            <span className="text-slate-400">
                              Lecture {lec.startLecture}
                              {lec.length > 1 ? `–${lec.startLecture + lec.length - 1}` : ''}
                            </span>
                          </div>
                          <div className="text-sm font-semibold text-slate-900">
                            {lec.subjectCode} — {lec.subjectName}
                          </div>
                          <div className="text-xs text-slate-600">
                            {lec.semesterName} · Section {lec.sectionName}
                            {lec.sharedPlace ? ` · ${lec.sharedPlace}` : ''}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Upcoming substitutes */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="rounded-lg border border-slate-200 bg-white">
              <div className="px-4 py-3 border-b border-slate-200 bg-slate-50/70 flex items-center gap-2">
                <UserCheck className="h-4 w-4 text-blue-700"/>
                <h2 className="text-sm font-semibold text-slate-900">
                  Upcoming substitutes — lectures you will cover
                </h2>
              </div>
              {coveringForOthers.length === 0 ? (
                <div className="p-6 text-center text-xs text-slate-500">
                  You have no upcoming substitute lectures to cover.
                </div>
              ) : (
                <div className="divide-y divide-slate-200">
                  {coveringForOthers.map((item) => (
                    <div key={item.substituteId} className="px-4 py-3 space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-blue-800">{item.onDate}</span>
                        <span className="font-mono text-slate-500">
                          {item.startTime}–{item.endTime}
                        </span>
                      </div>
                      <div className="text-sm font-medium text-slate-900">
                        {item.subjectCode} — {item.subjectName}
                      </div>
                      <div className="text-xs text-slate-600">
                        {item.semesterName} Section {item.sectionName} · Covering for{' '}
                        {item.otherTeacherName}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white">
              <div className="px-4 py-3 border-b border-slate-200 bg-slate-50/70 flex items-center gap-2">
                <CalendarClock className="h-4 w-4 text-slate-600"/>
                <h2 className="text-sm font-semibold text-slate-900">
                  Upcoming substitutes — your lectures covered by others
                </h2>
              </div>
              {othersCoveringForMe.length === 0 ? (
                <div className="p-6 text-center text-xs text-slate-500">
                  None of your upcoming lectures have a substitute arranged.
                </div>
              ) : (
                <div className="divide-y divide-slate-200">
                  {othersCoveringForMe.map((item) => (
                    <div key={item.substituteId} className="px-4 py-3 space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-slate-800">{item.onDate}</span>
                        <span className="font-mono text-slate-500">
                          {item.startTime}–{item.endTime}
                        </span>
                      </div>
                      <div className="text-sm font-medium text-slate-900">
                        {item.subjectCode} — {item.subjectName}
                      </div>
                      <div className="text-xs text-slate-600">
                        {item.semesterName} Section {item.sectionName} · Covered by{' '}
                        {item.otherTeacherName}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}