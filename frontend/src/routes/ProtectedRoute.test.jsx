import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../features/auth/authContext';
import ProtectedRoute from './ProtectedRoute';

function LoginProbe() {
  const location = useLocation();
  return <div data-testid="login-location">{location.pathname + location.search}</div>;
}

describe('ProtectedRoute', () => {
  it('redirects a visitor to /login and remembers the requested page', () => {
    render(
      <AuthProvider initialUser={null}>
        <MemoryRouter initialEntries={['/make-timetable?step=2']}>
          <Routes>
            <Route path="/login" element={<LoginProbe />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/make-timetable" element={<div>Make Timetable Page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId('login-location')).toHaveTextContent(
      '/login?next=%2Fmake-timetable%3Fstep%3D2'
    );
  });

  it('shows the requested page when the user is logged in', () => {
    render(
      <AuthProvider initialUser={{ displayName: 'HOD of CS', role: 'HOD' }}>
        <MemoryRouter initialEntries={['/make-timetable']}>
          <Routes>
            <Route path="/login" element={<LoginProbe />} />
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