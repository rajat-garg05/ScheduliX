import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Calendar,
  CalendarPlus,
  CheckCircle2,
  Eye,
  FolderTree,
  Loader2,
  Search,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';
import TimetableGrid from '../components/TimetableGrid';

export default function ClassesTimetablesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [search, setSearch] = useState('');
  const [viewingSection, setViewingSection] = useState(null); // { sectionId, sectionName, semesterName, timetableId }
  const [viewData, setViewData] = useState(null);
  const [loadingViewId, setLoadingViewId] = useState(null);

  const [loadingPreviewId, setLoadingPreviewId] = useState(null);
  const [deletePreview, setDeletePreview] = useState(null);

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const treeQuery = useQuery({
    queryKey: ['timetable-tree'],
    queryFn: async () => {
      const res = await api.get('/tree');
      return res.data?.semesters || [];
    },
    enabled: isHod,
  });

  const deleteMutation = useMutation({
    mutationFn: (timetableId) => api.delete(`/timetables/${timetableId}`),
    onSuccess: () => {
      const deletedName = deletePreview?.name || 'timetable';
      setDeletePreview(null);
      setViewingSection(null);
      setViewData(null);
      setError('');
      setSuccess(`Deleted ${deletedName}. Teachers and shared labs are free again.`);
      queryClient.invalidateQueries({ queryKey: ['timetable-tree'] });
      queryClient.invalidateQueries({ queryKey: ['timetable-drafts'] });
    },
    onError: (err) => {
      setError(getErrorMessage(err, 'Could not delete this timetable.'));
      setDeletePreview(null);
    },
  });

  const semesters = treeQuery.data || [];

  const filteredSemesters = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return semesters;
    return semesters
      .map((sem) => {
        const semMatches = sem.semesterName.toLowerCase().includes(q);
        const matchingSections = sem.sections.filter((sec) => {
          if (semMatches) return true;
          const secLabel = `section ${sec.sectionName}`.toLowerCase();
          const ttName = (sec.savedTimetable?.timetableName || '').toLowerCase();
          return secLabel.includes(q) || ttName.includes(q);
        });
        return { ...sem, sections: matchingSections };
      })
      .filter((sem) => sem.sections.length > 0);
  }, [semesters, search]);

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">Classes and Timetables</h1>
        <p className="mt-1.5 text-sm text-slate-600">
          Only the HOD can manage saved timetables here.
        </p>
      </div>
    );
  }

  async function openSectionView(sem, sec) {
    setLoadingViewId(sec.sectionId);
    setError('');
    setSuccess('');
    try {
      const res = await api.get(`/sections/${sec.sectionId}/timetable`);
      setViewData(res.data);
      setViewingSection({
        sectionId: sec.sectionId,
        sectionName: sec.sectionName,
        semesterName: sem.semesterName,
        timetableId: sec.savedTimetable.timetableId,
        timetableName: sec.savedTimetable.timetableName,
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Could not open this timetable.'));
    } finally {
      setLoadingViewId(null);
    }
  }

  async function requestDeletePreview(timetableId) {
    setLoadingPreviewId(timetableId);
    setError('');
    setSuccess('');
    try {
      const res = await api.get(`/timetables/${timetableId}/delete-preview`);
      setDeletePreview(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load delete details.'));
    } finally {
      setLoadingPreviewId(null);
    }
  }

  if (viewingSection && viewData) {
    return (
      <div className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <button
              type="button"
              onClick={() => {
                setViewingSection(null);
                setViewData(null);
              }}
              className="inline-flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-slate-900"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to Classes and Timetables</span>
            </button>
            <h1 className="mt-1 text-2xl font-semibold text-slate-900">
              {viewingSection.semesterName} — Section {viewingSection.sectionName}
            </h1>
            {viewData.savedAt && (
              <p className="mt-0.5 text-xs text-slate-500">
                Saved on {new Date(viewData.savedAt).toLocaleString()} · Read-only
              </p>
            )}
          </div>

          <button
            type="button"
            disabled={loadingPreviewId === viewingSection.timetableId}
            onClick={() => requestDeletePreview(viewingSection.timetableId)}
            className="inline-flex items-center gap-1.5 rounded-md border border-red-200 bg-white px-3.5 py-2 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {loadingPreviewId === viewingSection.timetableId ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Trash2 className="h-3.5 w-3.5" />
            )}
            <span>Delete timetable</span>
          </button>
        </div>

        {error && (
          <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
            {error}
          </div>
        )}

        <TimetableGrid
          view={viewData}
          mode="class"
          selectedSectionId={viewingSection.sectionId}
          readOnly
        />

        <ConfirmDialog
          open={Boolean(deletePreview)}
          title={`Delete ${deletePreview?.name || 'saved timetable'}`}
          message={deletePreview?.summaryMessage || ''}
          confirmLabel="Delete timetable"
          danger
          loading={deleteMutation.isPending}
          onConfirm={() => deleteMutation.mutate(deletePreview.timetableId)}
          onCancel={() => setDeletePreview(null)}
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Classes and Timetables</h1>
          <p className="mt-1 text-sm text-slate-600">
            View saved timetables by semester and section, or delete one to make it again.
          </p>
        </div>

        <Link
          to="/make-timetable"
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
        >
          <CalendarPlus className="h-4 w-4" />
          <span>Make Timetable</span>
        </Link>
      </div>

      {error && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} className="text-red-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {success && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <span>{success}</span>
          </div>
          <button type="button" onClick={() => setSuccess('')} className="text-emerald-700">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Search */}
      <div className="relative max-w-sm">
        <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search semester or section..."
          className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
        />
      </div>

      {treeQuery.isLoading ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading classes and timetables...</span>
        </div>
      ) : filteredSemesters.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <FolderTree className="h-8 w-8 text-slate-400 mx-auto" />
          <p className="mt-2.5 text-sm font-medium text-slate-900">
            {search.trim() ? 'No matching classes found' : 'No semesters or sections yet'}
          </p>
          <p className="mt-1 text-sm text-slate-500">
            {search.trim()
              ? 'Try clearing the search box.'
              : 'Add semesters and sections in Setup first.'}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {filteredSemesters.map((sem) => (
            <div key={sem.semesterId} className="rounded-lg border border-slate-200 bg-white">
              <div className="px-5 py-3 border-b border-slate-200 bg-slate-50/70 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-slate-900">{sem.semesterName}</h2>
                <span className="text-xs text-slate-500">
                  {sem.sections.length} section{sem.sections.length === 1 ? '' : 's'}
                </span>
              </div>

              {sem.sections.length === 0 ? (
                <div className="px-5 py-4 text-xs text-slate-500">
                  No sections in this semester yet.
                </div>
              ) : (
                <div className="divide-y divide-slate-200">
                  {sem.sections.map((sec) => {
                    const saved = sec.savedTimetable;
                    return (
                      <div
                        key={sec.sectionId}
                        className="px-5 py-4 flex flex-wrap items-center justify-between gap-3"
                      >
                        <div>
                          <div className="text-sm font-medium text-slate-900">
                            Section {sec.sectionName}
                          </div>
                          {saved ? (
                            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-slate-500">
                              <span className="inline-flex items-center gap-1">
                                <Calendar className="h-3.5 w-3.5 text-slate-400" />
                                <span>
                                  Saved{' '}
                                  {saved.savedAt
                                    ? new Date(saved.savedAt).toLocaleDateString()
                                    : ''}
                                </span>
                              </span>
                              <span className="inline-flex items-center gap-1">
                                <Users className="h-3.5 w-3.5 text-slate-400" />
                                <span>
                                  {saved.teacherCount} teacher
                                  {saved.teacherCount === 1 ? '' : 's'}
                                </span>
                              </span>
                            </div>
                          ) : (
                            <p className="mt-0.5 text-xs text-slate-400">No saved timetable yet</p>
                          )}
                        </div>

                        {saved ? (
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              disabled={loadingViewId === sec.sectionId}
                              onClick={() => openSectionView(sem, sec)}
                              className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-60"
                            >
                              {loadingViewId === sec.sectionId ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <Eye className="h-3.5 w-3.5" />
                              )}
                              <span>View</span>
                            </button>
                            <button
                              type="button"
                              disabled={loadingPreviewId === saved.timetableId}
                              onClick={() => requestDeletePreview(saved.timetableId)}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-red-50 hover:text-red-700 hover:border-red-200 disabled:opacity-60"
                            >
                              {loadingPreviewId === saved.timetableId ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <Trash2 className="h-3.5 w-3.5" />
                              )}
                              <span>Delete</span>
                            </button>
                          </div>
                        ) : (
                          <Link
                            to="/make-timetable"
                            className="text-xs font-medium text-blue-700 hover:underline"
                          >
                            Make timetable
                          </Link>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(deletePreview)}
        title={`Delete ${deletePreview?.name || 'saved timetable'}`}
        message={deletePreview?.summaryMessage || ''}
        confirmLabel="Delete timetable"
        danger
        loading={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate(deletePreview.timetableId)}
        onCancel={() => setDeletePreview(null)}
      />
    </div>
  );
}