import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });
export const money = amount => currency.format(amount);
const statuses = { UNPAID: 'Chưa thanh toán', PARTIAL: 'Đóng một phần', PAID: 'Đã thanh toán' };

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
      <div><dt>Còn thiếu</dt><dd>{money(invoice.remainingAmount)}</dd></div>
      <div><dt>Trạng thái</dt><dd>{statuses[invoice.status] ?? invoice.status}</dd></div>
    </dl></>}
  </section>;
}

export function PaymentForm({ onError }) {
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
