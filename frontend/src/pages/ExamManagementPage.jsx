import React, { useEffect, useState } from 'react';
import StatusBadge, { examStatus } from '../components/StatusBadge.jsx';
import { api } from '../services/api.js';

export default function ExamManagementPage({ onError }) {
  const [data, setData] = useState(null);
  const [selected, setSelected] = useState('');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([api.trainingExams(), api.invigilators()])
      .then(([exams, people]) => { if (active) setData({ exams, people }); })
      .catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [onError]);

  const exam = data?.exams.find(item => item.id === selected);

  async function save(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setSaved(false);
    setBusy(true);
    onError(null);
    try {
      await api.updateExam(exam.id, {
        room: fields.get('room'),
        format: fields.get('format'),
        invigilatorIds: fields.getAll('invigilator'),
      });
      setData({ ...data, exams: await api.trainingExams() });
      setSaved(true);
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  return <section className="panel">
    <h1>Lịch thi</h1>
    <label>Kỳ thi<select value={selected} disabled={busy} onChange={event => {
      setSelected(event.target.value);
      setSaved(false);
      onError(null);
    }}>
      <option value="">Chọn kỳ thi</option>
      {data?.exams.map(item => <option key={item.id} value={item.id}>
        {item.class_code} · {item.title} · {item.starts_at}
      </option>)}
    </select></label>
    {data?.exams.length === 0 && <p>Chưa có kỳ thi.</p>}
    {exam && <form key={exam.id} onSubmit={save}>
      <p>{exam.starts_at} – {exam.ends_at} <StatusBadge status={examStatus(exam)} /></p>
      <fieldset disabled={busy}>
        <label>Phòng thi hoặc địa chỉ trực tuyến<input name="room" defaultValue={exam.room} required maxLength={100} /></label>
        <label>Hình thức thi<select name="format" defaultValue={exam.exam_format}>
          <option value="ONSITE">Trực tiếp</option><option value="ONLINE">Trực tuyến</option>
        </select></label>
        <fieldset><legend>Giám thị</legend>
          {data.people.map(person => <label key={person.id} className="check">
            <input type="checkbox" name="invigilator" value={person.id}
              defaultChecked={exam.invigilator_ids?.split(',').includes(person.id)} />
            {person.full_name} · {person.employee_code}
          </label>)}
        </fieldset>
        <button>Lưu lịch thi</button>
      </fieldset>
    </form>}
    {saved && <p role="status">Đã cập nhật lịch thi.</p>}
  </section>;
}
