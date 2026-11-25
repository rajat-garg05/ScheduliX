export default function ComingSoonPage({ title }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6">
      <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
      <p className="mt-2 text-sm text-slate-600">
        This section is not ready yet. It will be added in an upcoming step.
      </p>
    </div>
  );
}