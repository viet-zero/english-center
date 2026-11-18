import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function DemoEmailPage({ onError }) {
  const [items, setItems] = useState([]);
  const [disabled, setDisabled] = useState(false);
  const [busy, setBusy] = useState(true);
  async function refresh() {
    setBusy(true);
    try { setItems(await api.passwordResetEmails()); setDisabled(false); }
    catch (error) { if (error.status === 404) setDisabled(true); else onError(error); }
    finally { setBusy(false); }
  }
  useEffect(() => {
    let active = true;
    api.passwordResetEmails().then(rows => { if (active) setItems(rows); })
      .catch(error => { if (active) { if (error.status === 404) setDisabled(true); else onError(error); } })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [onError]);
  return <section className="panel">
    <h1>Email khôi phục giả lập</h1>
    <p>Hộp thư dùng để kiểm thử đặt lại mật khẩu trong chế độ email demo.</p>
    <button disabled={busy} onClick={refresh}>Tải lại hộp thư demo</button>
    {disabled && <p>Chế độ email giả lập chưa được bật trên server.</p>}
    {!busy && !disabled && items.length === 0 && <p>Chưa có email khôi phục.</p>}
    {items.map(item => <article key={item.id}>
      <h2>{item.subject}</h2>
      <p>Gửi đến {item.recipient}</p>
      <p>Hết hạn lúc {new Date(item.expiresAt).toLocaleString('vi-VN')}</p>
      {item.valid ? <a href={item.resetUrl} target="_blank" rel="noreferrer">Mở liên kết đặt lại mật khẩu</a>
        : <p>Liên kết đã hết hiệu lực.</p>}
    </article>)}
  </section>;
}
