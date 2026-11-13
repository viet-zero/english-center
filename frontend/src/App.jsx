import React, { useCallback, useEffect, useState } from 'react';
import AcademicsPage from './pages/AcademicsPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import CoursesPage from './pages/CoursesPage.jsx';
import TuitionPage, { PaymentForm } from './pages/TuitionPage.jsx';
import { api } from './services/api.js';

export default function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);
  const [invoiceId, setInvoiceId] = useState('');
  const [error, setError] = useState('');

  const onError = useCallback(failure => {
    if (failure?.status === 401) {
      setUser(null);
      setInvoiceId('');
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
      setError('');
    } catch (failure) {
      onError(failure);
    } finally {
      setLoggingOut(false);
    }
  }

  const student = user?.roles.includes('STUDENT');
  const teacher = user?.roles.includes('TEACHER');
  const accountant = user?.roles.includes('ACCOUNTANT');
  return <>
    <header><div className="header-inner">
      <strong>English Center</strong>
      {user && <div className="session"><span>{user.username}</span>
        <button className="secondary" onClick={logout} disabled={loggingOut}>Đăng xuất</button>
      </div>}
    </div></header>
    <main>
      {error && <p className="notice" role="alert">{error}</p>}
      {loading && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
      {!loading && !user && <LoginPage onLogin={session => { setError(''); setUser(session); }} />}
      {student && <>
        <CoursesPage onInvoice={setInvoiceId} onError={onError} />
        <TuitionPage invoiceId={invoiceId} onInvoice={setInvoiceId} onError={onError} />
      </>}
      {student && <AcademicsPage key="student" onError={onError} />}
      {teacher && <AcademicsPage key="teacher" teacher onError={onError} />}
      {accountant && <PaymentForm onError={onError} />}
      {user && !student && !accountant && !teacher && <p>Vai trò của bạn chưa có màn hình trong cổng này.</p>}
    </main>
  </>;
}
