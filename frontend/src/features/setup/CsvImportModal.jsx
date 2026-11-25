import { useState } from 'react';
import { Download, Loader2, Upload, X } from 'lucide-react';
import api, { getErrorMessage } from '../../api/client';

const ROW_STYLE = {
  NEW: 'bg-emerald-50/70',
  UPDATE: 'bg-blue-50/70',
  UNCHANGED: 'bg-white',
  PROBLEM: 'bg-red-50/80',
};

const BADGE_STYLE = {
  NEW: 'bg-emerald-100 text-emerald-800',
  UPDATE: 'bg-blue-100 text-blue-800',
  UNCHANGED: 'bg-slate-100 text-slate-600',
  PROBLEM: 'bg-red-100 text-red-700',
};

const STATUS_LABEL = {
  NEW: 'New',
  UPDATE: 'Update',
  UNCHANGED: 'Skipped',
  PROBLEM: 'Problem',
};

export default function CsvImportModal({
  open,
  title,
  description,
  templatePath,
  templateFilename,
  previewPath,
  confirmPath,
  columns,
  showMode = true,
  onClose,
  onImported,
}) {
  const [file, setFile] = useState(null);
  const [mode, setMode] = useState('ADD_ONLY');
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState('');

  if (!open) return null;

  function resetAndClose() {
    setFile(null);
    setMode('ADD_ONLY');
    setPreview(null);
    setError('');
    onClose();
  }

  async function handleDownloadTemplate() {
    setDownloading(true);
    setError('');
    try {
      const res = await api.get(templatePath, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = templateFilename;
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

  async function runPreview(selectedFile, selectedMode) {
    if (!selectedFile) return;
    setLoadingPreview(true);
    setError('');
    setPreview(null);

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      if (showMode) {
        formData.append('mode', selectedMode);
      }
      const res = await api.post(previewPath, formData, {
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
    setError('');
    if (picked) {
      runPreview(picked, mode);
    }
  }

  function handleModeChange(nextMode) {
    setMode(nextMode);
    if (file) {
      runPreview(file, nextMode);
    }
  }

  async function handleConfirm() {
    if (!file) return;
    setConfirming(true);
    setError('');
    try {
      const formData = new FormData();
      formData.append('file', file);
      if (showMode) {
        formData.append('mode', mode);
      }
      const res = await api.post(confirmPath, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      onImported(res.data);
      resetAndClose();
    } catch (err) {
      setError(getErrorMessage(err, 'Import failed. Nothing was saved.'));
    } finally {
      setConfirming(false);
    }
  }

  const canConfirm = Boolean(preview && (preview.toAdd > 0 || preview.toUpdate > 0));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-4xl max-h-[90vh] flex flex-col rounded-lg border border-slate-200 bg-white shadow-sm">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <div>
            <h2 className="text-base font-semibold text-slate-900">{title}</h2>
            {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
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
              Need the exact column names? Download the sample CSV template first (up to 2,000 rows,
              2 MB max).
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

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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

            {showMode && (
              <div>
                <span className="block text-sm font-medium text-slate-700">
                  If a code already exists
                </span>
                <div className="mt-2 flex flex-col gap-1.5 text-sm text-slate-700">
                  <label className="inline-flex items-center gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="importMode"
                      value="ADD_ONLY"
                      checked={mode === 'ADD_ONLY'}
                      onChange={() => handleModeChange('ADD_ONLY')}
                      className="text-blue-700 focus:ring-blue-600"
                    />
                    <span>Add new only (skip existing codes)</span>
                  </label>
                  <label className="inline-flex items-center gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="importMode"
                      value="ADD_AND_UPDATE"
                      checked={mode === 'ADD_AND_UPDATE'}
                      onChange={() => handleModeChange('ADD_AND_UPDATE')}
                      className="text-blue-700 focus:ring-blue-600"
                    />
                    <span>Add new and update existing</span>
                  </label>
                </div>
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
              <span>Checking file rows...</span>
            </div>
          )}

          {preview && (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm">
                <span className="font-medium text-slate-900">{preview.summary}</span>
                <span className="text-xs text-slate-500">
                  Nothing is saved until you click Confirm import.
                </span>
              </div>

              <div className="rounded-md border border-slate-200 overflow-x-auto max-h-80">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="sticky top-0 bg-slate-100 border-b border-slate-200 text-slate-600">
                    <tr>
                      <th className="px-3 py-2 w-14">Row</th>
                      <th className="px-3 py-2 w-24">Status</th>
                      {columns.map((col) => (
                        <th key={col.key} className="px-3 py-2">
                          {col.label}
                        </th>
                      ))}
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
                        {columns.map((col) => (
                          <td key={col.key} className="px-3 py-2 text-slate-800">
                            {row.values?.[col.key] || '—'}
                          </td>
                        ))}
                        <td className="px-3 py-2 text-slate-700">{row.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
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
            <span>{confirming ? 'Importing...' : 'Confirm import'}</span>
          </button>
        </div>
      </div>
    </div>
  );
}