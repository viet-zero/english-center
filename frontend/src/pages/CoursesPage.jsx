import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

export default function CoursesPage({ onInvoice, onError }) {
  const [courses, setCourses] = useState([]);
  const [classes, setClasses] = useState([]);
  const [courseId, setCourseId] = useState('');
  const [order, setOrder] = useState(null);
  useEffect(() => {
    if (order?.status !== 'PENDING') return;
    let active = true;
    const timer = setInterval(() => {
      api.checkoutStatus(order.orderCode).then(result => {
        if (active) setOrder(result);
      }).catch(() => {});
    }, 3000);
    return () => { active = false; clearInterval(timer); };
  }, [order?.orderCode, order?.status]);

  useEffect(() => {
    const code = sessionStorage.getItem('english-center.payos');
    if (!code) return;
    let active = true;
    api.checkoutStatus(code).then(result => {
      if (active) setOrder(current => current ?? result);
    }).catch(() => {});
    return () => { active = false; };
  }, []);
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    api.courses().then(items => { if (active) setCourses(items); })
      .catch(error => { if (active) onError(error); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [onError]);

  async function load(loader) {
    setBusy(true);
    onError(null);
    try {
      await loader();
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  function chooseCourse(event) {
    const value = event.target.value;
    setCourseId(value);
    setClasses([]);
    if (value) load(async () => {
      const modules = await api.modules(value);
      const groups = await Promise.all(modules.map(async module =>
        (await api.classes(module.id)).map(item => ({ ...item, moduleName: module.name }))));
      setClasses(groups.flat());
    });
  }

  function payForClass(classId) {
    load(async () => {
      const checkout = await api.openCheckout(classId);
      sessionStorage.setItem('english-center.payos', checkout.orderCode);
      setOrder(checkout);
    });
  }

  return <section className="panel">
    <h2>Chọn lớp học</h2>
    <div className="fields">
      <label>Khóa học<select value={courseId} onChange={chooseCourse} disabled={busy}>
        <option value="">Chọn khóa học</option>
        {courses.map(course => <option key={course.id} value={course.id}>{course.name}</option>)}
      </select></label>
    </div>
    {order && <article aria-live="polite">
      <h3>Đơn payOS {order.orderCode}</h3>
      <p>Học phí {money(order.amount)}</p>
      {order.status === 'PENDING' && <>
        <p>Đang chờ xác nhận đủ tiền. Chỗ trống được kiểm tra khi tiền về.</p>
        {order.checkoutUrl
          ? <a href={order.checkoutUrl} target="_blank" rel="noreferrer">Mở mã QR payOS</a>
          : <p>Đơn thanh toán demo đang chờ webhook giả lập.</p>}
      </>}
      {order.status === 'ENROLLED' && <>
        <p>Đã thanh toán đủ và đăng ký lớp thành công.</p>
        <button onClick={() => onInvoice(order.invoiceId)}>Xem hóa đơn</button>
      </>}
      {order.status === 'REVIEW' && <p>Kế toán đang đối soát khoản chuyển này. Lớp chưa được ghi danh.</p>}
    </article>}
    {busy && <p role="status">Đang xử lý…</p>}
    {!busy && courseId && classes.length === 0 && <p>Chưa có lớp mở đăng ký.</p>}
    <div className="class-grid">{classes.map(item => <article key={item.id}>
      <h3>{item.classCode}</h3>
      <p>Học phần {item.moduleName}</p>
      <p>{item.teacherName} · {item.startDate} đến {item.endDate}</p>
      <p><strong>{money(item.tuition)}</strong> · Còn {item.remainingSeats} chỗ</p>
      {item.lessons.length > 0 ? <details><summary>Lịch học</summary><ul>
        {item.lessons.map((lesson, index) => <li key={index}>
          {lesson.date}, {lesson.startTime.slice(0, 5)}–{lesson.endTime.slice(0, 5)} · {lesson.roomName}
        </li>)}
      </ul></details> : <p>Chưa có lịch học.</p>}
      <button disabled={busy || item.remainingSeats <= 0}
        onClick={() => payForClass(item.id)}>Tạo mã QR payOS</button>
    </article>)}</div>
  </section>;
}
