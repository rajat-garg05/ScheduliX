import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  Info,
  Layers,
  Loader2,
  Lock,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import api, { getErrorMessage } from '../api/client';
import { useAuth } from '../features/auth/authContext';
import ConfirmDialog from '../components/ConfirmDialog';
import PlanImportModal from '../features/setup/PlanImportModal';

export default function SemesterPlansPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isHod = user?.role === 'HOD';

  const [selectedSemesterId, setSelectedSemesterId] = useState(null);
  const [selectedSectionId, setSelectedSectionId] = useState(null);

  const [banner, setBanner] = useState('');
  const [pageError, setPageError] = useState('');

  // Local editable draft for the selected section's plan
  const [draftItems, setDraftItems] = useState([]);
  const [planDirty, setPlanDirty] = useState(false);
  const [addSubjectId, setAddSubjectId] = useState('');
  const [addLectures, setAddLectures] = useState('3');

  // Modals
  const [semesterModal, setSemesterModal] = useState(null); // { mode: 'add'|'edit', semester?, number, name }
  const [sectionModal, setSectionModal] = useState(null); // { mode: 'add'|'edit', section?, name }
  const [modalError, setModalError] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(null); // { kind: 'semester'|'section', item }
  const [importOpen, setImportOpen] = useState(false);

  const semestersQuery = useQuery({
    queryKey: ['semesters'],
    queryFn: async () => {
      const res = await api.get('/semesters');
      return res.data || [];
    },
    enabled: isHod,
  });

  const subjectsQuery = useQuery({
    queryKey: ['subjects', 'active-for-plan'],
    queryFn: async () => {
      const res = await api.get('/subjects', { params: { page: 0, size: 100 } });
      return (res.data.items || []).filter((s) => s.active);
    },
    enabled: isHod,
  });

  const semesters = semestersQuery.data || [];
  const activeSubjects = subjectsQuery.data || [];

  // Auto-select first semester
  useEffect(() => {
    if (semesters.length === 0) {
      setSelectedSemesterId(null);
      return;
    }
    if (!semesters.some((s) => s.id === selectedSemesterId)) {
      setSelectedSemesterId(semesters[0].id);
    }
  }, [semesters, selectedSemesterId]);

  const sectionsQuery = useQuery({
    queryKey: ['sections', selectedSemesterId],
    queryFn: async () => {
      const res = await api.get(`/semesters/${selectedSemesterId}/sections`);
      return res.data || [];
    },
    enabled: isHod && Boolean(selectedSemesterId),
  });

  const sections = sectionsQuery.data || [];

  // Auto-select first section when semester changes
  useEffect(() => {
    if (sections.length === 0) {
      setSelectedSectionId(null);
      return;
    }
    if (!sections.some((s) => s.id === selectedSectionId)) {
      setSelectedSectionId(sections[0].id);
    }
  }, [sections, selectedSectionId]);

  const planQuery = useQuery({
    queryKey: ['section-plan', selectedSectionId],
    queryFn: async () => {
      const res = await api.get(`/sections/${selectedSectionId}/plan`);
      return res.data;
    },
    enabled: isHod && Boolean(selectedSectionId),
  });

  const plan = planQuery.data || null;

  useEffect(() => {
    if (!plan) {
      setDraftItems([]);
      setPlanDirty(false);
      return;
    }
    setDraftItems(
      (plan.items || []).map((item) => ({
        subjectId: item.subjectId,
        subjectCode: item.subjectCode,
        subjectName: item.subjectName,
        kind: item.kind,
        periodsInARow: item.periodsInARow,
        runTogetherGroup: item.runTogetherGroup,
        sharedPlace: item.sharedPlace,
        lecturesPerWeek: item.lecturesPerWeek,
        hasTeacher: item.hasTeacher,
      }))
    );
    setPlanDirty(false);
  }, [plan]);

  const saveSemesterMutation = useMutation({
    mutationFn: ({ mode, semester, number, name }) =>
      mode === 'edit'
        ? api.put(`/semesters/${semester.id}`, { number: Number(number), name: name.trim() })
        : api.post('/semesters', { number: Number(number), name: name.trim() }),
    onSuccess: (res, vars) => {
      queryClient.invalidateQueries({ queryKey: ['semesters'] });
      if (vars.mode === 'add' && res.data?.id) {
        setSelectedSemesterId(res.data.id);
      }
      setBanner(vars.mode === 'edit' ? 'Semester updated.' : 'Semester created.');
      setSemesterModal(null);
      setModalError('');
    },
    onError: (err) => setModalError(getErrorMessage(err, 'Could not save semester.')),
  });

  const saveSectionMutation = useMutation({
    mutationFn: ({ mode, section, name }) =>
      mode === 'edit'
        ? api.put(`/sections/${section.id}`, { name: name.trim() })
        : api.post(`/semesters/${selectedSemesterId}/sections`, { name: name.trim() }),
    onSuccess: (res, vars) => {
      queryClient.invalidateQueries({ queryKey: ['sections', selectedSemesterId] });
      if (vars.mode === 'add' && res.data?.id) {
        setSelectedSectionId(res.data.id);
      }
      setBanner(vars.mode === 'edit' ? 'Section renamed.' : 'Section added.');
      setSectionModal(null);
      setModalError('');
    },
    onError: (err) => setModalError(getErrorMessage(err, 'Could not save section.')),
  });

  const deleteMutation = useMutation({
    mutationFn: ({ kind, item }) =>
      kind === 'semester' ? api.delete(`/semesters/${item.id}`) : api.delete(`/sections/${item.id}`),
    onSuccess: (_, { kind, item }) => {
      setPageError('');
      if (kind === 'semester') {
        queryClient.invalidateQueries({ queryKey: ['semesters'] });
        setBanner(`Deleted ${item.name}.`);
      } else {
        queryClient.invalidateQueries({ queryKey: ['sections', selectedSemesterId] });
        setBanner(`Deleted Section ${item.name}.`);
      }
      setConfirmDelete(null);
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Could not delete.'));
      setConfirmDelete(null);
    },
  });

  const savePlanMutation = useMutation({
    mutationFn: (items) =>
      api.put(
        `/sections/${selectedSectionId}/plan`,
        items.map((item) => ({
          subjectId: item.subjectId,
          lecturesPerWeek: Number(item.lecturesPerWeek),
        }))
      ),
    onSuccess: (res) => {
      queryClient.setQueryData(['section-plan', selectedSectionId], res.data);
      setPageError('');
      setBanner('Semester plan saved.');
    },
    onError: (err) => {
      setPageError(getErrorMessage(err, 'Could not save semester plan.'));
    },
  });

  const availableSubjectsToAdd = useMemo(() => {
    const used = new Set(draftItems.map((i) => i.subjectId));
    return activeSubjects.filter((s) => !used.has(s.id));
  }, [activeSubjects, draftItems]);

  const draftTotalLectures = useMemo(
    () => draftItems.reduce((sum, item) => sum + (Number(item.lecturesPerWeek) || 0), 0),
    [draftItems]
  );

  if (!isHod) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-slate-900">Semester plans</h1>
        <p className="mt-1.5 text-sm text-slate-600">Only the HOD can manage semester plans.</p>
      </div>
    );
  }

  const selectedSemester = semesters.find((s) => s.id === selectedSemesterId) || null;
  const selectedSection = sections.find((s) => s.id === selectedSectionId) || null;
  const isLocked = Boolean(plan?.locked);

  function handleAddSubjectRow(e) {
    e.preventDefault();
    if (!addSubjectId) return;
    const subject = activeSubjects.find((s) => s.id === addSubjectId);
    if (!subject) return;

    const lecturesNum = Number(addLectures);
    if (!Number.isInteger(lecturesNum) || lecturesNum < 1 || lecturesNum > 40) {
      setPageError('Lectures per week must be a whole number between 1 and 40.');
      return;
    }

    setPageError('');
    setDraftItems((prev) =>
      [
        ...prev,
        {
          subjectId: subject.id,
          subjectCode: subject.code,
          subjectName: subject.name,
          kind: subject.kind,
          periodsInARow: subject.periodsInARow,
          runTogetherGroup: subject.runTogetherGroup,
          sharedPlace: subject.sharedPlace,
          lecturesPerWeek: lecturesNum,
          hasTeacher: true,
        },
      ].sort((a, b) => a.subjectCode.localeCompare(b.subjectCode))
    );
    setPlanDirty(true);
    setAddSubjectId('');
    setAddLectures('3');
  }

  function handleUpdateLectures(subjectId, rawValue) {
    setDraftItems((prev) =>
      prev.map((item) =>
        item.subjectId === subjectId
          ? { ...item, lecturesPerWeek: rawValue === '' ? '' : Number(rawValue) }
          : item
      )
    );
    setPlanDirty(true);
  }

  function handleRemoveRow(subjectId) {
    setDraftItems((prev) => prev.filter((item) => item.subjectId !== subjectId));
    setPlanDirty(true);
  }

  function handleResetDraft() {
    if (!plan) return;
    setDraftItems(plan.items || []);
    setPlanDirty(false);
    setPageError('');
  }

  function openAddSemester() {
    setModalError('');
    const nextNum = semesters.length > 0 ? Math.min(12, semesters[semesters.length - 1].number + 1) : 1;
    setSemesterModal({ mode: 'add', number: String(nextNum), name: `Semester ${nextNum}` });
  }

  function openEditSemester(semester) {
    setModalError('');
    setSemesterModal({ mode: 'edit', semester, number: String(semester.number), name: semester.name });
  }

  function openAddSection() {
    setModalError('');
    const nextLetter = String.fromCharCode(65 + Math.min(sections.length, 25));
    setSectionModal({ mode: 'add', name: nextLetter });
  }

  function openEditSection(section) {
    setModalError('');
    setSectionModal({ mode: 'edit', section, name: section.name });
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Semester plans</h1>
        <p className="mt-1 text-sm text-slate-600">
          Set up semesters, sections, and how many lectures each subject gets every week.
        </p>
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

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Column 1: Semesters */}
        <div className="lg:col-span-3 rounded-lg border border-slate-200 bg-white">
          <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              1. Semesters
            </h2>
            <button
              type="button"
              onClick={openAddSemester}
              className="inline-flex items-center gap-1 rounded-md bg-blue-700 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-800"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Add</span>
            </button>
          </div>

          {semestersQuery.isLoading ? (
            <div className="p-6 flex items-center justify-center gap-2 text-xs text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
              <span>Loading...</span>
            </div>
          ) : semesters.length === 0 ? (
            <div className="p-6 text-center">
              <Layers className="h-6 w-6 text-slate-400 mx-auto" />
              <p className="mt-2 text-sm font-medium text-slate-800">No semesters yet</p>
              <p className="mt-1 text-xs text-slate-500">Add Semester 3 to get started.</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {semesters.map((sem) => {
                const selected = sem.id === selectedSemesterId;
                return (
                  <div
                    key={sem.id}
                    className={`flex items-center justify-between gap-2 px-3 py-2.5 ${
                      selected ? 'bg-blue-50 border-l-2 border-l-blue-700' : 'hover:bg-slate-50'
                    }`}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedSemesterId(sem.id);
                        setPageError('');
                        setBanner('');
                      }}
                      className="flex-1 text-left min-w-0"
                    >
                      <div className="text-sm font-medium text-slate-900 truncate">{sem.name}</div>
                      <div className="text-xs text-slate-500">Number {sem.number}</div>
                    </button>
                    <div className="flex items-center gap-0.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => openEditSemester(sem)}
                        title="Edit semester"
                        className="p-1 rounded text-slate-500 hover:bg-white hover:text-slate-800"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDelete({ kind: 'semester', item: sem })}
                        title="Delete semester"
                        className="p-1 rounded text-slate-500 hover:bg-red-50 hover:text-red-700"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Column 2: Sections */}
        <div className="lg:col-span-3 rounded-lg border border-slate-200 bg-white">
          <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              2. Sections
            </h2>
            <button
              type="button"
              disabled={!selectedSemester}
              onClick={openAddSection}
              className="inline-flex items-center gap-1 rounded-md bg-blue-700 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-40"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Add</span>
            </button>
          </div>

          {!selectedSemester ? (
            <div className="p-6 text-center text-xs text-slate-500">
              Pick a semester on the left first.
            </div>
          ) : sectionsQuery.isLoading ? (
            <div className="p-6 flex items-center justify-center gap-2 text-xs text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
              <span>Loading...</span>
            </div>
          ) : sections.length === 0 ? (
            <div className="p-6 text-center">
              <p className="text-sm font-medium text-slate-800">No sections yet</p>
              <p className="mt-1 text-xs text-slate-500">Add Section A to start.</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {sections.map((sec) => {
                const selected = sec.id === selectedSectionId;
                return (
                  <div
                    key={sec.id}
                    className={`flex items-center justify-between gap-2 px-3 py-2.5 ${
                      selected ? 'bg-blue-50 border-l-2 border-l-blue-700' : 'hover:bg-slate-50'
                    }`}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedSectionId(sec.id);
                        setPageError('');
                        setBanner('');
                      }}
                      className="flex-1 text-left min-w-0 flex items-center gap-2"
                    >
                      <span className="text-sm font-medium text-slate-900 truncate">
                        Section {sec.name}
                      </span>
                      {sec.locked && (
                        <Lock
                          className="h-3.5 w-3.5 text-amber-600 shrink-0"
                          title="Has a saved timetable"
                        />
                      )}
                    </button>
                    <div className="flex items-center gap-0.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => openEditSection(sec)}
                        title="Rename section"
                        className="p-1 rounded text-slate-500 hover:bg-white hover:text-slate-800"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDelete({ kind: 'section', item: sec })}
                        title="Delete section"
                        className="p-1 rounded text-slate-500 hover:bg-red-50 hover:text-red-700"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Column 3: Semester Plan Table */}
        <div className="lg:col-span-6 rounded-lg border border-slate-200 bg-white">
          <div className="px-4 py-3.5 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                3. Semester plan
              </h2>
              {selectedSemester && selectedSection && (
                <p className="mt-0.5 text-sm font-semibold text-slate-900">
                  {selectedSemester.name} — Section {selectedSection.name}
                </p>
              )}
            </div>

            {selectedSection && (
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={isLocked}
                  onClick={() => setImportOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                >
                  <Upload className="h-3.5 w-3.5" />
                  <span>Upload file</span>
                </button>
                {planDirty && (
                  <button
                    type="button"
                    onClick={handleResetDraft}
                    className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    <span>Reset</span>
                  </button>
                )}
                <button
                  type="button"
                  disabled={isLocked || !planDirty || savePlanMutation.isPending}
                  onClick={() => savePlanMutation.mutate(draftItems)}
                  className="inline-flex items-center gap-1.5 rounded-md bg-blue-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-800 disabled:opacity-50"
                >
                  {savePlanMutation.isPending ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Save className="h-3.5 w-3.5" />
                  )}
                  <span>{savePlanMutation.isPending ? 'Saving...' : 'Save plan'}</span>
                </button>
              </div>
            )}
          </div>

          {!selectedSection ? (
            <div className="p-10 text-center">
              <BookOpen className="h-7 w-7 text-slate-400 mx-auto" />
              <p className="mt-2.5 text-sm font-medium text-slate-900">No section selected</p>
              <p className="mt-1 text-sm text-slate-500">
                Create or pick a semester and section to build its weekly subject plan.
              </p>
            </div>
          ) : planQuery.isLoading ? (
            <div className="p-10 flex items-center justify-center gap-2 text-sm text-slate-600">
              <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
              <span>Loading plan...</span>
            </div>
          ) : (
            <div className="p-4 space-y-4">
              {/* Lock banner */}
              {isLocked && (
                <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-900">
                  <Lock className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
                  <span>{plan.lockMessage}</span>
                </div>
              )}

              {/* Summary strip */}
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 bg-slate-50 px-3.5 py-2 text-xs text-slate-700">
                <div className="flex items-center gap-4">
                  <span>
                    Subjects: <strong className="text-slate-900">{draftItems.length}</strong>
                  </span>
                  <span>
                    Total lectures / week:{' '}
                    <strong className="text-slate-900">{draftTotalLectures}</strong>
                  </span>
                </div>
                {planDirty && (
                  <span className="font-medium text-blue-700">Unsaved changes</span>
                )}
              </div>

              {/* Warnings */}
              {plan?.warnings?.length > 0 && !planDirty && (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-900 space-y-1">
                  <div className="font-medium flex items-center gap-1.5">
                    <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />
                    <span>Teachers missing for some subjects:</span>
                  </div>
                  <ul className="list-disc pl-5 space-y-0.5">
                    {plan.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Add subject row */}
              {!isLocked && (
                <form
                  onSubmit={handleAddSubjectRow}
                  className="flex flex-wrap items-end gap-2.5 rounded-md border border-slate-200 bg-slate-50/70 p-3"
                >
                  <div className="flex-1 min-w-[200px]">
                    <label
                      htmlFor="addPlanSubject"
                      className="block text-xs font-medium text-slate-600"
                    >
                      Add subject
                    </label>
                    <select
                      id="addPlanSubject"
                      value={addSubjectId}
                      onChange={(e) => {
                        const id = e.target.value;
                        setAddSubjectId(id);
                        const chosen = activeSubjects.find((s) => s.id === id);
                        if (chosen) {
                          setAddLectures(String(chosen.kind === 'LAB' ? chosen.periodsInARow : 3));
                        }
                      }}
                      className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                    >
                      <option value="">Choose a subject...</option>
                      {availableSubjectsToAdd.map((sub) => (
                        <option key={sub.id} value={sub.id}>
                          {sub.code} — {sub.name}
                          {sub.kind === 'LAB' ? ` (Lab, ${sub.periodsInARow} in a row)` : ''}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="w-28">
                    <label
                      htmlFor="addPlanLectures"
                      className="block text-xs font-medium text-slate-600"
                    >
                      Lectures / week
                    </label>
                    <input
                      id="addPlanLectures"
                      type="number"
                      min={1}
                      max={40}
                      value={addLectures}
                      onChange={(e) => setAddLectures(e.target.value)}
                      className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={!addSubjectId}
                    className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                  >
                    <Plus className="h-4 w-4" />
                    <span>Add</span>
                  </button>
                </form>
              )}

              {/* Plan table */}
              {draftItems.length === 0 ? (
                <div className="rounded-md border border-dashed border-slate-200 p-8 text-center">
                  <p className="text-sm font-medium text-slate-800">No subjects in this plan yet</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Add subjects above or click Upload file to load a CSV plan.
                  </p>
                </div>
              ) : (
                <div className="rounded-md border border-slate-200 overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-500">
                        <th className="px-3 py-2.5 w-24">Code</th>
                        <th className="px-3 py-2.5">Subject</th>
                        <th className="px-3 py-2.5 w-28">Lectures / week</th>
                        {!isLocked && <th className="px-3 py-2.5 w-16 text-right">Remove</th>}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                      {draftItems.map((item) => (
                        <tr key={item.subjectId} className="hover:bg-slate-50/60">
                          <td className="px-3 py-2.5 font-medium text-slate-900">
                            {item.subjectCode}
                          </td>
                          <td className="px-3 py-2.5">
                            <div className="text-slate-900">{item.subjectName}</div>
                            <div className="text-xs text-slate-500">
                              {item.kind === 'LAB'
                                ? `Lab · ${item.periodsInARow} in a row`
                                : 'Theory'}
                              {item.runTogetherGroup
                                ? ` · Runs with ${item.runTogetherGroup}`
                                : ''}
                              {!item.hasTeacher && !planDirty ? ' · No teacher assigned' : ''}
                            </div>
                          </td>
                          <td className="px-3 py-2.5">
                            {isLocked ? (
                              <span className="font-medium text-slate-800">
                                {item.lecturesPerWeek}
                              </span>
                            ) : (
                              <input
                                type="number"
                                min={1}
                                max={40}
                                value={item.lecturesPerWeek}
                                onChange={(e) =>
                                  handleUpdateLectures(item.subjectId, e.target.value)
                                }
                                className="w-20 rounded-md border border-slate-300 px-2 py-1 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                              />
                            )}
                          </td>
                          {!isLocked && (
                            <td className="px-3 py-2.5 text-right">
                              <button
                                type="button"
                                onClick={() => handleRemoveRow(item.subjectId)}
                                title="Remove subject"
                                className="p-1.5 rounded-md text-slate-500 hover:bg-red-50 hover:text-red-700"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Helpful tip */}
              <div className="flex items-start gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
                <Info className="h-4 w-4 shrink-0 text-slate-400 mt-0.5" />
                <span>
                  Tip: Use a different &ldquo;Runs at the same time as&rdquo; group name for each
                  semester (for example, LABGROUP-S3 for Semester 3 and LABGROUP-S5 for Semester 5).
                </span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Semester Add/Edit Modal */}
      {semesterModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
              <h2 className="text-base font-semibold text-slate-900">
                {semesterModal.mode === 'edit' ? 'Edit semester' : 'Add semester'}
              </h2>
              <button
                type="button"
                onClick={() => setSemesterModal(null)}
                className="p-1 rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                setModalError('');
                const num = Number(semesterModal.number);
                if (!Number.isInteger(num) || num < 1 || num > 12) {
                  setModalError('Semester number must be between 1 and 12.');
                  return;
                }
                if (!semesterModal.name.trim()) {
                  setModalError('Please enter a semester name.');
                  return;
                }
                saveSemesterMutation.mutate(semesterModal);
              }}
              className="px-5 py-4 space-y-4"
            >
              {modalError && (
                <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {modalError}
                </div>
              )}
              <div>
                <label htmlFor="semNumber" className="block text-sm font-medium text-slate-700">
                  Semester number (1 to 12)
                </label>
                <input
                  id="semNumber"
                  type="number"
                  min={1}
                  max={12}
                  value={semesterModal.number}
                  onChange={(e) => {
                    const nextNum = e.target.value;
                    setSemesterModal((prev) => ({
                      ...prev,
                      number: nextNum,
                      name:
                        prev.mode === 'add' &&
                        (!prev.name || prev.name === `Semester ${prev.number}`)
                          ? `Semester ${nextNum}`
                          : prev.name,
                    }));
                  }}
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>
              <div>
                <label htmlFor="semName" className="block text-sm font-medium text-slate-700">
                  Semester name
                </label>
                <input
                  id="semName"
                  type="text"
                  value={semesterModal.name}
                  onChange={(e) => setSemesterModal({ ...semesterModal, name: e.target.value })}
                  placeholder="e.g. Semester 3"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setSemesterModal(null)}
                  className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saveSemesterMutation.isPending}
                  className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
                >
                  {saveSemesterMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>Save</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Section Add/Rename Modal */}
      {sectionModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
              <h2 className="text-base font-semibold text-slate-900">
                {sectionModal.mode === 'edit' ? 'Rename section' : 'Add section'}
              </h2>
              <button
                type="button"
                onClick={() => setSectionModal(null)}
                className="p-1 rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                setModalError('');
                if (!sectionModal.name.trim()) {
                  setModalError('Please enter a section name.');
                  return;
                }
                saveSectionMutation.mutate(sectionModal);
              }}
              className="px-5 py-4 space-y-4"
            >
              {modalError && (
                <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {modalError}
                </div>
              )}
              <div>
                <label htmlFor="secName" className="block text-sm font-medium text-slate-700">
                  Section name
                </label>
                <input
                  id="secName"
                  type="text"
                  value={sectionModal.name}
                  onChange={(e) => setSectionModal({ ...sectionModal, name: e.target.value })}
                  placeholder="e.g. A"
                  className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setSectionModal(null)}
                  className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saveSectionMutation.isPending}
                  className="inline-flex items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-60"
                >
                  {saveSectionMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>Save</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CSV Upload Modal */}
      <PlanImportModal
        open={importOpen}
        semesterName={selectedSemester?.name || ''}
        primarySection={selectedSection}
        siblingSections={sections}
        onClose={() => setImportOpen(false)}
        onImported={(result) => {
          queryClient.invalidateQueries({ queryKey: ['section-plan'] });
          setBanner(result.message);
          setPageError('');
        }}
      />

      {/* Delete Confirmation */}
      <ConfirmDialog
        open={Boolean(confirmDelete)}
        title={confirmDelete?.kind === 'semester' ? 'Delete semester' : 'Delete section'}
        message={
          confirmDelete?.kind === 'semester'
            ? `Delete ${confirmDelete.item.name}? You can only delete a semester when it has no sections.`
            : confirmDelete
              ? `Delete Section ${confirmDelete.item.name}? This will also remove its semester plan. Sections with a saved timetable cannot be deleted.`
              : ''
        }
        confirmLabel="Delete"
        danger
        loading={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate(confirmDelete)}
        onCancel={() => setConfirmDelete(null)}
      />
    </div>
  );
}