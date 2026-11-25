import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, ChevronDown, KeyRound, LogOut, Menu, User, X } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../features/auth/authContext';
import { MENU_BY_ROLE } from './menuItems';

export default function AppShell() {
  const { user, isLoggedIn, logout } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const menuItems = MENU_BY_ROLE[user?.role] || [];

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => {
      const res = await api.get('/notifications/unread-count');
      return res.data;
    },
    enabled: isLoggedIn && !user?.mustChangePassword,
    refetchInterval: 60000,
  });

  const unreadCount = unreadData?.count || 0;

  async function handleLogout() {
    setProfileOpen(false);
    await logout();
    queryClient.clear();
    navigate('/login', { replace: true });
  }

  function renderNavLinks(onClickItem) {
    return menuItems.map((item) => {
      if (item.children) {
        return (
          <div key={item.label} className="pt-3 first:pt-0">
            <div className="px-3 pb-1.5 text-xs font-semibold uppercase tracking-wider text-slate-400">
              {item.label}
            </div>
            <div className="space-y-0.5">
              {item.children.map((child) => {
                const Icon = child.icon;
                return (
                  <NavLink
                    key={child.to}
                    to={child.to}
                    onClick={onClickItem}
                    className={({ isActive }) =>
                      `flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span>{child.label}</span>
                  </NavLink>
                );
              })}
            </div>
          </div>
        );
      }

      const Icon = item.icon;
      return (
        <NavLink
          key={item.to}
          to={item.to}
          onClick={onClickItem}
          className={({ isActive }) =>
            `flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
              isActive
                ? 'bg-blue-50 text-blue-700'
                : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900'
            }`
          }
        >
          <Icon className="h-4 w-4 shrink-0" />
          <span>{item.label}</span>
        </NavLink>
      );
    });
  }

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:w-64 lg:flex-col lg:fixed lg:inset-y-0 border-r border-slate-200 bg-white">
        <div className="h-14 flex items-center px-5 border-b border-slate-200">
          <Link to="/dashboard" className="text-lg font-semibold tracking-tight text-slate-900">
            Scheduli<span className="text-blue-700">X</span>
          </Link>
        </div>
        <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
          {renderNavLinks(undefined)}
        </nav>
      </aside>

      {/* Mobile sidebar drawer */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden flex">
          <div
            className="fixed inset-0 bg-slate-900/40"
            onClick={() => setSidebarOpen(false)}
          />
          <aside className="relative z-50 w-64 max-w-xs bg-white flex flex-col border-r border-slate-200">
            <div className="h-14 flex items-center justify-between px-5 border-b border-slate-200">
              <span className="text-lg font-semibold tracking-tight text-slate-900">
                Scheduli<span className="text-blue-700">X</span>
              </span>
              <button
                type="button"
                onClick={() => setSidebarOpen(false)}
                className="p-1.5 rounded-md text-slate-500 hover:bg-slate-100"
                aria-label="Close menu"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
              {renderNavLinks(() => setSidebarOpen(false))}
            </nav>
          </aside>
        </div>
      )}

      {/* Main area */}
      <div className="flex-1 flex flex-col lg:pl-64 min-w-0">
        <header className="sticky top-0 z-20 h-14 bg-white border-b border-slate-200 px-4 sm:px-6 flex items-center justify-between">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden p-2 -ml-2 rounded-md text-slate-600 hover:bg-slate-100"
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="hidden lg:block text-sm text-slate-500">
            {user?.role === 'HOD' ? 'Department administration' : 'Teacher workspace'}
          </div>

          <div className="flex items-center gap-3">
            <Link
              to="/notifications"
              className="relative p-2 rounded-md text-slate-600 hover:bg-slate-100 hover:text-slate-900"
              aria-label="Notifications"
            >
              <Bell className="h-5 w-5" />
              {unreadCount > 0 && (
                <span className="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-blue-700 text-white text-[11px] font-medium flex items-center justify-center">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </Link>

            <div className="relative">
              <button
                type="button"
                onClick={() => setProfileOpen((open) => !open)}
                className="flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <User className="h-4 w-4 text-slate-500" />
                <span className="max-w-40 truncate">{user?.displayName}</span>
                <ChevronDown className="h-4 w-4 text-slate-400" />
              </button>

              {profileOpen && (
                <div className="absolute right-0 mt-1.5 w-48 rounded-md border border-slate-200 bg-white py-1 shadow-sm">
                  <Link
                    to="/change-password"
                    onClick={() => setProfileOpen(false)}
                    className="flex items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
                  >
                    <KeyRound className="h-4 w-4 text-slate-500" />
                    <span>Change password</span>
                  </Link>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 text-left"
                  >
                    <LogOut className="h-4 w-4 text-slate-500" />
                    <span>Log out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6 max-w-6xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}