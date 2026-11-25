import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { History, Loader2, Search } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';

const KIND_OPTIONS = [
  { value: '', label: 'All types' },
  { value: 'ACCOUNT', label: 'Accounts' },
  { value: 'IMPORT', label: 'File imports' },
  { value: 'TEACHER', label: 'Teachers' },
  { value: 'SUBJECT', label: 'Subjects' },
  { value: 'PLAN', label: 'Semester plans' },
  { value: 'TIMETABLE', label: 'Timetables' },
  { value: 'LEAVE', label: 'Leave' },
  { value: 'SUBSTITUTE', label: 'Substitutes' },
];

const KIND_LABELS = Object.fromEntries(
  KIND_OPTIONS.filter((o) => o.value).map((o) => [o.value, o.label])
);

function formatTimestamp(isoString) {
  if (!isoString) return '';
  return new Date(isoString).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function HistoryPage() {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [kind, setKind] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);

  const isHod = user?.role === 'HOD';

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['history', { search, kind, fromDate, toDate, page }],
    queryFn: async () => {
      const params = { page, size: 50 };
      if (search.trim()) params.search = search.trim();
      if (kind) params.kind = kind;
      if (fromDate) params.from = fromDate;
      if (toDate) params.to = toDate;
      const res = await api.get('/history', { params });
      return res.data;
    },
    enabled: isHod,
  });

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">History</h1>
        <p className="mt-1.5 text-sm text-slate-600">
          Only the HOD can view department history.
        </p>
      </div>
    );
  }

  const items = data?.items || [];
  const totalPages = data?.totalPages || 0;
  const hasFilters = Boolean(search || kind || fromDate || toDate);

  function clearFilters() {
    setSearch('');
    setKind('');
    setFromDate('');
    setToDate('');
    setPage(0);
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">History</h1>
        <p className="mt-1 text-sm text-slate-600">
          A permanent record of important actions taken in your department.
        </p>
      </div>

      {/* Filters */}
      <div className="rounded-lg border border-slate-200 bg-white p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        <div className="lg:col-span-2">
          <label htmlFor="historySearch" className="block text-xs font-medium text-slate-600">
            Search
          </label>
          <div className="relative mt-1">
            <Search className="h-4 w-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              id="historySearch"
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Search by person or action..."
              className="block w-full rounded-md border border-slate-300 pl-9 pr-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
          </div>
        </div>

        <div>
          <label htmlFor="historyKind" className="block text-xs font-medium text-slate-600">
            Type
          </label>
          <select
            id="historyKind"
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              setPage(0);
            }}
            className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          >
            {KIND_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="historyFrom" className="block text-xs font-medium text-slate-600">
            From date
          </label>
          <input
            id="historyFrom"
            type="date"
            value={fromDate}
            onChange={(e) => {
              setFromDate(e.target.value);
              setPage(0);
            }}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
        </div>

        <div>
          <label htmlFor="historyTo" className="block text-xs font-medium text-slate-600">
            To date
          </label>
          <input
            id="historyTo"
            type="date"
            value={toDate}
            onChange={(e) => {
              setToDate(e.target.value);
              setPage(0);
            }}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
          />
        </div>

        {hasFilters && (
          <div className="sm:col-span-2 lg:col-span-5 flex justify-end">
            <button
              type="button"
              onClick={clearFilters}
              className="text-xs font-medium text-blue-700 hover:underline"
            >
              Clear filters
            </button>
          </div>
        )}
      </div>

      {isLoading && (
        <div className="rounded-lg border border-slate-200 bg-white p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading history...</span>
        </div>
      )}

      {isError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {getErrorMessage(error, 'Could not load history.')}
        </div>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <History className="h-7 w-7 text-slate-400 mx-auto" />
          <p className="mt-3 text-sm font-medium text-slate-900">No matching history records</p>
          <p className="mt-1 text-sm text-slate-500">
            {hasFilters
              ? 'Try clearing or widening your filters.'
              : 'Actions taken in your department will be recorded here.'}
          </p>
        </div>
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-500">
                <th className="px-4 py-3 w-44">When</th>
                <th className="px-4 py-3 w-36">Type</th>
                <th className="px-4 py-3 w-44">Done by</th>
                <th className="px-4 py-3">What happened</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {items.map((entry) => (
                <tr key={entry.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
                    {formatTimestamp(entry.createdAt)}
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-flex rounded border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-700">
                      {KIND_LABELS[entry.kind] || entry.kind}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-700 font-medium">{entry.userName}</td>
                  <td className="px-4 py-3 text-slate-800">{entry.text}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}