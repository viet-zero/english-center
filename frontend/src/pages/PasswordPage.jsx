import React, { useState } from 'react';
import { api } from '../services/api.js';
import { passwordError } from '../services/passwordRules.js';

export default function PasswordPage({ onChanged, onError }) {
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));
    const invalid = passwordError(data.newPassword, data.confirmPassword);
    if (invalid) { onError(new Error(invalid)); return; }
    setBusy(true);
    onError(null);
    try { await api.changePassword(data); onChanged(); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }
  return <section className="panel">
    <h1>Đổi mật khẩu</h1>
    <p>Mật khẩu mới cần ít nhất 8 ký tự, có chữ hoa, chữ thường và chữ số. Đổi thành công sẽ yêu cầu đăng nhập lại trên các thiết bị.</p>
    <form onSubmit={submit}><fieldset disabled={busy}>
      <label>Mật khẩu hiện tại<input name="currentPassword" type="password" required maxLength={72} autoComplete="current-password" /></label>
      <label>Mật khẩu mới<input name="newPassword" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
      <label>Xác nhận mật khẩu mới<input name="confirmPassword" type="password" required maxLength={72} autoComplete="new-password" /></label>
      <button>Đổi mật khẩu</button>
    </fieldset></form>
  </section>;
}
