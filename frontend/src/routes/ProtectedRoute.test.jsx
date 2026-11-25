import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../features/auth/authContext';
import ProtectedRoute from './ProtectedRoute';

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

describe('ProtectedRoute', () => {
  it('redirects a visitor to /login and remembers the requested page', () => {
    render(
      <AuthProvider initialUser={null}>
        <MemoryRouter initialEntries={['/make-timetable?step=2']}>
          <Routes>
            <Route path="/login" element={<LocationProbe />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/make-timetable" element={<div>Make Timetable Page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/login?next=%2Fmake-timetable%3Fstep%3D2'
    );
  });

  it('redirects a user with mustChangePassword to /change-password', () => {
    render(
      <AuthProvider
        initialUser={{ displayName: 'Prof. Rao', role: 'TEACHER', mustChangePassword: true }}
      >
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<div>Dashboard Page</div>} />
              <Route path="/change-password" element={<div>Change Password Page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText('Change Password Page')).toBeInTheDocument();
  });

  it('shows the requested page when the user is logged in and password is up to date', () => {
    render(
      <AuthProvider
        initialUser={{ displayName: 'HOD of CS', role: 'HOD', mustChangePassword: false }}
      >
        <MemoryRouter initialEntries={['/make-timetable']}>
          <Routes>
            <Route path="/login" element={<LocationProbe />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/make-timetable" element={<div>Make Timetable Page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText('Make Timetable Page')).toBeInTheDocument();
  });
});