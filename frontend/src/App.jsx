import React, { useCallback, useEffect, useState } from 'react';
import NotificationBell from './components/NotificationBell.jsx';
import AcademicsPage from './pages/AcademicsPage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import CatalogAdmin from './pages/CatalogAdmin.jsx';
import CoursesPage from './pages/CoursesPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import ExamManagementPage from './pages/ExamManagementPage.jsx';
import RoomSchedulingPage from './pages/RoomSchedulingPage.jsx';
import TrainingPage from './pages/TrainingPage.jsx';
import TuitionPage, { PaymentForm } from './pages/TuitionPage.jsx';
import WeekPage from './pages/WeekPage.jsx';
import { api } from './services/api.js';

const menus = {
  STUDENT: ['Học viên', [['week', 'Thời khóa biểu'], ['courses', 'Đăng ký lớp'], ['study', 'Lớp của tôi'], ['tuition', 'Học phí']]],
  TEACHER: ['Giảng viên', [['week', 'Thời khóa biểu'], ['teach', 'Lớp giảng dạy']]],
  TRAINING_STAFF: ['Đào tạo', [['week', 'Thời khóa biểu'], ['catalog', 'Danh mục đào tạo'], ['rooms', 'Xếp phòng'], ['exams', 'Lịch thi']]],
  ACCOUNTANT: ['Kế toán', [['payment', 'Ghi nhận học phí']]],
  ADMIN: ['Quản trị viên', [['admin', 'Tạo tài khoản'], ['admin-catalog', 'Quản lý khóa học']]],
};
const scopes = { STUDENT: 'student', TEACHER: 'teacher', TRAINING_STAFF: 'training' };

export default function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);
  const [invoiceId, setInvoiceId] = useState('');
  const [error, setError] = useState('');
  const [selectedRole, setSelectedRole] = useState('');
  const [selectedPage, setSelectedPage] = useState('');
  const [dark, setDark] = useState(() => localStorage.getItem('ec-theme') === 'dark');

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    localStorage.setItem('ec-theme', dark ? 'dark' : 'light');
  }, [dark]);

  const onError = useCallback(failure => {
    if (failure?.status === 401) {
      setUser(null);
      setInvoiceId('');
      setSelectedRole('');
      setSelectedPage('');
    }
    setError(failure?.message ?? '');
  }, []);

  useEffect(() => {
    let active = true;
    api.me().then(session => { if (active) setUser(session); })
      .catch(failure => { if (active && failure.status !== 401) onError(failure); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [onError]);

  async function logout() {
    setLoggingOut(true);
    try {
      await api.logout();
      setUser(null);
      setInvoiceId('');
      setSelectedRole('');
      setSelectedPage('');
      setError('');
    } catch (failure) {
      onError(failure);
    } finally {
      setLoggingOut(false);
    }
  }

  const roles = user?.roles.filter(role => menus[role]) ?? [];
  const role = roles.includes(selectedRole) ? selectedRole : roles[0];
  const links = menus[role]?.[1] ?? [];
  const page = links.some(([key]) => key === selectedPage) ? selectedPage : links[0]?.[0];

  return <>
    <header><div className="header-inner">
      <div className="brand"><span className="brand-mark" aria-hidden="true">EC</span><strong>English Center</strong></div>
      <div className="session">
        <button className="secondary" onClick={() => setDark(!dark)}>{dark ? 'Chế độ sáng' : 'Chế độ tối'}</button>
        {user && <>
          {roles.includes('STUDENT') && <NotificationBell key={user.username} />}
          <span className="session-name">{user.username}</span>
          <button className="secondary" onClick={logout} disabled={loggingOut}>Đăng xuất</button>
        </>}
      </div>
    </div></header>
    <div className={user ? 'portal' : ''}>
      {user && <aside className="sidebar">
        {roles.length > 1 ? <label>Vai trò<select value={role} onChange={event => {
          setSelectedRole(event.target.value);
          setSelectedPage('');
          setError('');
        }}>
          {roles.map(value => <option key={value} value={value}>{menus[value][0]}</option>)}
        </select></label> : <strong>{menus[role]?.[0]}</strong>}
        <nav aria-label="Chức năng">{links.map(([key, label]) =>
          <button key={key} aria-current={page === key ? 'page' : undefined} onClick={() => {
            setSelectedPage(key);
            setError('');
          }}>{label}</button>)}</nav>
      </aside>}
      <main>
        {error && <p className="notice" role="alert">{error}</p>}
        {loading && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
        {!loading && !user && <LoginPage onLogin={session => { setError(''); setUser(session); }} />}
        {page === 'week' && <WeekPage key={role} scope={scopes[role]} onError={onError} />}
        {page === 'courses' && <CoursesPage onInvoice={id => { setInvoiceId(id); setSelectedPage('tuition'); }} onError={onError} />}
        {page === 'tuition' && <TuitionPage invoiceId={invoiceId} onInvoice={setInvoiceId} onError={onError} />}
        {page === 'study' && <AcademicsPage key="student" onError={onError} />}
        {page === 'teach' && <AcademicsPage key="teacher" teacher onError={onError} />}
        {page === 'payment' && <PaymentForm onError={onError} />}
        {page === 'admin-catalog' && <CatalogAdmin onError={onError} />}
        {page === 'admin' && <AdminPage onError={onError} />}
        {page === 'exams' && <ExamManagementPage onError={onError} />}
        {page === 'rooms' && <RoomSchedulingPage onError={onError} />}
        {page === 'catalog' && <TrainingPage onError={onError} />}
        {user && !role && <p>Tài khoản chưa được cấp vai trò.</p>}
      </main>
    </div>
  </>;
}
