import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/authContext';
import { flattenMenuItems } from '../components/menuItems';

export default function DashboardPage() {
  const { user } = useAuth();
  const shortcuts = flattenMenuItems(user?.role).filter((item) => item.to !== '/dashboard');

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">
        Welcome, {user?.displayName}
      </h1>
      <p className="mt-1 text-sm text-slate-600">
        Choose a section below to get started.
      </p>

      <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {shortcuts.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.to}
              to={item.to}
              className="group rounded-lg border border-slate-200 bg-white p-4 flex items-center gap-3 hover:border-blue-300 hover:bg-blue-50/40 transition-colors"
            >
              <div className="h-9 w-9 rounded-md bg-slate-100 text-slate-700 group-hover:bg-blue-100 group-hover:text-blue-700 flex items-center justify-center shrink-0">
                <Icon className="h-5 w-5" />
              </div>
              <span className="text-sm font-medium text-slate-900">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </div>
  );
}