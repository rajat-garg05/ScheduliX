import { Link } from 'react-router-dom';
import {
  FileSpreadsheet,
  CalendarRange,
  Pin,
  UserCheck,
  CalendarClock,
  ArrowRight,
} from 'lucide-react';
import { useAuth } from '../features/auth/authContext';
import BrandLogo from '../components/BrandLogo';
import SampleTimetable from '../components/SampleTimetable';

const HIGHLIGHTS = [
  {
    icon: FileSpreadsheet,
    title: 'Add teachers and subjects from a file',
    description:
      'Upload a spreadsheet or enter details by hand. Problems are shown row by row before anything is saved.',
  },
  {
    icon: CalendarRange,
    title: 'Make timetables for many classes at once',
    description:
      'Build schedules for all sections of a semester together while avoiding clashes with already saved timetables.',
  },
  {
    icon: Pin,
    title: 'Keep the lectures you like and make the rest again',
    description:
      'Pin any lecture that works well and rearrange only the remaining slots until the whole week looks right.',
  },
  {
    icon: UserCheck,
    title: 'Handle teacher leave day by day',
    description:
      'Record leave or approve teacher requests. Free and eligible substitutes are suggested without changing the saved timetable.',
  },
  {
    icon: CalendarClock,
    title: "Every teacher can see today's timetable",
    description:
      'Teachers check all class schedules, their own weekly lectures, and accepted substitutes from one place.',
  },
];

const STEPS = [
  {
    number: '1',
    title: 'Register your department',
    description: 'Create the HOD account with your department name and confirm your email address.',
  },
  {
    number: '2',
    title: 'Add your data',
    description: 'Load your teachers, subjects, who can teach what, and each section’s semester plan.',
  },
  {
    number: '3',
    title: 'Make the timetable',
    description: 'Set your timings, review teachers, make the timetable in one click, and save it.',
  },
];

export default function HomePage() {
  const { isLoggedIn } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col">
      {/* Top bar */}
      <header className="border-b border-slate-200 bg-white">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <Link to="/" aria-label="ScheduliX" className="inline-flex items-center">
            <BrandLogo size="lg" />
          </Link>

          <div className="flex items-center gap-3">
            {isLoggedIn ? (
              <Link
                to="/dashboard"
                className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
              >
                Go to my dashboard
                <ArrowRight className="h-4 w-4" />
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="rounded-md px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className="rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="flex-1">
        {/* Hero */}
        <section className="bg-white border-b border-slate-200">
          <div className="max-w-6xl mx-auto px-4 sm:px-6 py-14 sm:py-18">
            <div className="max-w-2xl">
              <p className="text-sm font-medium text-blue-700">
                For college departments
              </p>
              <h1 className="mt-2 text-3xl sm:text-4xl font-semibold tracking-tight text-slate-900 leading-tight">
                Make your department timetable in minutes
              </h1>
              <p className="mt-4 text-base text-slate-600 leading-relaxed">
                Set up your teachers, subjects, and semester plans once. ScheduliX builds clash-free
                timetables for every section and helps you arrange daily substitutes when a teacher
                is on leave.
              </p>

              <div className="mt-7 flex flex-wrap items-center gap-3">
                {isLoggedIn ? (
                  <Link
                    to="/dashboard"
                    className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-800"
                  >
                    Go to my dashboard
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                ) : (
                  <>
                    <Link
                      to="/register"
                      className="rounded-md bg-blue-700 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-800"
                    >
                      Register your department
                    </Link>
                    <Link
                      to="/login"
                      className="rounded-md border border-slate-300 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    >
                      Login
                    </Link>
                  </>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* Key points */}
        <section className="max-w-6xl mx-auto px-4 sm:px-6 py-14">
          <h2 className="text-xl font-semibold text-slate-900">
            Everything a department needs for weekly and daily scheduling
          </h2>
          <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {HIGHLIGHTS.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.title}
                  className="rounded-lg border border-slate-200 bg-white p-5"
                >
                  <div className="h-9 w-9 rounded-md bg-blue-50 text-blue-700 flex items-center justify-center">
                    <Icon className="h-5 w-5" />
                  </div>
                  <h3 className="mt-3.5 text-base font-semibold text-slate-900">
                    {item.title}
                  </h3>
                  <p className="mt-1.5 text-sm text-slate-600 leading-relaxed">
                    {item.description}
                  </p>
                </div>
              );
            })}
          </div>
        </section>

        {/* Sample timetable */}
        <section className="max-w-6xl mx-auto px-4 sm:px-6 pb-14">
          <div className="mb-5">
            <h2 className="text-xl font-semibold text-slate-900">
              How a class timetable looks
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              Days across, lectures down, breaks clearly marked, and paired labs kept back to back.
            </p>
          </div>
          <SampleTimetable />
        </section>

        {/* Three steps */}
        <section className="bg-white border-y border-slate-200">
          <div className="max-w-6xl mx-auto px-4 sm:px-6 py-14">
            <h2 className="text-xl font-semibold text-slate-900">
              Start in three steps
            </h2>
            <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
              {STEPS.map((step) => (
                <div key={step.number} className="flex gap-3.5">
                  <div className="h-8 w-8 rounded-full bg-blue-700 text-white text-sm font-semibold flex items-center justify-center shrink-0">
                    {step.number}
                  </div>
                  <div>
                    <h3 className="text-base font-semibold text-slate-900">{step.title}</h3>
                    <p className="mt-1 text-sm text-slate-600 leading-relaxed">
                      {step.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* For teachers strip */}
        <section className="max-w-6xl mx-auto px-4 sm:px-6 py-12">
          <div className="rounded-lg border border-slate-200 bg-white p-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">For teachers</h2>
              <p className="mt-1 text-sm text-slate-600 max-w-2xl">
                You do not need to register. Once your HOD adds you, sign in with your official
                email to see your weekly lectures, today&apos;s substitutes, and request leave.
              </p>
            </div>
            {!isLoggedIn && (
              <Link
                to="/login"
                className="inline-flex items-center justify-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 shrink-0"
              >
                Teacher login
              </Link>
            )}
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500">
          <span>ScheduliX · Department timetable management</span>
          <span>Built for clear weekly timetables and daily leave cover</span>
        </div>
      </footer>
    </div>
  );
}