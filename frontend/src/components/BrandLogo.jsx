export default function BrandLogo({ size = 'md' }) {
  const sizes = {
    md: { icon: 'h-7 w-7', text: 'text-lg' },
    lg: { icon: 'h-8 w-8', text: 'text-xl' },
  };
  const current = sizes[size] || sizes.md;

  return (
    <span
      className={`inline-flex items-center gap-2 font-semibold tracking-tight text-slate-900 ${current.text}`}
    >
      <img
        src="/logo.png"
        alt=""
        aria-hidden="true"
        className={`${current.icon} rounded-md object-contain shrink-0`}
      />
      <span>
        Scheduli<span className="text-blue-700">X</span>
      </span>
    </span>
  );
}
