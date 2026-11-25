import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  BookOpen,
  CheckCircle2,
  Link2,
  Loader2,
  Save,
  Search,
  Upload,
  Users,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import CsvImportModal from '../features/setup/CsvImportModal';

const CSV_COLUMNS = [
  { key: 'facultyCode', label: 'Teacher code' },
  { key: 'subjectCode', label: 'Subject code' },
];

export default function TeacherSubjectsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [viewMode, setViewMode] = useState('BY_TEACHER');
  const [leftSearch, setLeftSearch] = useState('');
  const [rightSearch, setRightSearch] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [checkedIds, setCheckedIds] = useState(new Set());
  const [savedIds, setSavedIds] = useState(new Set());
  const [banner, setBanner] = useState('');
  const [pageError, setPageError] = useState('');
  const [importOpen, setImportOpen] = useState(false);

  const teachersQuery = useQuery({
    queryKey: ['teachers', 'all-for-links'],
    queryFn: async () => {
      const res = await api.get('/teachers', { params: { page: 0, size: 100 } });
      return res.data.items || [];
    },
    enabled: isHod,
  });

  const subjectsQuery = useQuery({
    queryKey: ['subjects', 'all-for-links'],
    queryFn: async () => {
      const res = await api.get('/subjects', { params: { page: 0, size: 100 } });
      return res.data.items || [];
    },
    enabled: isHod,
  });

  const teachers = teachersQuery.data || [];
  const subjects = subjectsQuery.data || [];

  const leftItems = viewMode === 'BY_TEACHER' ? teachers : subjects;
  const rightItems = viewMode === 'BY_TEACHER' ? subjects : teachers;

  const filteredLeft = useMemo(() => {
    const q = leftSearch.trim().toLowerCase();
    if (!q) return leftItems;
    return leftItems.filter((item) => {
      const label = viewMode === 'BY_TEACHER' ? item.fullName : item.name;
      return (
        item.code.toLowerCase().includes(q) ||
        label.toLowerCase().includes(q)
      );
    });
  }, [leftItems, leftSearch, viewMode]);

  const filteredRight = useMemo(() => {
    const q = rightSearch.trim().toLowerCase();
    if (!q) return rightItems;
    return rightItems.filter((item) => {
      const label = viewMode === 'BY_TEACHER' ? item.name : item.fullName;
      return (
        item.code.toLowerCase().includes(q) ||
        label.toLowerCase().includes(q)
      );
    });
  }, [rightItems, rightSearch, viewMode]);

  // Select the first item automatically when switching tabs or loading data
  useEffect(() => {
    if (leftItems.length === 0) {
      setSelectedId(null);
      return;
    }
    const stillExists = leftItems.some((item) => item.id === selectedId);
    if (!stillExists) {
      setSelectedId(leftItems[0].id);
    }
  }, [leftItems, selectedId]);

  const linksQuery = useQuery({
    queryKey: ['teacher-subjects', viewMode, selectedId],
    queryFn: async () => {
      const path =
        viewMode === 'BY_TEACHER'
          ? `/teachers/${selectedId}/subjects`
          : `/subjects/${selectedId}/teachers`;
      const res = await api.get(path);
      return res.data || [];
    },
    enabled: isHod && Boolean(selectedId),
  });

  useEffect(() => {
    if (!linksQuery.data) return;
    const ids = new Set(linksQuery.data.map((item) => item.id));
    setCheckedIds(new Set(ids));
    setSavedIds(new Set(ids));
  }, [linksQuery.data, selectedId, viewMode]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const idList = Array.from(checkedIds);
      if (viewMode === 'BY_TEACHER') {
        const res = await api.put(`/teachers/${selectedId}/subjects`, { subjectIds: idList });
        return res.data;
      }
      const res = await api.put(`/subjects/${selectedId}/teachers`, { teacherIds: idList });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teacher-subjects'] });
      setSavedIds(new Set(checkedIds));
      setPageError('');
      setBanner('Changes saved.');
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Could not save changes.'));
    },
  });

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">Who can teach what</h1>
        <p className="mt-1.5 text-sm text-slate-600">
          Only the HOD can manage which teachers can teach which subjects.
        </p>
      </div>
    );
  }

  function switchMode(nextMode) {
    if (nextMode === viewMode) return;
    setViewMode(nextMode);
    setSelectedId(null);
    setLeftSearch('');
    setRightSearch('');
    setBanner('');
    setPageError('');
  }

  function toggleItem(id) {
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  const hasChanges = useMemo(() => {
    if (checkedIds.size !== savedIds.size) return true;
    for (const id of checkedIds) {
      if (!savedIds.has(id)) return true;
    }
    return false;
  }, [checkedIds, savedIds]);

  const selectedItem = leftItems.find((item) => item.id === selectedId) || null;
  const loadingBase = teachersQuery.isLoading || subjectsQuery.isLoading;
  const baseError = teachersQuery.error || subjectsQuery.error;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Who can teach what</h1>
          <p className="mt-1 text-sm text-slate-600">
            Choose which subjects each teacher can take, or upload a CSV file with teacher and
            subject codes.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setImportOpen(true)}
          className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <Upload className="h-4 w-4" />
          <span>Import file</span>
        </button>
      </div>

      {banner && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span>{banner}</span>
          </div>
          <button type="button" onClick={() => setBanner('')} className="text-emerald-700 hover:text-emerald-900">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {(pageError || baseError) && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{pageError || getErrorMessage(baseError, 'Could not load teachers or subjects.')}</span>
          {pageError && (
            <button type="button" onClick={() => setPageError('')} className="text-red-700 hover:text-red-900">
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      )}

      {/* Mode switch */}
      <div className="inline-flex rounded-md border border-slate-200 bg-white p-1">
        <button
          type="button"
          onClick={() => switchMode('BY_TEACHER')}
          className={`inline-flex items-center gap-1.5 rounded px-3.5 py-1.5 text-sm font-medium transition-colors ${
            viewMode === 'BY_TEACHER'
              ? 'bg-blue-700 text-white'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Users className="h-4 w-4" />
          <span>By teacher</span>
        </button>
        <button
          type="button"
          onClick={() => switchMode('BY_SUBJECT')}
          className={`inline-flex items-center gap-1.5 rounded px-3.5 py-1.5 text-sm font-medium transition-colors ${
            viewMode === 'BY_SUBJECT'
              ? 'bg-blue-700 text-white'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <BookOpen className="h-4 w-4" />
          <span>By subject</span>
        </button>
      </div>

      {loadingBase ? (
        <div className="rounded-lg border border-slate-200 bg-white p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading teachers and subjects...</span>
        </div>
      ) : teachers.length === 0 || subjects.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <Link2 className="h-7 w-7 text-slate-400 mx-auto" />
          <p className="mt-3 text-sm font-medium text-slate-900">
            Add teachers and subjects first
          </p>
          <p className="mt-1 text-sm text-slate-500">
            You need at least one teacher and one subject before linking who can teach what.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          {/* Left selector list */}
          <div className="lg:col-span-5 rounded-lg border border-slate-200 bg-white flex flex-col max-h-[600px]">
            <div className="p-3.5 border-b border-slate-200 space-y-2">
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                {viewMode === 'BY_TEACHER' ? '1. Pick a teacher' : '1. Pick a subject'}
              </div>
              <div className="relative">
                <Search className="h-4 w-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  value={leftSearch}
                  onChange={(e) => setLeftSearch(e.target.value)}
                  placeholder={
                    viewMode === 'BY_TEACHER' ? 'Filter teachers...' : 'Filter subjects...'
                  }
                  className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
              {filteredLeft.length === 0 ? (
                <div className="p-6 text-center text-sm text-slate-500">No matches found.</div>
              ) : (
                filteredLeft.map((item) => {
                  const isSelected = item.id === selectedId;
                  const title = viewMode === 'BY_TEACHER' ? item.fullName : item.name;
                  const meta =
                    viewMode === 'BY_TEACHER'
                      ? item.designation || item.email
                      : item.kind === 'LAB'
                        ? `Lab · ${item.periodsInARow} in a row`
                        : 'Theory';
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => {
                        setSelectedId(item.id);
                        setBanner('');
                        setPageError('');
                      }}
                      className={`w-full text-left px-4 py-3 transition-colors ${
                        isSelected
                          ? 'bg-blue-50 border-l-2 border-l-blue-700'
                          : 'hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-medium text-slate-900 truncate">{title}</span>
                        <span className="text-xs font-medium text-slate-500 shrink-0">
                          {item.code}
                        </span>
                      </div>
                      <div className="mt-0.5 text-xs text-slate-500 truncate">
                        {meta}
                        {!item.active && ' · Inactive'}
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* Right checkbox panel */}
          <div className="lg:col-span-7 rounded-lg border border-slate-200 bg-white flex flex-col max-h-[600px]">
            <div className="p-4 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  {viewMode === 'BY_TEACHER'
                    ? '2. Tick subjects they can teach'
                    : '2. Tick teachers who can teach this'}
                </div>
                {selectedItem && (
                  <p className="mt-0.5 text-sm font-semibold text-slate-900">
                    {selectedItem.code} —{' '}
                    {viewMode === 'BY_TEACHER' ? selectedItem.fullName : selectedItem.name}
                    <span className="ml-2 text-xs font-normal text-slate-500">
                      ({checkedIds.size} selected)
                    </span>
                  </p>
                )}
              </div>

              <button
                type="button"
                disabled={!selectedItem || !hasChanges || saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
                className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
              >
                {saveMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                <span>{saveMutation.isPending ? 'Saving...' : 'Save changes'}</span>
              </button>
            </div>

            <div className="px-4 py-2.5 border-b border-slate-100 bg-slate-50/70">
              <div className="relative">
                <Search className="h-4 w-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  value={rightSearch}
                  onChange={(e) => setRightSearch(e.target.value)}
                  placeholder={
                    viewMode === 'BY_TEACHER' ? 'Filter subjects...' : 'Filter teachers...'
                  }
                  className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
              {linksQuery.isLoading ? (
                <div className="p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
                  <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
                  <span>Loading current assignments...</span>
                </div>
              ) : filteredRight.length === 0 ? (
                <div className="p-6 text-center text-sm text-slate-500">No matches found.</div>
              ) : (
                filteredRight.map((item) => {
                  const checked = checkedIds.has(item.id);
                  const title = viewMode === 'BY_TEACHER' ? item.name : item.fullName;
                  const subline =
                    viewMode === 'BY_TEACHER'
                      ? item.kind === 'LAB'
                        ? `Lab · ${item.periodsInARow} in a row`
                        : 'Theory'
                      : item.designation || item.email;
                  return (
                    <label
                      key={item.id}
                      className={`flex items-start gap-3 px-4 py-3 cursor-pointer transition-colors ${
                        checked ? 'bg-blue-50/40' : 'hover:bg-slate-50'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleItem(item.id)}
                        className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-medium text-slate-900">{title}</span>
                          <span className="text-xs font-medium text-slate-500 shrink-0">
                            {item.code}
                          </span>
                        </div>
                        <div className="text-xs text-slate-500">
                          {subline}
                          {!item.active && ' · Inactive'}
                        </div>
                      </div>
                    </label>
                  );
                })
              )}
            </div>
          </div>
        </div>
      )}

      {/* CSV Import Modal */}
      <CsvImportModal
        open={importOpen}
        title="Import who can teach what from CSV"
        description="Upload a CSV with columns: facultyCode, subjectCode. Existing links and duplicates are skipped."
        templatePath="/teacher-subjects/template"
        templateFilename="who_can_teach_what_template.csv"
        previewPath="/teacher-subjects/import/preview"
        confirmPath="/teacher-subjects/import/confirm"
        columns={CSV_COLUMNS}
        showMode={false}
        onClose={() => setImportOpen(false)}
        onImported={(result) => {
          queryClient.invalidateQueries({ queryKey: ['teacher-subjects'] });
          setBanner(result.message);
        }}
      />
    </div>
  );
}