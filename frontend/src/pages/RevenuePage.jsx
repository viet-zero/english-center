import React, { useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

function localDay() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

export default function RevenuePage({ onError }) {
  const [report, setReport] = useState(null);
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setBusy(true);
    onError(null);
    try { setReport(await api.revenue(fields.get('from'), fields.get('to'))); }
    catch (error) { setReport(null); onError(error); }
    finally { setBusy(false); }
  }
  return <section className="panel">
    <h1>Báo cáo doanh thu và hoàn tiền</h1>
    <p>Thu ròng theo dòng tiền = tổng tiền nhận − khoản hoàn trong kỳ. Báo cáo tính theo ngày thu/hoàn,
      bao gồm thanh toán trực tuyến và cả hai nguồn hoàn tiền demo. Tiền đầu ca và chênh lệch quỹ được theo dõi trong ca thu tiền.</p>
    <form onSubmit={submit}><fieldset disabled={busy}>
      <label>Từ ngày<input name="from" type="date" defaultValue={localDay()} required /></label>
      <label>Đến ngày<input name="to" type="date" defaultValue={localDay()} required /></label>
      <button>Xem báo cáo doanh thu</button>
    </fieldset></form>
    {report && <>
      <dl className="totals">
        <div><dt>Tổng tiền nhận</dt><dd>{money(report.summary.received)}</dd></div>
        <div><dt>Tổng hoàn tiền demo</dt><dd>{money(report.summary.refunded)}</dd></div>
        <div><dt>Thu ròng sau hoàn</dt><dd>{money(report.summary.netReceived)}</dd></div>
        <div><dt>Thu tiền mặt</dt><dd>{money(report.summary.cashReceived)}</dd></div>
        <div><dt>Hoàn tiền mặt</dt><dd>{money(report.summary.cashRefunded)}</dd></div>
        <div><dt>Khoản nhận trong kỳ hiện chờ đối soát</dt><dd>{money(report.summary.pendingReview)}</dd></div>
      </dl>
      {report.days.length === 0 && <p>Không có giao dịch trong khoảng ngày đã chọn.</p>}
      <ul>{report.days.map(item => <li key={item.day}>
        {String(item.day).slice(0, 10)} · Nhận {money(item.received)} · Hoàn {money(item.refunded)}
        {' · '}Thu ròng {money(item.netReceived)}
      </li>)}</ul>
    </>}
  </section>;
}
