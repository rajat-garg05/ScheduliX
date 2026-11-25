import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  Loader2,
  Pencil,
  Plus,
  Power,
  Search,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';
import CsvImportModal from '../features/setup/CsvImportModal';

const CSV_COLUMNS = [
  { key: 'subjectCode', label: 'Code' },
  { key: 'name', label: 'Name' },
  { key: 'classification', label: 'Type' },
  { key: 'periodsInARow', label: 'Lectures in a row' },
  { key: 'runsTogetherGroup', label: 'Runs at the same time as' },
  { key: 'sharedPlace', label: 'Shared lab or place' },
];

const EMPTY_FORM = {
  code: '',
  name: '',
  kind: 'THEORY',
  periodsInARow: 1,
  runTogetherGroup: '',
  sharedPlace: '',
};

export default function SubjectsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [banner, setBanner] = useState('');
  const [warnings, setWarnings] = useState([]);
  const [pageError, setPageError] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editingSubject, setEditingSubject] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');

  const [importOpen, setImportOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['subjects', { search, page }],
    queryFn: async () => {
      const res = await api.get('/subjects', {
        params: { search: search.trim() || undefined, page, size: 50 },
      });
      return res.data;
    },
    enabled: isHod,
  });

  const saveMutation = useMutation({
    mutationFn: (payload) =>
      editingSubject
        ? api.put(`/subjects/${editingSubject.id}`, payload)
        : api.post('/subjects', payload),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['subjects'] });
      setBanner(editingSubject ? 'Subject updated.' : 'Subject added.');
      setWarnings(res.data?.warnings || []);
      closeForm();
    },
    onError: (err) => {
      setFormError(getErrorMessage(err, 'Could not save subject.'));
    },
  });

  const actionMutation = useMutation({
    mutationFn: async ({ type, subject }) => {
      if (type === 'deactivate') return api.post(`/subjects/${subject.id}/deactivate`);
      if (type === 'activate') return api.post(`/subjects/${subject.id}/activate`);
      if (type === 'delete') return api.delete(`/subjects/${subject.id}`);
      return null;
    },
    onSuccess: (res, { type, subject }) => {
      queryClient.invalidateQueries({ queryKey: ['subjects'] });
      setPageError('');
      setWarnings(res?.data?.warnings || []);
      if (type === 'deactivate') setBanner(`${subject.name} has been deactivated.`);
      if (type === 'activate') setBanner(`${subject.name} is active again.`);
      if (type === 'delete') setBanner(`${subject.name} has been deleted.`);
      setConfirmAction(null);
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Action failed.'));
      setConfirmAction(null);
    },
  });

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">Subjects</h1>
        <p className="mt-1.5 text-sm text-slate-600">Only the HOD can manage subjects.</p>
      </div>
    );
  }

  function openAdd() {
    setEditingSubject(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError('');
    setFormOpen(true);
  }

  function openEdit(subject) {
    setEditingSubject(subject);
    setForm({
      code: subject.code,
      name: subject.name,
      kind: subject.kind,
      periodsInARow: subject.periodsInARow,
      runTogetherGroup: subject.runTogetherGroup || '',
      sharedPlace: subject.sharedPlace || '',
    });
    setFieldErrors({});
    setFormError('');
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditingSubject(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError('');
  }

  function handleSaveSubmit(e) {
    e.preventDefault();
    setFormError('');
    const errors = {};
    if (!form.code.trim()) errors.code = 'Please enter a subject code.';
    if (!form.name.trim()) errors.name = 'Please enter the subject name.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    saveMutation.mutate({
      code: form.code.trim(),
      name: form.name.trim(),
      kind: form.kind,
      periodsInARow: Number(form.periodsInARow),
      runTogetherGroup: form.kind === 'LAB' ? form.runTogetherGroup.trim() || null : null,
      sharedPlace: form.kind === 'LAB' ? form.sharedPlace.trim() || null : null,
    });
  }

  const items = data?.items || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Subjects</h1>
          <p className="mt-1 text-sm text-slate-600">
            Manage theory subjects and labs, including labs that run at the same time or share a
            place.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={() => setImportOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            <Upload className="h-4 w-4" />
            <span>Import file</span>
          </button>
          <button
            type="button"
            onClick={openAdd}
            className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-2 text-sm font-medium text-white hover:bg-blue-800"
          >
            <Plus className="h-4 w-4" />
            <span>Add subject</span>
          </button>
        </div>
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

      {warnings.length > 0 && (
        <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 space-y-1">
          {warnings.map((w, idx) => (
            <div key={idx} className="flex items-start gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
              <span>{w}</span>
            </div>
          ))}
        </div>
      )}

      {pageError && (
        <div className="flex items-center justify-between gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <span>{pageError}</span>
          <button type="button" onClick={() => setPageError('')} className="text-red-700 hover:text-red-900">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Search bar */}
      <div className="relative max-w-sm">
        <Search className="h-4 w-4 text-slate-400 absolute left-3 top-2.5" />
        <input
          type="text"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          placeholder="Search by code, name, group, or place..."
          className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
        />
      </div>

      {isLoading && (
        <div className="rounded-lg border border-slate-200 bg-white p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading subjects...</span>
        </div>
      )}

      {isError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {getErrorMessage(error, 'Could not load subjects.')}
        </div>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <BookOpen className="h-7 w-7 text-slate-400 mx-auto" />
          <p className="mt-3 text-sm font-medium text-slate-900">
            {search ? 'No subjects match your search' : 'No subjects added yet'}
          </p>
          <p className="mt-1 text-sm text-slate-500">
            {search
              ? 'Try a different code, name, or lab place.'
              : 'Add theory subjects and labs by hand or import them from a CSV file.'}
          </p>
          {!search && (
            <button
              type="button"
              onClick={openAdd}
              className="mt-4 inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-2 text-sm font-medium text-white hover:bg-blue-800"
            >
              <Plus className="h-4 w-4" />
              <span>Add subject</span>
            </button>
          )}
        </div>
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white overflow-x-auto">
          <table className="w-full min-w-[840px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-500">
                <th className="px-4 py-3 w-24">Code</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3 w-24">Type</th>
                <th className="px-4 py-3 w-32">Lectures in a row</th>
                <th className="px-4 py-3">Runs at the same time as</th>
                <th className="px-4 py-3">Shared lab or place</th>
                <th className="px-4 py-3 w-24">Status</th>
                <th className="px-4 py-3 text-right w-32">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {items.map((subject) => (
                <tr key={subject.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-3 font-medium text-slate-900">{subject.code}</td>
                  <td className="px-4 py-3 text-slate-900">{subject.name}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${
                        subject.kind === 'LAB'
                          ? 'bg-teal-50 border border-teal-200 text-teal-800'
                          : 'bg-slate-100 border border-slate-200 text-slate-700'
                      }`}
                    >
                      {subject.kind === 'LAB' ? 'Lab' : 'Theory'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{subject.periodsInARow}</td>
                  <td className="px-4 py-3 text-slate-600">{subject.runTogetherGroup || '—'}</td>
                  <td className="px-4 py-3 text-slate-600">{subject.sharedPlace || '—'}</td>
                  <td className="px-4 py-3">
                    {subject.active ? (
                      <span className="inline-flex rounded bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-xs font-medium text-emerald-700">
                        Active
                      </span>
                    ) : (
                      <span className="inline-flex rounded bg-slate-100 border border-slate-200 px-2 py-0.5 text-xs font-medium text-slate-600">
                        Inactive
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        type="button"
                        onClick={() => openEdit(subject)}
                        title="Edit subject"
                        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          setConfirmAction({
                            type: subject.active ? 'deactivate' : 'activate',
                            subject,
                          })
                        }
                        title={subject.active ? 'Deactivate subject' : 'Activate subject'}
                        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                      >
                        <Power className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmAction({ type: 'delete', subject })}
                        title="Delete subject"
                        className="p-1.5 rounded-md text-slate-600 hover:bg-red-50 hover:text-red-700"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
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

      {/* Add / Edit Modal */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
              <h2 className="text-base font-semibold text-slate-900">
                {editingSubject ? 'Edit subject' : 'Add subject'}
              </h2>
              <button
                type="button"
                onClick={closeForm}
                className="p-1 rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleSaveSubmit} className="px-5 py-4 space-y-4" noValidate>
              {formError && (
                <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {formError}
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="sCode" className="block text-sm font-medium text-slate-700">
                    Subject code
                  </label>
                  <input
                    id="sCode"
                    type="text"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    placeholder="e.g. CS301"
                    className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                  />
                  {fieldErrors.code && (
                    <p className="mt-1 text-xs text-red-600">{fieldErrors.code}</p>
                  )}
                </div>

                <div>
                  <label htmlFor="sKind" className="block text-sm font-medium text-slate-700">
                    Type
                  </label>
                  <select
                    id="sKind"
                    value={form.kind}
                    onChange={(e) => {
                      const nextKind = e.target.value;
                      setForm({
                        ...form,
                        kind: nextKind,
                        periodsInARow: nextKind === 'LAB' && form.periodsInARow === 1 ? 2 : form.periodsInARow,
                      });
                    }}
                    className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                  >
                    <option value="THEORY">Theory</option>
                    <option value="LAB">Lab</option>
                  </select>
                </div>
              </div>

              <div>
                <label htmlFor="sName" className="block text-sm font-medium text-slate-700">
                  Subject name
                </label>
                <input
                  id="sName"
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. Data Structures"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
                {fieldErrors.name && <p className="mt-1 text-xs text-red-600">{fieldErrors.name}</p>}
              </div>

              <div>
                <label htmlFor="sPeriods" className="block text-sm font-medium text-slate-700">
                  Lectures in a row
                </label>
                <select
                  id="sPeriods"
                  value={form.periodsInARow}
                  onChange={(e) => setForm({ ...form, periodsInARow: Number(e.target.value) })}
                  className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                >
                  <option value={1}>1 lecture</option>
                  <option value={2}>2 lectures back to back</option>
                  <option value={3}>3 lectures back to back</option>
                </select>
                <p className="mt-1 text-xs text-slate-500">
                  How many lectures are held back to back without a break (labs are usually 2).
                </p>
              </div>

              {form.kind === 'LAB' && (
                <>
                  <div>
                    <label htmlFor="sGroup" className="block text-sm font-medium text-slate-700">
                      Runs at the same time as (group name)
                    </label>
                    <input
                      id="sGroup"
                      type="text"
                      value={form.runTogetherGroup}
                      onChange={(e) => setForm({ ...form, runTogetherGroup: e.target.value })}
                      placeholder="e.g. LABGROUP-S3 (optional)"
                      className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                    />
                    <p className="mt-1 text-xs text-slate-500">
                      Labs with the same group name run together for one class, half the class in
                      each. They must use different places.
                    </p>
                  </div>

                  <div>
                    <label htmlFor="sPlace" className="block text-sm font-medium text-slate-700">
                      Shared lab or place (only one class at a time)
                    </label>
                    <input
                      id="sPlace"
                      type="text"
                      value={form.sharedPlace}
                      onChange={(e) => setForm({ ...form, sharedPlace: e.target.value })}
                      placeholder="e.g. Computer Lab 1 (optional)"
                      className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                    />
                    <p className="mt-1 text-xs text-slate-500">
                      Only one class in the whole department can use this place at the same time.
                    </p>
                  </div>
                </>
              )}

              <div className="pt-2 flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={closeForm}
                  className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
                >
                  {saveMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>{editingSubject ? 'Save changes' : 'Add subject'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CSV Import Modal */}
      <CsvImportModal
        open={importOpen}
        title="Import subjects from CSV"
        description="Upload a CSV with columns: subjectCode, name, classification, periodsInARow, runsTogetherGroup, sharedPlace."
        templatePath="/subjects/template"
        templateFilename="subjects_template.csv"
        previewPath="/subjects/import/preview"
        confirmPath="/subjects/import/confirm"
        columns={CSV_COLUMNS}
        onClose={() => setImportOpen(false)}
        onImported={(result) => {
          queryClient.invalidateQueries({ queryKey: ['subjects'] });
          setBanner(result.message);
          setWarnings([]);
        }}
      />

      {/* Confirmation Dialog */}
      <ConfirmDialog
        open={Boolean(confirmAction)}
        title={
          confirmAction?.type === 'delete'
            ? 'Delete subject'
            : confirmAction?.type === 'deactivate'
              ? 'Deactivate subject'
              : 'Activate subject'
        }
        message={
          confirmAction?.type === 'delete'
            ? `Delete ${confirmAction.subject.name} (${confirmAction.subject.code})? Subjects used in a semester plan or timetable cannot be deleted.`
            : confirmAction?.type === 'deactivate'
              ? `Deactivate ${confirmAction.subject.name}? It will remain in existing timetables but will not be active for new setup.`
              : confirmAction
                ? `Activate ${confirmAction.subject.name} again?`
                : ''
        }
        confirmLabel={
          confirmAction?.type === 'delete'
            ? 'Delete'
            : confirmAction?.type === 'deactivate'
              ? 'Deactivate'
              : 'Activate'
        }
        danger={confirmAction?.type === 'delete'}
        loading={actionMutation.isPending}
        onConfirm={() => actionMutation.mutate(confirmAction)}
        onCancel={() => setConfirmAction(null)}
      />
    </div>
  );
}