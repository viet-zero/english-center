import React, { useState } from 'react';
import { api } from '../services/api.js';
import { passwordError } from '../services/passwordRules.js';

export default function PasswordRecoveryPage({ token, onBack }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [done, setDone] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));
    if (token) {
      const invalid = passwordError(data.newPassword, data.confirmPassword);
      if (invalid) { setError(invalid); return; }
    }
    setBusy(true);
    setError('');
    setMessage('');
    try {
      if (token) {
        await api.resetPassword({ token, ...data });
        setDone(true);
        setMessage('Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.');
      } else {
        const result = await api.forgotPassword(data.email.trim());
        setMessage(result.message);
      }
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <section className="panel login">
    <h1>{token ? 'Đặt lại mật khẩu' : 'Quên mật khẩu'}</h1>
    <p>{token
      ? 'Liên kết có hiệu lực 5 phút và chỉ dùng một lần. Yêu cầu liên kết mới sẽ làm liên kết cũ hết hiệu lực.'
      : 'Nhập email đã được đăng ký trong hồ sơ tài khoản.'}</p>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {!done && <form onSubmit={submit}><fieldset disabled={busy}>
      {token ? <>
        <label>Mật khẩu mới<input name="newPassword" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <label>Xác nhận mật khẩu mới<input name="confirmPassword" type="password" required maxLength={72} autoComplete="new-password" /></label>
      </> : <label>Email khôi phục<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>}
      <button>{busy ? 'Đang xử lý…' : token ? 'Lưu mật khẩu mới' : 'Gửi liên kết khôi phục'}</button>
    </fieldset></form>}
    <button className="outline" disabled={busy} onClick={onBack}>Quay lại</button>
  </section>;
}
