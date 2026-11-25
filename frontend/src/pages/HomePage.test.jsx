import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../features/auth/authContext';
import HomePage from './HomePage';

describe('HomePage', () => {
  it('shows Login, Register, and the Sample timetable to a visitor', () => {
    render(
      <AuthProvider initialUser={null}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText('Make your department timetable in minutes')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Login' }).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Register' })).toBeInTheDocument();
    expect(screen.getByText('Sample')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Go to my dashboard/i })).not.toBeInTheDocument();
  });

  it('shows Go to my dashboard instead of Login and Register when logged in', () => {
    render(
      <AuthProvider initialUser={{ displayName: 'HOD of CS', role: 'HOD' }}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getAllByRole('link', { name: /Go to my dashboard/i }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: 'Register' })).not.toBeInTheDocument();
  });
});