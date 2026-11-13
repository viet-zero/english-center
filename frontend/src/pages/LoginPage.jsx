import React, { useState } from 'react';
import { api } from '../services/api.js';

export default function LoginPage({ onLogin }) {
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
      setError(failure.message);
    } finally {
      form.elements.password.value = '';
      setBusy(false);
    }
  }

  return <section className="panel login">
    <h1>Đăng nhập</h1>
    <form onSubmit={submit}>
      <label>Tên đăng nhập<input name="username" autoComplete="username" required maxLength={100} /></label>
      <label>Mật khẩu<input name="password" type="password" autoComplete="current-password" required /></label>
      {error && <p role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
    </form>
  </section>;
}
