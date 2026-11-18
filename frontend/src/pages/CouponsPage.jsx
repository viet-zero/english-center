import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

function localTime(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function CouponsPage({ onError }) {
  const [coupons, setCoupons] = useState([]);
  const [courses, setCourses] = useState([]);
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('');
  useEffect(() => {
    let active = true;
    Promise.all([api.coupons(), api.trainingCourses()]).then(([codes, courses]) => {
      if (active) { setCoupons(codes); setCourses(courses); }
    }).catch(error => { if (active) onError(error); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [onError]);

  function submit(editing) {
    return async event => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = Object.fromEntries(new FormData(form));
      data.maxUses = Number(data.maxUses);
      setBusy(true);
      setMessage('');
      onError(null);
      try {
        if (editing) await api.updateCoupon(selected.id, data);
        else await api.createCoupon({ ...data, code: data.code.trim().toUpperCase(), amount: Number(data.amount) });
        setCoupons(await api.coupons());
        setSelected(null);
        form.reset();
        setMessage('Đã lưu mã giảm giá.');
      } catch (error) { onError(error); }
      finally { setBusy(false); }
    };
  }

  return <section className="panel">
    <h1>Mã giảm giá</h1>
    <p>Mã áp dụng cho một khóa học. Mỗi học viên dùng mỗi mã một lần. Lượt dùng chỉ được ghi nhận khi thanh toán đủ và ghi danh thành công.</p>
    {message && <p role="status">{message}</p>}
    <details><summary>Tạo mã giảm giá</summary>
      <form onSubmit={submit(false)}><fieldset disabled={busy}>
        <label>Mã giảm giá<input name="code" pattern="[A-Za-z0-9_-]{3,30}" minLength={3} maxLength={30} required /></label>
        <label>Khóa học áp dụng<select name="courseId" required>
          <option value="">Chọn khóa học</option>
          {courses.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <label>Số tiền giảm (₫)<input name="amount" type="number" min="1" max="2147483647" step="1" required /></label>
        <PeriodFields />
        <button>Tạo mã giảm giá</button>
      </fieldset></form>
    </details>
    <ul>{coupons.map(item => <li key={item.id}>
      <strong>{item.code}</strong> · {item.courseName} · Giảm {money(item.amount)}
      {' · '}Đã dùng {item.usedCount}/{item.maxUses} lượt · {item.status === 'ACTIVE' ? 'Đang bật' : 'Đã tắt'}
      {' · '}Từ {new Date(item.startsAt).toLocaleString('vi-VN')} đến {new Date(item.expiresAt).toLocaleString('vi-VN')}
      <button className="outline" disabled={busy} onClick={() => setSelected(item)}>Sửa mã {item.code}</button>
    </li>)}</ul>
    {selected && <form key={selected.id} onSubmit={submit(true)}>
      <h2>Điều chỉnh {selected.code}</h2>
      <p>{selected.courseName} · Giảm {money(selected.amount)} · Đã dùng {selected.usedCount} lượt</p>
      <fieldset disabled={busy}>
        <PeriodFields value={selected} />
        <button>Lưu điều kiện mã</button>
        <button className="outline" type="button" onClick={() => setSelected(null)}>Đóng chỉnh sửa</button>
      </fieldset>
    </form>}
  </section>;
}

function PeriodFields({ value }) {
  return <>
    <label>Bắt đầu áp dụng<input name="startsAt" type="datetime-local" required defaultValue={value?.startsAt ? localTime(new Date(value.startsAt)) : localTime(new Date())} /></label>
    <label>Hết hạn<input name="expiresAt" type="datetime-local" required defaultValue={value?.expiresAt ? localTime(new Date(value.expiresAt)) : localTime(new Date(Date.now() + 7 * 86400000))} /></label>
    <label>Tổng lượt dùng tối đa<input name="maxUses" type="number" min={Math.max(1, value?.usedCount ?? 0)} required defaultValue={value?.maxUses ?? 1} /></label>
    <label>Trạng thái mã<select name="status" defaultValue={value?.status ?? 'ACTIVE'}>
      <option value="ACTIVE">Đang bật</option><option value="INACTIVE">Đã tắt</option>
    </select></label>
  </>;
}
