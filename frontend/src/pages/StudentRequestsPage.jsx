import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

const statuses = { PENDING: 'Chờ đào tạo duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối', REFUNDED: 'Đã hoàn tiền demo' };

export default function StudentRequestsPage({ scope, onError }) {
  const [items, setItems] = useState([]);
  const [enrollments, setEnrollments] = useState([]);
  const [demo, setDemo] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function refresh() {
    if (scope === 'student') {
      const [rows, registrations] = await Promise.all([api.studentRequests(), api.requestEnrollments()]);
      setItems(rows);
      setEnrollments(registrations);
    } else if (scope === 'training') {
      setItems(await api.trainingRequests());
    } else {
      const data = await api.studentRefunds();
      setItems(data.items);
      setDemo(data.demo);
    }
  }

  useEffect(() => {
    let active = true;
    async function initial() {
      try {
        if (scope === 'student') {
          const [rows, registrations] = await Promise.all([api.studentRequests(), api.requestEnrollments()]);
          if (active) { setItems(rows); setEnrollments(registrations); }
        } else if (scope === 'training') {
          const rows = await api.trainingRequests();
          if (active) setItems(rows);
        } else {
          const data = await api.studentRefunds();
          if (active) { setItems(data.items); setDemo(data.demo); }
        }
      } catch (error) { if (active) onError(error); }
    }
    initial();
    return () => { active = false; };
  }, [scope, onError]);

  async function run(action, confirmation) {
    setBusy(true);
    setMessage('');
    onError(null);
    try { await action(); await refresh(); setMessage(confirmation); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    run(async () => { await api.submitStudentRequest(data); form.reset(); }, 'Đã gửi yêu cầu chờ đào tạo duyệt.');
  }

  return <section className="panel">
    <h1>{scope === 'accountant' ? 'Hoàn tiền yêu cầu hủy' : 'Yêu cầu hủy và bảo lưu'}</h1>
    <p>Chỉ tiếp nhận khi đã thanh toán đủ và số buổi đã kết thúc không quá 50% lịch học.
      Khoản hoàn hoặc giá trị bảo lưu tính theo số tiền thực đóng × số buổi còn lại / tổng số buổi, làm tròn xuống đồng.</p>
    <button className="outline" disabled={busy} onClick={() => run(refresh, 'Đã tải lại danh sách.')}>Tải lại yêu cầu</button>
    {message && <p role="status">{message}</p>}
    {scope === 'student' && <form onSubmit={submit}><fieldset disabled={busy}>
      <label>Lớp xin hủy hoặc bảo lưu<select name="enrollmentId" required>
        <option value="">Chọn lớp đã thanh toán</option>
        {enrollments.map(item => <option key={item.id} value={item.id}
          disabled={!item.totalLessons || item.elapsedLessons * 2 > item.totalLessons || items.some(row => row.enrollmentId === item.id && row.status === 'PENDING')}>
          {item.classCode} · {item.moduleName} · Đã qua {item.elapsedLessons}/{item.totalLessons} buổi
        </option>)}
      </select></label>
      <label>Loại yêu cầu<select name="kind"><option value="CANCEL">Hủy lớp và hoàn tiền</option><option value="HOLD">Bảo lưu học phí</option></select></label>
      <label>Lý do yêu cầu<textarea name="reason" required maxLength={1000} /></label>
      <button>Gửi yêu cầu</button>
    </fieldset></form>}
    {items.length === 0 && <p>Chưa có yêu cầu.</p>}
    {items.map(item => <article key={item.id}>
      <h2>{item.classCode} · {item.kind === 'CANCEL' ? 'Hủy lớp' : 'Bảo lưu'}</h2>
      <p>{item.fullName} · {item.invoiceCode} · {statuses[item.status]}</p>
      <p>Lý do {item.reason}</p>
      {item.remainingValue != null && <p>{item.kind === 'HOLD' ? 'Giá trị học phí bảo lưu' : 'Số tiền được hoàn'} {money(item.remainingValue)}
        {' · '}Đã qua {item.elapsedLessons}/{item.totalLessons} buổi · Thực đóng {money(item.paidAmount)}</p>}
      {item.reviewNote && <p>Đào tạo xử lý {item.reviewedBy} · {item.reviewNote}</p>}
      {item.refundNote && <p>Hoàn tiền demo · {item.refundNote}</p>}
      {scope === 'training' && item.status === 'PENDING' && <form onSubmit={event => {
        event.preventDefault();
        const fields = new FormData(event.currentTarget);
        run(() => api.reviewStudentRequest(item.id, {
          approved: fields.get('approved') === 'true', note: fields.get('note'),
        }), 'Đã xử lý yêu cầu.');
      }}><fieldset disabled={busy}>
        <label>Quyết định<select name="approved"><option value="true">Duyệt yêu cầu</option><option value="false">Từ chối</option></select></label>
        <label>Ghi chú duyệt<textarea name="note" required maxLength={1000} /></label>
        <button>Xác nhận duyệt</button>
      </fieldset></form>}
      {scope === 'accountant' && item.status === 'APPROVED' && <>
        {!demo && <p>Cần bật chế độ demo để ghi nhận hoàn tiền giả lập.</p>}
        <form onSubmit={event => {
          event.preventDefault();
          const fields = new FormData(event.currentTarget);
          run(() => api.refundStudentRequest(item.id, { note: fields.get('note') }), 'Đã ghi nhận hoàn tiền demo.');
        }}><fieldset disabled={busy || !demo}>
          <label>Chứng từ hoặc ghi chú hoàn tiền demo<textarea name="note" required maxLength={1000} /></label>
          <button>Xác nhận hoàn tiền demo</button>
        </fieldset></form>
      </>}
    </article>)}
  </section>;
}
