import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowRight,
  Bell,
  BookOpen,
  Calendar,
  CalendarDays,
  CalendarOff,
  CalendarPlus,
  FolderTree,
  Loader2,
  Users,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';

export default function DashboardPage() {
  const { user } = useAuth();
  const isHod = user?.role === 'HOD';

  const dashQuery = useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => {
      const res = await api.get('/dashboard');
      return res.data;
    },
  });

  const noticesQuery = useQuery({
    queryKey: ['dashboard-notifications'],
    queryFn: async () => {
      const res = await api.get('/notifications', { params: { page: 0, size: 5 } });
      return res.data?.items || [];
    },
    enabled: !isHod,
  });

  if (dashQuery.isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
        <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
        <span>Loading dashboard...</span>
      </div>
    );
  }

  if (dashQuery.isError) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        {getErrorMessage(dashQuery.error, 'Could not load dashboard.')}
      </div>
    );
  }

  const data = dashQuery.data;

  if (isHod && data?.hod) {
    const h = data.hod;
    const unsavedClasses = Math.max(0, h.totalSectionCount - h.savedTimetableSectionCount);

    // Pick the most sensible next-step shortcut
    let nextStep = {
      label: 'Make Timetable',
      description: 'Create a new timetable or continue an unfinished draft.',
      to: '/make-timetable',
    };
    if (h.activeTeacherCount === 0) {
      nextStep = {
        label: 'Add teachers',
        description: 'Start by adding your department teachers or uploading a CSV file.',
        to: '/setup/teachers',
      };
    } else if (h.activeSubjectCount === 0) {
      nextStep = {
        label: 'Add subjects',
        description: 'Add your theory subjects and labs so you can build semester plans.',
        to: '/setup/subjects',
      };
    } else if (h.totalSectionCount === 0) {
      nextStep = {
        label: 'Set up semester plans',
        description: 'Create semesters, sections, and weekly lecture counts.',
        to: '/setup/semester-plans',
      };
    } else if (h.todayReviewCount > 0) {
      nextStep = {
        label: 'Review today’s substitutes',
        description: `${h.todayReviewCount} lecture${h.todayReviewCount === 1 ? '' : 's'} need your review on Today.`,
        to: '/today',
      };
    } else if (h.pendingLeaveCount > 0) {
      nextStep = {
        label: 'Review leave requests',
        description: `${h.pendingLeaveCount} leave request${h.pendingLeaveCount === 1 ? '' : 's'} waiting for your decision.`,
        to: '/leave',
      };
    } else if (unsavedClasses > 0) {
      nextStep = {
        label: 'Make a timetable',
        description: `${unsavedClasses} class${unsavedClasses === 1 ? '' : 'es'} still do not have a saved timetable.`,
        to: '/make-timetable',
      };
    }

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-600">
            Overview of {user?.departmentName || 'your department'} for {data.todayDate}.
          </p>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <Link
            to="/setup/teachers"
            className="rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-300 transition-colors"
          >
            <div className="flex items-center justify-between text-xs font-medium text-slate-500">
              <span>Teachers</span>
              <Users className="h-4 w-4 text-slate-400" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">
              {h.activeTeacherCount}
            </div>
          </Link>

          <Link
            to="/setup/subjects"
            className="rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-300 transition-colors"
          >
            <div className="flex items-center justify-between text-xs font-medium text-slate-500">
              <span>Subjects</span>
              <BookOpen className="h-4 w-4 text-slate-400" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">
              {h.activeSubjectCount}
            </div>
          </Link>

          <Link
            to="/timetables"
            className="rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-300 transition-colors"
          >
            <div className="flex items-center justify-between text-xs font-medium text-slate-500">
              <span>Saved timetables</span>
              <FolderTree className="h-4 w-4 text-slate-400" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">
              {h.savedTimetableSectionCount}{' '}
              <span className="text-sm font-normal text-slate-500">
                / {h.totalSectionCount} classes
              </span>
            </div>
          </Link>

          <Link
            to="/leave"
            className={`rounded-lg border p-4 transition-colors ${
              h.pendingLeaveCount > 0
                ? 'border-amber-200 bg-amber-50/60 hover:border-amber-300'
                : 'border-slate-200 bg-white hover:border-slate-300'
            }`}
          >
            <div className="flex items-center justify-between text-xs font-medium text-slate-600">
              <span>Leave waiting</span>
              <CalendarOff className="h-4 w-4 text-amber-600" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">
              {h.pendingLeaveCount}
            </div>
          </Link>

          <Link
            to="/today"
            className={`rounded-lg border p-4 transition-colors ${
              h.todayReviewCount > 0
                ? 'border-amber-200 bg-amber-50/60 hover:border-amber-300'
                : 'border-slate-200 bg-white hover:border-slate-300'
            }`}
          >
            <div className="flex items-center justify-between text-xs font-medium text-slate-600">
              <span>To review today</span>
              <AlertTriangle className="h-4 w-4 text-amber-600" />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">
              {h.todayReviewCount}
            </div>
          </Link>
        </div>

        {/* Next sensible action */}
        <div className="rounded-lg border border-blue-200 bg-blue-50/50 p-5 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-blue-700">
              Recommended next step
            </div>
            <p className="mt-1 text-sm text-slate-800">{nextStep.description}</p>
          </div>
          <Link
            to={nextStep.to}
            className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
          >
            <span>{nextStep.label}</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>

        {/* Quick shortcuts */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Link
            to="/make-timetable"
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
          >
            <span className="inline-flex items-center gap-2">
              <CalendarPlus className="h-4 w-4 text-blue-700" />
              <span>Make Timetable</span>
            </span>
            <ArrowRight className="h-4 w-4 text-slate-400" />
          </Link>

          <Link
            to="/today"
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
          >
            <span className="inline-flex items-center gap-2">
              <CalendarDays className="h-4 w-4 text-blue-700" />
              <span>Open Today</span>
            </span>
            <ArrowRight className="h-4 w-4 text-slate-400" />
          </Link>

          <Link
            to="/leave"
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
          >
            <span className="inline-flex items-center gap-2">
              <CalendarOff className="h-4 w-4 text-blue-700" />
              <span>Manage Leave</span>
            </span>
            <ArrowRight className="h-4 w-4 text-slate-400" />
          </Link>
        </div>
      </div>
    );
  }

  // Teacher dashboard
  const myLectures = data?.teacher?.todayLectures || [];
  const notices = noticesQuery.data || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">
          Welcome, {data?.teacher?.teacherName || user?.fullName}
        </h1>
        <p className="mt-1 text-sm text-slate-600">Your schedule for {data?.todayDate}.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* My lectures today */}
        <div className="lg:col-span-7 rounded-lg border border-slate-200 bg-white">
          <div className="px-5 py-3.5 border-b border-slate-200 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">My lectures today</h2>
            <Link to="/today" className="text-xs font-medium text-blue-700 hover:underline">
              Full department schedule
            </Link>
          </div>

          {myLectures.length === 0 ? (
            <div className="p-8 text-center">
              <Calendar className="h-7 w-7 text-slate-400 mx-auto" />
              <p className="mt-2 text-sm font-medium text-slate-900">No lectures today</p>
              <p className="mt-1 text-xs text-slate-500">
                You have no scheduled lectures or substitute duties today.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-200">
              {myLectures.map((lec) => (
                <div key={lec.lectureId} className="px-5 py-3.5 space-y-1">
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                    <span className="font-mono text-slate-500">
                      {lec.startTime}–{lec.endTime}
                    </span>
                    {lec.coveringForSomeone && (
                      <span className="rounded-full bg-blue-50 border border-blue-200 px-2 py-0.5 text-[11px] font-medium text-blue-800">
                        Substitute for {lec.otherTeacherName}
                      </span>
                    )}
                    {lec.coveredBySomeone && (
                      <span className="rounded-full bg-amber-50 border border-amber-200 px-2 py-0.5 text-[11px] font-medium text-amber-800">
                        Covered by {lec.otherTeacherName}
                      </span>
                    )}
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
          )}
        </div>

        {/* Latest notifications + shortcuts */}
        <div className="lg:col-span-5 space-y-4">
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="px-5 py-3.5 border-b border-slate-200 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Bell className="h-4 w-4 text-slate-500" />
                <h2 className="text-sm font-semibold text-slate-900">Latest notifications</h2>
              </div>
              <Link
                to="/notifications"
                className="text-xs font-medium text-blue-700 hover:underline"
              >
                See all
              </Link>
            </div>

            {noticesQuery.isLoading ? (
              <div className="p-6 flex items-center justify-center gap-2 text-xs text-slate-500">
                <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-700" />
                <span>Loading...</span>
              </div>
            ) : notices.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-500">No notifications yet.</div>
            ) : (
              <div className="divide-y divide-slate-200">
                {notices.map((n) => (
                  <div key={n.id} className="px-4 py-3 text-xs text-slate-700">
                    <p>{n.text}</p>
                    <p className="mt-1 text-[11px] text-slate-400">
                      {new Date(n.createdAt).toLocaleString()}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Link
              to="/my-timetable"
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3 text-xs font-medium text-slate-800 hover:bg-slate-50"
            >
              <span>My timetable</span>
              <ArrowRight className="h-3.5 w-3.5 text-slate-400" />
            </Link>
            <Link
              to="/leave"
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3 text-xs font-medium text-slate-800 hover:bg-slate-50"
            >
              <span>Ask for leave</span>
              <ArrowRight className="h-3.5 w-3.5 text-slate-400" />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
