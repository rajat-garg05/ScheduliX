import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './features/auth/authContext';
import ProtectedRoute from './routes/ProtectedRoute';
import AppShell from './components/AppShell';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import CheckEmailPage from './pages/CheckEmailPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import NotificationsPage from './pages/NotificationsPage';
import HistoryPage from './pages/HistoryPage';
import TeachersPage from './pages/TeachersPage';
import SubjectsPage from './pages/SubjectsPage';
import TeacherSubjectsPage from './pages/TeacherSubjectsPage';
import SemesterPlansPage from './pages/SemesterPlansPage';
import MakeTimetablePage from './pages/MakeTimetablePage';
import ClassesTimetablesPage from './pages/ClassesTimetablesPage';
import LeavePage from './pages/LeavePage';
import TodayPage from './pages/TodayPage';
import DashboardPage from './pages/DashboardPage';
import ComingSoonPage from './pages/ComingSoonPage';

const queryClient = new QueryClient();

function ChangePasswordRoute() {
  const { user } = useAuth();
  if (user?.mustChangePassword) {
    return <ChangePasswordPage />;
  }
  return <AppShell />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Public routes */}
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/check-email" element={<CheckEmailPage />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />

            {/* Protected routes */}
            <Route element={<ProtectedRoute />}>
              <Route element={<ChangePasswordRoute />}>
                <Route path="/change-password" element={<ChangePasswordPage />} />
              </Route>

              <Route element={<AppShell />}>
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/history" element={<HistoryPage />} />

                {/* HOD sections */}
                <Route path="/setup/teachers" element={<TeachersPage />} />
                <Route path="/setup/subjects" element={<SubjectsPage />} />
                <Route path="/setup/teacher-subjects" element={<TeacherSubjectsPage />} />
                <Route path="/setup/semester-plans" element={<SemesterPlansPage />} />
                <Route path="/timetables" element={<ClassesTimetablesPage />} />
                <Route path="/make-timetable" element={<MakeTimetablePage />} />
                <Route path="/leave" element={<LeavePage />} />
                <Route path="/today" element={<TodayPage />} />

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