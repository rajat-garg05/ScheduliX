import { useState } from 'react';
import { AlertTriangle, Download, Loader2, Lock, Upload, X } from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

const ROW_STYLE = {
  NEW: 'bg-emerald-50/70',
  CHANGED: 'bg-blue-50/70',
  UNCHANGED: 'bg-white',
  PROBLEM: 'bg-red-50/80',
};

const BADGE_STYLE = {
  NEW: 'bg-emerald-100 text-emerald-800',
  CHANGED: 'bg-blue-100 text-blue-800',
  UNCHANGED: 'bg-slate-100 text-slate-600',
  PROBLEM: 'bg-red-100 text-red-700',
};

const STATUS_LABEL = {
  NEW: 'New',
  CHANGED: 'Changed',
  UNCHANGED: 'Unchanged',
  PROBLEM: 'Problem',
};

export default function PlanImportModal({
  open,
  semesterName,
  primarySection,
  siblingSections,
  onClose,
  onImported,
}) {
  const [file, setFile] = useState(null);
  const [selectedOtherIds, setSelectedOtherIds] = useState([]);
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [replaceConfirmed, setReplaceConfirmed] = useState(false);
  const [error, setError] = useState('');

  if (!open || !primarySection) return null;

  const otherSections = (siblingSections || []).filter((s) => s.id !== primarySection.id);

  function resetAndClose() {
    setFile(null);
    setSelectedOtherIds([]);
    setPreview(null);
    setReplaceConfirmed(false);
    setError('');
    onClose();
  }

  async function handleDownloadTemplate() {
    setDownloading(true);
    setError('');
    try {
      const res = await api.get('/plan/template', { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'semester_plan_template.csv';
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not download the template file.'));
    } finally {
      setDownloading(false);
    }
  }

  function buildFormData(selectedFile, otherIds) {
    const formData = new FormData();
    formData.append('file', selectedFile);
    for (const id of otherIds) {
      formData.append('otherSectionIds', id);
    }
    return formData;
  }

  async function runPreview(selectedFile, otherIds) {
    if (!selectedFile) return;
    setLoadingPreview(true);
    setError('');
    setPreview(null);

    try {
      const formData = buildFormData(selectedFile, otherIds);
      const res = await api.post(`/sections/${primarySection.id}/plan/import/preview`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setPreview(res.data);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not read this file.'));
    } finally {
      setLoadingPreview(false);
    }
  }

  function handleFileChange(e) {
    const picked = e.target.files?.[0] || null;
    setFile(picked);
    setPreview(null);
    setReplaceConfirmed(false);
    setError('');
    if (picked) {
      runPreview(picked, selectedOtherIds);
    }
  }

  function toggleOtherSection(sectionId) {
    const next = selectedOtherIds.includes(sectionId)
      ? selectedOtherIds.filter((id) => id !== sectionId)
      : [...selectedOtherIds, sectionId];
    setSelectedOtherIds(next);
    setReplaceConfirmed(false);
    if (file) {
      runPreview(file, next);
    }
  }

  async function handleConfirm() {
    if (!file) return;
    setConfirming(true);
    setError('');
    try {
      const formData = buildFormData(file, selectedOtherIds);
      const res = await api.post(`/sections/${primarySection.id}/plan/import/confirm`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      onImported(res.data);
      resetAndClose();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save the uploaded plan.'));
    } finally {
      setConfirming(false);
    }
  }

  const needsReplaceAck = selectedOtherIds.length > 0;
  const canConfirm = Boolean(preview?.canConfirm && (!needsReplaceAck || replaceConfirmed));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-4xl max-h-[90vh] flex flex-col rounded-lg border border-slate-200 bg-white shadow-sm">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="text-base font-semibold text-slate-900">
              Upload semester plan — {semesterName}, Section {primarySection.name}
            </h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Upload a CSV with columns: subjectCode, lecturesPerWeek. This replaces the current
              plan for the chosen sections.
            </p>
          </div>
          <button
            type="button"
            onClick={resetAndClose}
            disabled={confirming}
            className="p-1.5 rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-slate-50 p-3.5">
            <div className="text-xs text-slate-600">
              Download the template to check the exact column names (subjectCode, lecturesPerWeek).
            </div>
            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={downloading}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            >
              {downloading ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Download className="h-3.5 w-3.5" />
              )}
              <span>Download template</span>
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700">Choose CSV file</label>
              <label className="mt-1.5 flex items-center gap-2.5 rounded-md border border-dashed border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-700 hover:border-blue-500 cursor-pointer">
                <Upload className="h-4 w-4 text-slate-500 shrink-0" />
                <span className="truncate">{file ? file.name : 'Select a .csv file...'}</span>
                <input
                  type="file"
                  accept=".csv,text/csv"
                  onChange={handleFileChange}
                  className="hidden"
                />
              </label>
            </div>

            {otherSections.length > 0 && (
              <div>
                <span className="block text-sm font-medium text-slate-700">
                  Use this file for other sections too
                </span>
                <div className="mt-2 flex flex-wrap gap-2">
                  {otherSections.map((sec) => {
                    const checked = selectedOtherIds.includes(sec.id);
                    return (
                      <label
                        key={sec.id}
                        className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-xs font-medium ${
                          sec.locked
                            ? 'border-slate-200 bg-slate-100 text-slate-400 cursor-not-allowed'
                            : checked
                              ? 'border-blue-600 bg-blue-50 text-blue-800 cursor-pointer'
                              : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50 cursor-pointer'
                        }`}
                        title={
                          sec.locked
                            ? 'Locked because this section has a saved timetable'
                            : `Apply to Section ${sec.name}`
                        }
                      >
                        <input
                          type="checkbox"
                          disabled={sec.locked}
                          checked={checked}
                          onChange={() => toggleOtherSection(sec.id)}
                          className="h-3.5 w-3.5 rounded border-slate-300 text-blue-700 focus:ring-blue-600"
                        />
                        <span>Section {sec.name}</span>
                        {sec.locked && <Lock className="h-3 w-3" />}
                      </label>
                    );
                  })}
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  Sections with a saved timetable are locked and cannot be overwritten.
                </p>
              </div>
            )}
          </div>

          {error && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
              {error}
            </div>
          )}

          {loadingPreview && (
            <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-600">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
              <span>Checking plan rows...</span>
            </div>
          )}

          {preview && (
            <div className="space-y-3">
              {preview.lockErrors?.length > 0 && (
                <div className="rounded-md border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700 space-y-1">
                  {preview.lockErrors.map((msg, i) => (
                    <p key={i}>{msg}</p>
                  ))}
                </div>
              )}

              <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm">
                <span className="font-medium text-slate-900">{preview.summary}</span>
                <span className="text-xs text-slate-500">
                  Nothing is saved until you click Confirm upload.
                </span>
              </div>

              {preview.warnings?.length > 0 && (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-900 space-y-1">
                  <div className="font-medium flex items-center gap-1.5">
                    <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />
                    <span>Warnings (you can still save, then assign teachers later):</span>
                  </div>
                  <ul className="list-disc pl-5 space-y-0.5">
                    {preview.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}

              <div className="rounded-md border border-slate-200 overflow-x-auto max-h-72">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="sticky top-0 bg-slate-100 border-b border-slate-200 text-slate-600">
                    <tr>
                      <th className="px-3 py-2 w-14">Row</th>
                      <th className="px-3 py-2 w-24">Status</th>
                      <th className="px-3 py-2 w-28">Subject code</th>
                      <th className="px-3 py-2">Subject name</th>
                      <th className="px-3 py-2 w-28">Lectures / week</th>
                      <th className="px-3 py-2">Details</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {preview.rows.map((row) => (
                      <tr key={row.rowNumber} className={ROW_STYLE[row.status] || 'bg-white'}>
                        <td className="px-3 py-2 text-slate-500 font-medium">{row.rowNumber}</td>
                        <td className="px-3 py-2">
                          <span
                            className={`inline-flex rounded px-2 py-0.5 text-[11px] font-medium ${
                              BADGE_STYLE[row.status] || 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {STATUS_LABEL[row.status] || row.status}
                          </span>
                        </td>
                        <td className="px-3 py-2 font-medium text-slate-800">
                          {row.subjectCode || '—'}
                        </td>
                        <td className="px-3 py-2 text-slate-800">{row.subjectName || '—'}</td>
                        <td className="px-3 py-2 text-slate-800">{row.lecturesPerWeek || '—'}</td>
                        <td className="px-3 py-2 text-slate-700">{row.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {needsReplaceAck && preview.canConfirm && (
                <label className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50/70 px-3.5 py-2.5 text-xs text-amber-900 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={replaceConfirmed}
                    onChange={(e) => setReplaceConfirmed(e.target.checked)}
                    className="mt-0.5 h-4 w-4 rounded border-amber-400 text-blue-700 focus:ring-blue-600"
                  />
                  <span>
                    I understand this will replace the existing semester plan for{' '}
                    <strong>
                      {preview.targetSections.map((s) => `Section ${s.name}`).join(', ')}
                    </strong>
                    .
                  </span>
                </label>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-slate-200 bg-slate-50 flex items-center justify-end gap-2.5 rounded-b-lg">
          <button
            type="button"
            onClick={resetAndClose}
            disabled={confirming}
            className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={!canConfirm || confirming || loadingPreview}
            className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
          >
            {confirming && <Loader2 className="h-4 w-4 animate-spin" />}
            <span>{confirming ? 'Saving...' : 'Confirm upload'}</span>
          </button>
        </div>
      </div>
    </div>
  );
}