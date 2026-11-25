import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './features/auth/authContext';
import ProtectedRoute from './routes/ProtectedRoute';
import AppShell from './components/AppShell';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ComingSoonPage from './pages/ComingSoonPage';

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Public routes */}
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<ComingSoonPage title="Register department" />} />
            <Route path="/forgot-password" element={<ComingSoonPage title="Forgot password" />} />
            <Route path="/reset-password" element={<ComingSoonPage title="Reset password" />} />
            <Route path="/verify-email" element={<ComingSoonPage title="Verify email" />} />

            {/* Protected routes */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AppShell />}>
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/notifications" element={<ComingSoonPage title="Notifications" />} />
                <Route path="/change-password" element={<ComingSoonPage title="Change password" />} />

                {/* HOD sections */}
                <Route path="/setup/teachers" element={<ComingSoonPage title="Teachers" />} />
                <Route path="/setup/subjects" element={<ComingSoonPage title="Subjects" />} />
                <Route path="/setup/teacher-subjects" element={<ComingSoonPage title="Who can teach what" />} />
                <Route path="/setup/semester-plans" element={<ComingSoonPage title="Semester plans" />} />
                <Route path="/timetables" element={<ComingSoonPage title="Classes and Timetables" />} />
                <Route path="/make-timetable" element={<ComingSoonPage title="Make Timetable" />} />
                <Route path="/leave" element={<ComingSoonPage title="Leave" />} />
                <Route path="/today" element={<ComingSoonPage title="Today" />} />
                <Route path="/history" element={<ComingSoonPage title="History" />} />

                {/* Teacher sections */}
                <Route path="/all-timetables" element={<ComingSoonPage title="All timetables" />} />
                <Route path="/my-timetable" element={<ComingSoonPage title="My timetable" />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}