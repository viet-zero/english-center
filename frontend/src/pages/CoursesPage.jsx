import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

export default function CoursesPage({ onInvoice, onError }) {
  const [demo, setDemo] = useState(false);
  const [courses, setCourses] = useState([]);
  const [classes, setClasses] = useState([]);
  const [courseId, setCourseId] = useState('');
  const [couponCode, setCouponCode] = useState('');
  const [order, setOrder] = useState(null);
  useEffect(() => {
    if (!['PENDING', 'REVIEW'].includes(order?.status)) return;
    let active = true;
    const timer = setInterval(() => {
      api.checkoutStatus(order.orderCode).then(result => {
        if (active) setOrder(result);
      }).catch(() => {});
    }, 3000);
    return () => { active = false; clearInterval(timer); };
  }, [order?.orderCode, order?.status]);

  useEffect(() => {
    const code = sessionStorage.getItem('english-center.payment') ?? sessionStorage.getItem('english-center.payos');
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
    Promise.all([api.courses(), api.paymentMode()]).then(([items, mode]) => {
      if (active) { setCourses(items); setDemo(mode.demo); }
    })
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
    setCouponCode('');
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
      const checkout = await api.openCheckout(classId, couponCode);
      sessionStorage.setItem('english-center.payment', checkout.orderCode);
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
    {courseId && <label>Mã giảm giá (không bắt buộc)<input value={couponCode} disabled={busy} maxLength={30} onChange={event => setCouponCode(event.target.value)} /></label>}
    {order && <article aria-live="polite">
      <h3>{demo ? "Đơn thanh toán demo" : "Đơn SePay"} {order.orderCode}</h3>
      <p>Học phí gốc {money(order.tuitionAmount ?? order.amount)} · Giảm {money(order.discountAmount ?? 0)}</p>
      {order.couponCode && <p>Mã áp dụng <strong>{order.couponCode}</strong></p>}
      <p>Cần thanh toán <strong>{money(order.amount)}</strong></p>
      {order.status === 'PENDING' && <>
        <p>Đang chờ xác nhận đủ tiền. Chỗ trống được kiểm tra khi tiền về.</p>
        {order.amount === 0 ? <button disabled={busy} onClick={() => load(async () => {
          setOrder(await api.confirmFree(order.orderCode));
        })}>Xác nhận học phí 0 ₫</button> : order.qrUrl
          ? <div>
            <img src={order.qrUrl} alt={'QR chuyển khoản SePay cho đơn ' + order.paymentCode}
              style={{ width: 'min(100%, 320px)', height: 'auto' }} referrerPolicy="no-referrer" />
            <p>Nội dung chuyển khoản <strong>{order.paymentCode}</strong></p>
            <p>Tài khoản nhận {order.receivingAccount}</p>
          </div>
          : demo && order.provider === "SEPAY" && <>
            <p>Thanh toán giả lập cho đồ án.</p>
            <button disabled={busy} onClick={() => load(async () => {
              setOrder(await api.demoPayment(order.orderCode));
              if (courseId) {
                const modules = await api.modules(courseId);
                const groups = await Promise.all(modules.map(async module =>
                  (await api.classes(module.id)).map(item => ({ ...item, moduleName: module.name }))));
                setClasses(groups.flat());
              }
            })}>Thanh toán demo</button>
          </>}
      </>}
      {order.status === 'ENROLLED' && <>
        <p>Đã thanh toán đủ và đăng ký lớp thành công.</p>
        <button onClick={() => onInvoice(order.invoiceId)}>Xem hóa đơn</button>
      </>}
      {order.provider === "PAYOS" && order.status === "PENDING" && <p>Đơn cũ từ payOS. Hãy chọn lớp để tạo đơn SePay mới.</p>}
      {order.status === 'REFUNDED' && <p>Đã ghi nhận hoàn tiền demo.</p>}
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
        onClick={() => payForClass(item.id)}>{demo ? "Thanh toán" : "Tạo mã QR SePay"}</button>
    </article>)}</div>
  </section>;
}
