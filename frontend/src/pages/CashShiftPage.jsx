import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

export default function CashShiftPage({ onError }) {
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [openId, setOpenId] = useState(() => crypto.randomUUID());
  useEffect(() => {
    let active = true;
    api.cashShifts().then(value => { if (active) setData(value); }).catch(onError);
    return () => { active = false; };
  }, [onError]);

  async function run(action, message) {
    setBusy(true);
    setMessage('');
    onError(null);
    try { await action(); setData(await api.cashShifts()); setMessage(message); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  const current = data?.current;
  return <section className="panel">
    <h1>Ca thu tiền</h1>
    <p>Tiền mặt theo hệ thống = tiền đầu ca + thu tiền mặt − hoàn tiền mặt. Các khoản chuyển khoản được theo dõi riêng trong báo cáo.</p>
    <button className="outline" disabled={busy} onClick={() => run(async () => {}, 'Đã cập nhật ca thu tiền.')}>Cập nhật ca</button>
    {message && <p role="status">{message}</p>}
    {data && !current.id && <form onSubmit={event => {
      event.preventDefault();
      const form = event.currentTarget;
      const openingCash = new FormData(form).get('openingCash');
      run(async () => { await api.openCashShift({ id: openId, openingCash }); setOpenId(crypto.randomUUID()); }, 'Đã mở ca thu tiền.');
    }}><fieldset disabled={busy}>
      <label>Tiền mặt đầu ca (₫)<input name="openingCash" type="number" min="0" max="9999999999999.99" step="0.01" required /></label>
      <button>Mở ca thu tiền</button>
    </fieldset></form>}
    {current?.id && <>
      <h2>Ca đang mở</h2>
      <p>Mở lúc {new Date(current.opened_at).toLocaleString('vi-VN')}</p>
      <dl className="totals">
        <div><dt>Đầu ca</dt><dd>{money(current.opening_cash)}</dd></div>
        <div><dt>Thu tiền mặt</dt><dd>{money(current.cash_received)}</dd></div>
        <div><dt>Hoàn tiền mặt demo</dt><dd>{money(current.cash_refunded)}</dd></div>
        <div><dt>Tiền mặt theo hệ thống</dt><dd>{money(current.expected_cash)}</dd></div>
      </dl>
      <form onSubmit={event => {
        event.preventDefault();
        const fields = Object.fromEntries(new FormData(event.currentTarget));
        run(() => api.closeCashShift(current.id, fields), 'Đã chốt ca và lưu số liệu đối soát.');
      }}><fieldset disabled={busy}>
        <label>Tiền mặt thực nộp (₫)<input name="actualCash" type="number" min="0" max="9999999999999.99" step="0.01" required /></label>
        <label>Lý do chênh lệch hoặc ghi chú<textarea name="note" maxLength={1000} /></label>
        <button>Chốt ca thu tiền</button>
      </fieldset></form>
    </>}
    <h2>Lịch sử ca của tôi</h2>
    <ul>{data?.history.filter(item => item.status === 'CLOSED').map(item => <li key={item.id}>
      {new Date(item.opened_at).toLocaleString('vi-VN')} – {new Date(item.closed_at).toLocaleString('vi-VN')}
      {' · '}Đầu ca {money(item.opening_cash)} · Thu {money(item.cash_received)} · Hoàn {money(item.cash_refunded)}
      {' · '}Theo hệ thống {money(item.expected_cash)} · Thực nộp {money(item.actual_cash)}
      {' · '}Chênh lệch {money(item.difference)} · {item.close_note}
    </li>)}</ul>
  </section>;
}
