import React, { useState } from 'react';
import { api } from '../services/api.js';
import PasswordRecoveryPage from './PasswordRecoveryPage.jsx';

export default function LoginPage({ onLogin }) {
  const [forgot, setForgot] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError('');
    try {
      onLogin(await api.login(data.get('username').trim(), data.get('password')));
    } catch (failure) {
      setError(failure.status === 401 ? 'Tên đăng nhập hoặc mật khẩu không đúng.' : failure.message);
    } finally {
      form.elements.password.value = '';
      setBusy(false);
    }
  }

  if (forgot) return <PasswordRecoveryPage onBack={() => setForgot(false)} />;
  return <section className="panel login">
    <h1>Đăng nhập</h1>
    <form onSubmit={submit}>
      <label>Tên đăng nhập<input name="username" autoComplete="username" required maxLength={100} /></label>
      <label>Mật khẩu<input name="password" type="password" autoComplete="current-password" required /></label>
      {error && <p role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
    </form>
    <button className="outline" disabled={busy} onClick={() => setForgot(true)}>Quên mật khẩu</button>
  </section>;
}
