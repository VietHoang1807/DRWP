import { lazy } from 'react';
import { createBrowserRouter, Outlet } from 'react-router';
import { AppShell } from './components/layout/AppShell';
import { RequireAuth, RequirePlatformAdmin, RequireTenant } from './auth/guards';

// Tách bundle theo route: trang giới thiệu (kèm engine scroll-craft) và từng màn hình chỉ tải khi cần
const Landing = lazy(() => import('./screens/landing/Landing'));
const Login = lazy(() => import('./screens/auth/Login'));
const OidcCallback = lazy(() => import('./screens/auth/OidcCallback'));
const SelectTenant = lazy(() => import('./screens/auth/SelectTenant'));
const Dashboard = lazy(() => import('./screens/Dashboard'));
const RecordList = lazy(() => import('./screens/records/RecordList'));
const RecordCreate = lazy(() => import('./screens/records/RecordCreate'));
const RecordDetail = lazy(() => import('./screens/records/RecordDetail'));
const FormList = lazy(() => import('./screens/forms/FormList'));
const FormBuilder = lazy(() => import('./screens/forms/FormBuilder'));
const WorkflowList = lazy(() => import('./screens/workflows/WorkflowList'));
const WorkflowDesigner = lazy(() => import('./screens/workflows/WorkflowDesigner'));
const TaskInbox = lazy(() => import('./screens/tasks/TaskInbox'));
const Notifications = lazy(() => import('./screens/Notifications'));
const AuditLogs = lazy(() => import('./screens/AuditLogs'));
const Roles = lazy(() => import('./screens/identity/Roles'));
const Members = lazy(() => import('./screens/identity/Members'));
const Tenants = lazy(() => import('./screens/platform/Tenants'));
const ComingSoon = lazy(() => import('./screens/ComingSoon'));
const NotFound = lazy(() => import('./screens/NotFound'));

export const router = createBrowserRouter([
  { path: '/', element: <Landing /> },
  { path: '/login', element: <Login /> },
  { path: '/auth/callback', element: <OidcCallback /> },
  { path: '/select-tenant', element: <RequireAuth><SelectTenant /></RequireAuth> },
  {
    path: '/app',
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { path: 'platform/tenants', element: <RequirePlatformAdmin><Tenants /></RequirePlatformAdmin> },
      {
        element: <RequireTenant><Outlet /></RequireTenant>,
        children: [
          { index: true, element: <Dashboard /> },
          { path: 'records', element: <RecordList /> },
          { path: 'records/new', element: <RecordCreate /> },
          { path: 'records/:id', element: <RecordDetail /> },
          { path: 'forms', element: <FormList /> },
          { path: 'forms/:code', element: <FormBuilder /> },
          { path: 'workflows', element: <WorkflowList /> },
          { path: 'workflows/:code', element: <WorkflowDesigner /> },
          { path: 'tasks', element: <TaskInbox /> },
          { path: 'notifications', element: <Notifications /> },
          { path: 'audit', element: <AuditLogs /> },
          { path: 'roles', element: <Roles /> },
          { path: 'members', element: <Members /> },
          { path: 'batch-jobs', element: <ComingSoon kind="batch" /> },
          { path: 'auth-sources', element: <ComingSoon kind="auth" /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFound /> },
]);
