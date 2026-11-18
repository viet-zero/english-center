import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

const decisions = { PASS: 'Đạt', FAIL: 'Chưa đạt điểm hoặc chuyên cần', FINANCIAL_HOLD: 'Chờ hoàn tất học phí' };

export default function CompletionPage({ scope, onError }) {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState('');
  const [view, setView] = useState(null);
  const [results, setResults] = useState([]);
  const [certificates, setCertificates] = useState([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        if (scope === 'student') {
          const [rows, documents] = await Promise.all([api.completionResults(), api.certificates()]);
          if (active) { setResults(rows); setCertificates(documents); }
        } else {
          const list = await api.completionClasses(scope);
          if (active) setClasses(list);
        }
      } catch (error) { if (active) onError(error); }
    }
    load();
    return () => { active = false; };
  }, [scope, onError]);

  async function run(action, confirmation) {
    setBusy(true);
    setMessage('');
    onError(null);
    try { await action(); setMessage(confirmation); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  if (scope === 'student') return <section className="panel">
    <h1>Kết quả và chứng chỉ</h1>
    {results.length === 0 && <p>Chưa có kết quả đã chốt.</p>}
    <ul>{results.map(item => <li key={item.classCode}>
      <strong>{item.classCode}</strong> · {item.moduleName} · Điểm {item.finalScore}/10 ·
      Chuyên cần {item.attendancePercent}% · {decisions[item.decision]}
    </li>)}</ul>
    <h2>Chứng chỉ khóa học</h2>
    <p>Chứng chỉ được cấp sau khi đạt đủ các học phần, chuyên cần và học phí của khóa.</p>
    {certificates.length === 0 && <p>Chưa có chứng chỉ được cấp.</p>}
    {certificates.map(item => <article key={item.id}>
      <h3>{item.courseName}</h3>
      <p>{item.code} · Điểm {item.finalScore}/10 · Chuyên cần {item.attendancePercent}%</p>
      <a href={'/api/academics/student/certificates/' + encodeURIComponent(item.id) + '/pdf'}>Tải chứng chỉ PDF {item.code}</a>
    </article>)}
  </section>;

  const finalized = Boolean(view?.classroom.finalized_at);
  const canFinalize = !finalized && view?.ended && view.students.length > 0 && view.students.every(item => item.ready);
  return <section className="panel">
    <h1>{scope === 'training' ? 'Chốt kết quả cuối khóa' : 'Điểm tổng kết lớp giảng dạy'}</h1>
    <label>Lớp tổng kết<select value={classId} disabled={busy} onChange={event => {
      const id = event.target.value;
      setClassId(id);
      setView(null);
      if (id) run(async () => setView(await api.completionClass(scope, id)), '');
    }}>
      <option value="">Chọn lớp</option>
      {classes.map(item => <option key={item.id} value={item.id}>{item.classCode} · {item.moduleName}</option>)}
    </select></label>
    {message && <p role="status">{message}</p>}
    {view && <>
      <p>Điểm tổng kết = tổng (điểm kỳ thi × hệ số) / tổng hệ số, làm tròn 2 chữ số.
        Chuyên cần tính buổi có mặt hoặc đi trễ trên tổng buổi không bị hủy.</p>
      <p>Ngưỡng đạt {view.classroom.completion_pass_score}/10 · Chuyên cần tối thiểu {view.classroom.completion_attendance}%</p>
      <p>{finalized ? 'Đã chốt. Điểm, điểm danh và hệ số được khóa.' :
        view.ended ? 'Lịch học và kỳ thi đã kết thúc.' : 'Lớp chưa kết thúc đầy đủ lịch học và kỳ thi.'}</p>
      {scope === 'training' && !finalized && <form onSubmit={event => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        run(async () => {
          const weights = Object.fromEntries(view.exams.map(exam => [exam.id, data.get('weight-' + exam.id)]));
          await api.completionPolicy(classId, {
            passScore: data.get('passScore'), minimumAttendance: data.get('minimumAttendance'), weights,
          });
          setView(await api.completionClass(scope, classId));
        }, 'Đã lưu quy định tổng kết.');
      }}><fieldset disabled={busy}>
        <label>Điểm đạt tối thiểu<input name="passScore" type="number" min="0" max="10" step="0.01" required defaultValue={view.classroom.completion_pass_score} /></label>
        <label>Chuyên cần tối thiểu (%)<input name="minimumAttendance" type="number" min="0" max="100" step="0.01" required defaultValue={view.classroom.completion_attendance} /></label>
        {view.exams.map(exam => <label key={exam.id}>Hệ số {exam.title}<input name={'weight-' + exam.id} type="number" min="0.01" max="9999.99" step="0.01" required defaultValue={exam.weight} /></label>)}
        <button>Lưu quy định tổng kết</button>
      </fieldset></form>}
      <ul>{view.students.map(item => <li key={item.enrollmentId}>
        <strong>{item.fullName}</strong> · Điểm {item.finalScore ?? 'Chưa đủ'}/10 · Chuyên cần {item.attendancePercent}%
        {' · '}{item.paid ? 'Đã đủ học phí' : 'Chưa hoàn tất học phí'}
        {' · '}{item.missingGrades > 0 ? 'Thiếu ' + item.missingGrades + ' điểm' : ''}
        {' · '}{item.missingAttendance > 0 ? 'Thiếu ' + item.missingAttendance + ' buổi điểm danh' : ''}
        {' · '}{decisions[item.decision]}
        {scope === 'training' && finalized && item.decision !== 'FAIL' && <button className="outline" disabled={busy} onClick={() => run(async () => {
          await api.issueCertificate(classId, item.enrollmentId);
          setView(await api.completionClass(scope, classId));
        }, 'Đã kiểm tra học phí và kết quả. Chứng chỉ được cấp khi đủ tất cả học phần của khóa.')}>Kiểm tra và cấp chứng chỉ cho {item.fullName}</button>}
      </li>)}</ul>
      {scope === 'training' && !finalized && <button disabled={busy || !canFinalize} onClick={() => run(async () => {
        await api.finalizeClass(classId);
        setView(await api.completionClass(scope, classId));
      }, 'Đã chốt kết quả và cấp chứng chỉ cho học viên đủ điều kiện.')}>Chốt kết quả lớp</button>}
    </>}
  </section>;
}
