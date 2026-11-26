import { Link } from 'react-router-dom';
import BrandLogo from '../../components/BrandLogo';

export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center px-4 py-10">
      <div className="w-full max-w-md mx-auto">
        <div className="mb-6 text-center">
          <Link to="/" aria-label="ScheduliX" className="inline-flex items-center">
            <BrandLogo size="lg" />
          </Link>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-6 sm:p-7">
          <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-slate-600">{subtitle}</p>}
          <div className="mt-5">{children}</div>
        </div>

        {footer && <div className="mt-5 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </div>
  );
}