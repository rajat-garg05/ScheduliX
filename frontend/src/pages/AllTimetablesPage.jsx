import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Calendar, Loader2, Search } from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import TimetableGrid from '../components/TimetableGrid';

export default function AllTimetablesPage() {
  const [search, setSearch] = useState('');
  const [selectedSectionId, setSelectedSectionId] = useState('');

  const savedListQuery = useQuery({
    queryKey: ['saved-timetables-list'],
    queryFn: async () => {
      const res = await api.get('/timetables/saved');
      return res.data?.semesters || [];
    },
  });

  const semesters = savedListQuery.data || [];

  const flatSections = useMemo(() => {
    const list = [];
    for (const sem of semesters) {
      for (const sec of sem.sections || []) {
        list.push({
          sectionId: sec.sectionId,
          sectionName: sec.sectionName,
          semesterName: sem.semesterName,
          label: `${sem.semesterName} — Section ${sec.sectionName}`,
        });
      }
    }
    return list;
  }, [semesters]);

  const filteredSections = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return flatSections;
    return flatSections.filter((item) => item.label.toLowerCase().includes(q));
  }, [flatSections, search]);

  useEffect(() => {
    if (
      filteredSections.length > 0 &&
      !filteredSections.some((item) => item.sectionId === selectedSectionId)
    ) {
      setSelectedSectionId(filteredSections[0].sectionId);
    }
  }, [filteredSections, selectedSectionId]);

  const gridQuery = useQuery({
    queryKey: ['section-saved-timetable', selectedSectionId],
    queryFn: async () => {
      const res = await api.get(`/sections/${selectedSectionId}/timetable`);
      return res.data;
    },
    enabled: Boolean(selectedSectionId),
  });

  const activeMeta = flatSections.find((item) => item.sectionId === selectedSectionId);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">All timetables</h1>
        <p className="mt-1 text-sm text-slate-600">
          Choose any class to view its saved weekly timetable.
        </p>
      </div>

      {savedListQuery.isLoading ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700"/>
          <span>Loading saved timetables...</span>
        </div>
      ) : flatSections.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <Calendar className="h-7 w-7 text-slate-400 mx-auto"/>
          <p className="mt-2.5 text-sm font-medium text-slate-900">No saved timetables yet</p>
          <p className="mt-1 text-sm text-slate-500">
            Saved timetables will appear here as soon as the HOD publishes them.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative w-full sm:w-64">
              <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2"/>
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Filter classes..."
                className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>

            <select
              value={selectedSectionId}
              onChange={(e) => setSelectedSectionId(e.target.value)}
              className="w-full sm:w-72 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
            >
              {filteredSections.map((item) => (
                <option key={item.sectionId} value={item.sectionId}>
                  {item.label}
                </option>
              ))}
            </select>
          </div>

          {gridQuery.isError && (
            <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
              {getErrorMessage(gridQuery.error, 'Could not load this timetable.')}
            </div>
          )}

          {gridQuery.isLoading ? (
            <div className="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700"/>
              <span>Loading timetable...</span>
            </div>
          ) : gridQuery.data ? (
            <div className="space-y-2">
              {activeMeta && (
                <div className="text-sm font-semibold text-slate-800">{activeMeta.label}</div>
              )}
              <TimetableGrid mode="class" readOnly selectedSectionId={selectedSectionId} view={gridQuery.data}/>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}