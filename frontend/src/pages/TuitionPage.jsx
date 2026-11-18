import React, { useEffect, useState } from 'react';
import StatusBadge from '../components/StatusBadge.jsx';
import { api } from '../services/api.js';

const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });
export const money = amount => currency.format(amount);


export default function TuitionPage({ invoiceId, onInvoice, onError }) {
  const [input, setInput] = useState(invoiceId);
  const [invoice, setInvoice] = useState(null);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    setInput(invoiceId);
    setInvoice(null);
    if (!invoiceId) return;
    let active = true;
    setBusy(true);
    api.invoice(invoiceId).then(data => { if (active) { setInvoice(data); setInput(data.invoiceCode ?? invoiceId); } })
      .catch(error => { if (active) onError(error); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [invoiceId, onError, reload]);

  function lookup(event) {
    event.preventDefault();
    onError(null);
    const value = input.trim();
    if (!value) return;
    if (value === invoiceId) setReload(value => value + 1);
    else onInvoice(value);
  }

  return <section className="panel">
    <h2>Tra cứu học phí</h2>
    <form className="inline" onSubmit={lookup}>
      <label>Mã hóa đơn<input required maxLength={36} value={input} onChange={event => setInput(event.target.value)} /></label>
      <button disabled={busy}>{busy ? 'Đang tải…' : 'Tra cứu'}</button>
    </form>
    {invoice && <><p>Mã hóa đơn: <strong>{invoice.invoiceCode}</strong></p><dl className="totals">
      <div><dt>Phải thu</dt><dd>{money(invoice.totalDue)}</dd></div>
      <div><dt>Đã thu</dt><dd>{money(invoice.totalPaid)}</dd></div>
      <div><dt>Đã hoàn tiền demo</dt><dd>{money(invoice.refundedAmount ?? 0)}</dd></div>
      <div><dt>Giá trị bảo lưu</dt><dd>{money(invoice.reservedAmount ?? 0)}</dd></div>
      <div><dt>Còn thiếu</dt><dd>{money(invoice.remainingAmount)}</dd></div>
      <div><dt>Trạng thái</dt><dd><StatusBadge status={invoice.status} /></dd></div>
    </dl></>}
  </section>;
}

export function PaymentForm({ onError }) {
  const [reviews, setReviews] = useState([]);
  const [reviewLoading, setReviewLoading] = useState(true);

  useEffect(() => {
    let active = true;
    function refresh() {
      api.paymentReviews().then(items => { if (active) setReviews(items); })
        .catch(error => { if (active) onError(error); })
        .finally(() => { if (active) setReviewLoading(false); });
    }
    refresh();
    const timer = setInterval(refresh, 10000);
    return () => { active = false; clearInterval(timer); };
  }, [onError]);

  const [busy, setBusy] = useState(false);
  const [receipt, setReceipt] = useState(null);

  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setReceipt(null);
    onError(null);
    try {
      const payment = await api.pay(data.get('invoiceId').trim(), {
        amount: data.get('amount'),
        method: data.get('method'),
        referenceCode: data.get('referenceCode').trim(),
      });
      setReceipt(payment);
      form.reset();
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  return <section className="panel">
    <h2>Ghi nhận học phí</h2>
    <h3>Đối soát và lịch sử giao dịch payOS</h3>
    {reviewLoading && <p>Đang tải giao dịch…</p>}
    {!reviewLoading && reviews.length === 0 && <p>Chưa có giao dịch cần đối soát.</p>}
    <ul>{reviews.map(item => <li key={item.reference}>
      Đơn {item.orderCode} · Lớp {item.classCode} · Học viên {item.studentId} ·
      Nhận {money(item.receivedAmount)} / {money(item.expectedAmount)} ·
      Mã giao dịch {item.reference} · {item.reason}
      <ReviewActions item={item} onError={onError} onResolved={async () => setReviews(await api.paymentReviews())} />
    </li>)}</ul>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label>Mã hóa đơn<input name="invoiceId" required maxLength={36} /></label>
        <label>Số tiền (₫)<input name="amount" type="number" required min="0.01" max="9999999999999.99" step="0.01" /></label>
        <label>Phương thức<select name="method">
          <option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option>
        </select></label>
        <label>Mã tham chiếu<input name="referenceCode" required maxLength={100} /></label>
        <button>{busy ? 'Đang ghi nhận…' : 'Ghi nhận khoản thu'}</button>
      </fieldset>
    </form>
    {receipt && <p role="status">Đã ghi nhận {money(receipt.amount)} · Mã tham chiếu {receipt.referenceCode}</p>}
  </section>;
}

function ReviewActions({ item, onError, onResolved }) {
  const [classes, setClasses] = useState([]);
  const [busy, setBusy] = useState(false);
  const [action, setAction] = useState('TRANSFER');

  useEffect(() => {
    if (item.resolution) return;
    let active = true;
    api.reviewClasses(item.reference).then(items => { if (active) setClasses(items); })
      .catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [item.reference, item.resolution, onError]);

  if (item.resolution) return <p role="status">
    {item.resolution === 'TRANSFER' ? 'Đã chuyển lớp' : 'Đã ghi nhận hoàn tiền demo'}
    {' · '}{item.resolutionNote} · Người xử lý {item.resolvedBy}
    {item.resolvedAt && <> · {new Date(item.resolvedAt).toLocaleString('vi-VN')}</>}
  </p>;

  async function resolve(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    onError(null);
    try {
      await api.resolveReview(item.reference, {
        action, targetClassId: data.get('targetClassId'), note: data.get('note'),
      });
      await onResolved();
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  return <form onSubmit={resolve}>
    <fieldset disabled={busy}>
      <label>Xử lý giao dịch<select value={action} onChange={event => setAction(event.target.value)}>
        <option value="TRANSFER">Chuyển sang lớp còn chỗ</option>
        <option value="REFUND_DEMO">Ghi nhận hoàn tiền demo</option>
      </select></label>
      {action === 'TRANSFER' && <label>Lớp nhận cùng học phần<select name="targetClassId" required>
        <option value="">Chọn lớp nhận</option>
        {classes.map(item => <option key={item.id} value={item.id}>{item.classCode} · Còn {item.remainingSeats} chỗ</option>)}
      </select></label>}
      <label>Ghi chú xử lý<input name="note" required maxLength={255} /></label>
      <button disabled={action === 'TRANSFER' && classes.length === 0}>Xác nhận xử lý</button>
    </fieldset>
  </form>;
}