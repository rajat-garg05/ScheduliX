import {
  LayoutDashboard,
  Users,
  BookOpen,
  Link2,
  ClipboardList,
  FolderTree,
  CalendarPlus,
  CalendarOff,
  CalendarClock,
  History,
  CalendarDays,
  UserCheck,
} from 'lucide-react';

export const MENU_BY_ROLE = {
  HOD: [
    { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
    {
      label: 'Setup',
      children: [
        { label: 'Teachers', to: '/setup/teachers', icon: Users },
        { label: 'Subjects', to: '/setup/subjects', icon: BookOpen },
        { label: 'Who can teach what', to: '/setup/teacher-subjects', icon: Link2 },
        { label: 'Semester plans', to: '/setup/semester-plans', icon: ClipboardList },
      ],
    },
    { label: 'Classes and Timetables', to: '/timetables', icon: FolderTree },
    { label: 'Make Timetable', to: '/make-timetable', icon: CalendarPlus },
    { label: 'Leave', to: '/leave', icon: CalendarOff },
    { label: 'Today', to: '/today', icon: CalendarClock },
    { label: 'History', to: '/history', icon: History },
  ],
  TEACHER: [
    { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
    { label: 'All timetables', to: '/all-timetables', icon: CalendarDays },
    { label: 'Today', to: '/today', icon: CalendarClock },
    { label: 'My timetable', to: '/my-timetable', icon: UserCheck },
    { label: 'Leave', to: '/leave', icon: CalendarOff },
  ],
};

export function flattenMenuItems(role) {
  const items = MENU_BY_ROLE[role] || [];
  const flat = [];
  for (const item of items) {
    if (item.children) {
      flat.push(...item.children);
    } else {
      flat.push(item);
    }
  }
  return flat;
}