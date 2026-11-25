import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CheckCircle2,
  KeyRound,
  Loader2,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
  UserCheck,
  UserX,
  Users,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';
import CsvImportModal from '../features/setup/CsvImportModal';

const CSV_COLUMNS = [
  { key: 'facultyCode', label: 'Code' },
  { key: 'fullName', label: 'Full name' },
  { key: 'officialEmail', label: 'Email' },
  { key: 'designation', label: 'Designation' },
];

const EMPTY_FORM = { code: '', fullName: '', email: '', designation: '' };

export default function TeachersPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [banner, setBanner] = useState('');
  const [pageError, setPageError] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editingTeacher, setEditingTeacher] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');

  const [importOpen, setImportOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['teachers', { search, page }],
    queryFn: async () => {
      const res = await api.get('/teachers', {
        params: { search: search.trim() || undefined, page, size: 50 },
      });
      return res.data;
    },
    enabled: isHod,
  });

  const saveMutation = useMutation({
    mutationFn: (payload) =>
      editingTeacher
        ? api.put(`/teachers/${editingTeacher.id}`, payload)
        : api.post('/teachers', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teachers'] });
      setBanner(
        editingTeacher
          ? 'Teacher details updated.'
          : 'Teacher added. They can sign in using their official email and the default password.'
      );
      closeForm();
    },
    onError: (err) => {
      setFormError(getErrorMessage(err, 'Could not save teacher details.'));
    },
  });

  const actionMutation = useMutation({
    mutationFn: async ({ type, teacher }) => {
      if (type === 'deactivate') return api.post(`/teachers/${teacher.id}/deactivate`);
      if (type === 'activate') return api.post(`/teachers/${teacher.id}/activate`);
      if (type === 'reset') return api.post(`/teachers/${teacher.id}/reset-password`);
      if (type === 'delete') return api.delete(`/teachers/${teacher.id}`);
      return null;
    },
    onSuccess: (res, { type, teacher }) => {
      queryClient.invalidateQueries({ queryKey: ['teachers'] });
      setPageError('');
      if (type === 'deactivate') setBanner(`${teacher.fullName} has been deactivated.`);
      if (type === 'activate') setBanner(`${teacher.fullName} is active again.`);
      if (type === 'reset') setBanner(res?.data?.message || `Password reset for ${teacher.fullName}.`);
      if (type === 'delete') setBanner(`${teacher.fullName} has been deleted.`);
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
        <h1 className="text-lg font-semibold text-slate-900">Teachers</h1>
        <p className="mt-1.5 text-sm text-slate-600">Only the HOD can manage teachers.</p>
      </div>
    );
  }

  function openAdd() {
    setEditingTeacher(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError('');
    setFormOpen(true);
  }

  function openEdit(teacher) {
    setEditingTeacher(teacher);
    setForm({
      code: teacher.code,
      fullName: teacher.fullName,
      email: teacher.email,
      designation: teacher.designation || '',
    });
    setFieldErrors({});
    setFormError('');
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditingTeacher(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError('');
  }

  function handleSaveSubmit(e) {
    e.preventDefault();
    setFormError('');
    const errors = {};
    if (!form.code.trim()) errors.code = 'Please enter a teacher code.';
    if (!form.fullName.trim()) errors.fullName = "Please enter the teacher's full name.";
    if (!form.email.trim()) errors.email = "Please enter the teacher's official email.";
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    saveMutation.mutate({
      code: form.code.trim(),
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      designation: form.designation.trim(),
    });
  }

  const items = data?.items || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Teachers</h1>
          <p className="mt-1 text-sm text-slate-600">
            Add teachers by hand or import from a CSV file. Each new teacher gets a login with the
            default password.
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
            <span>Add teacher</span>
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
          placeholder="Search by code, name, or email..."
          className="block w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
        />
      </div>

      {isLoading && (
        <div className="rounded-lg border border-slate-200 bg-white p-8 flex items-center justify-center gap-2 text-sm text-slate-600">
          <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
          <span>Loading teachers...</span>
        </div>
      )}

      {isError && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {getErrorMessage(error, 'Could not load teachers.')}
        </div>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
          <Users className="h-7 w-7 text-slate-400 mx-auto" />
          <p className="mt-3 text-sm font-medium text-slate-900">
            {search ? 'No teachers match your search' : 'No teachers added yet'}
          </p>
          <p className="mt-1 text-sm text-slate-500">
            {search
              ? 'Try a different code, name, or email.'
              : 'Add teachers one by one or upload a CSV file to get started.'}
          </p>
          {!search && (
            <button
              type="button"
              onClick={openAdd}
              className="mt-4 inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3.5 py-2 text-sm font-medium text-white hover:bg-blue-800"
            >
              <Plus className="h-4 w-4" />
              <span>Add teacher</span>
            </button>
          )}
        </div>
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-500">
                <th className="px-4 py-3 w-24">Code</th>
                <th className="px-4 py-3">Full name</th>
                <th className="px-4 py-3">Official email</th>
                <th className="px-4 py-3">Designation</th>
                <th className="px-4 py-3 w-24">Status</th>
                <th className="px-4 py-3 text-right w-44">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {items.map((teacher) => (
                <tr key={teacher.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-3 font-medium text-slate-900">{teacher.code}</td>
                  <td className="px-4 py-3 text-slate-900">{teacher.fullName}</td>
                  <td className="px-4 py-3 text-slate-600">{teacher.email}</td>
                  <td className="px-4 py-3 text-slate-600">{teacher.designation || '—'}</td>
                  <td className="px-4 py-3">
                    {teacher.active ? (
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
                        onClick={() => openEdit(teacher)}
                        title="Edit teacher"
                        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmAction({ type: 'reset', teacher })}
                        title="Reset password to default"
                        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                      >
                        <KeyRound className="h-4 w-4" />
                      </button>
                      {teacher.active ? (
                        <button
                          type="button"
                          onClick={() => setConfirmAction({ type: 'deactivate', teacher })}
                          title="Deactivate teacher"
                          className="p-1.5 rounded-md text-slate-600 hover:bg-amber-50 hover:text-amber-700"
                        >
                          <UserX className="h-4 w-4" />
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setConfirmAction({ type: 'activate', teacher })}
                          title="Activate teacher"
                          className="p-1.5 rounded-md text-slate-600 hover:bg-emerald-50 hover:text-emerald-700"
                        >
                          <UserCheck className="h-4 w-4" />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => setConfirmAction({ type: 'delete', teacher })}
                        title="Delete teacher"
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
          <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
              <h2 className="text-base font-semibold text-slate-900">
                {editingTeacher ? 'Edit teacher' : 'Add teacher'}
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

              <div>
                <label htmlFor="tCode" className="block text-sm font-medium text-slate-700">
                  Teacher code
                </label>
                <input
                  id="tCode"
                  type="text"
                  value={form.code}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                  placeholder="e.g. T001"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
                {fieldErrors.code && <p className="mt-1 text-xs text-red-600">{fieldErrors.code}</p>}
              </div>

              <div>
                <label htmlFor="tName" className="block text-sm font-medium text-slate-700">
                  Full name
                </label>
                <input
                  id="tName"
                  type="text"
                  value={form.fullName}
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                  placeholder="e.g. Prof. Arjun Mehta"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
                {fieldErrors.fullName && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.fullName}</p>
                )}
              </div>

              <div>
                <label htmlFor="tEmail" className="block text-sm font-medium text-slate-700">
                  Official email
                </label>
                <input
                  id="tEmail"
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  placeholder="arjun.mehta@college.edu"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
                <p className="mt-1 text-xs text-slate-500">
                  Used to log in.{' '}
                  {editingTeacher
                    ? 'Editing details never resets their password.'
                    : 'Default password is ScheduliX@123 (must be changed at first login).'}
                </p>
                {fieldErrors.email && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>
                )}
              </div>

              <div>
                <label htmlFor="tDesig" className="block text-sm font-medium text-slate-700">
                  Designation
                </label>
                <input
                  id="tDesig"
                  type="text"
                  value={form.designation}
                  onChange={(e) => setForm({ ...form, designation: e.target.value })}
                  placeholder="e.g. Assistant Professor"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>

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
                  <span>{editingTeacher ? 'Save changes' : 'Add teacher'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CSV Import Modal */}
      <CsvImportModal
        open={importOpen}
        title="Import teachers from CSV"
        description="Upload a CSV with columns: facultyCode, fullName, officialEmail, designation."
        templatePath="/teachers/template"
        templateFilename="teachers_template.csv"
        previewPath="/teachers/import/preview"
        confirmPath="/teachers/import/confirm"
        columns={CSV_COLUMNS}
        onClose={() => setImportOpen(false)}
        onImported={(result) => {
          queryClient.invalidateQueries({ queryKey: ['teachers'] });
          setBanner(result.message);
        }}
      />

      {/* Confirmation Dialog */}
      <ConfirmDialog
        open={Boolean(confirmAction)}
        title={
          confirmAction?.type === 'delete'
            ? 'Delete teacher'
            : confirmAction?.type === 'deactivate'
              ? 'Deactivate teacher'
              : confirmAction?.type === 'activate'
                ? 'Activate teacher'
                : 'Reset password to default'
        }
        message={
          confirmAction?.type === 'delete'
            ? `Delete ${confirmAction.teacher.fullName} (${confirmAction.teacher.code})? This removes their record and login permanently. Teachers used in timetables or leave records cannot be deleted.`
            : confirmAction?.type === 'deactivate'
              ? `Deactivate ${confirmAction.teacher.fullName}? Their login will be switched off immediately and they will be signed out.`
              : confirmAction?.type === 'activate'
                ? `Activate ${confirmAction.teacher.fullName}? They will be able to log in again.`
                : confirmAction
                  ? `Reset ${confirmAction.teacher.fullName}'s password to ScheduliX@123? They will be signed out and asked to choose a new password on next login.`
                  : ''
        }
        confirmLabel={
          confirmAction?.type === 'delete'
            ? 'Delete'
            : confirmAction?.type === 'deactivate'
              ? 'Deactivate'
              : confirmAction?.type === 'activate'
                ? 'Activate'
                : 'Reset password'
        }
        danger={confirmAction?.type === 'delete' || confirmAction?.type === 'deactivate'}
        loading={actionMutation.isPending}
        onConfirm={() => actionMutation.mutate(confirmAction)}
        onCancel={() => setConfirmAction(null)}
      />
    </div>
  );
}