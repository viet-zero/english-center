import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

const choices = {
  PRESENT: 'Có mặt', EXCUSED: 'Vắng có phép', ABSENT: 'Vắng không phép', LATE: 'Đi muộn',
};

export default function LessonAttendance({ lesson, onError, onClose }) {
  const [rows, setRows] = useState(null);
  const [statuses, setStatuses] = useState({});
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let active = true;
    api.attendanceRoster(lesson.class_id, lesson.id).then(roster => {
      if (!active) return;
      setRows(roster);
      setStatuses(Object.fromEntries(roster.map(row => [row.id, row.attendance_status ?? 'PRESENT'])));
    }).catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [lesson, onError]);

  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setSaved(false);
    onError(null);
    try {
      await api.saveAttendance(lesson.class_id, lesson.id, statuses);
      setSaved(true);
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  const present = Object.values(statuses).filter(status => status === 'PRESENT').length;
  return <section className="panel" aria-label="Điểm danh theo buổi">
    <h2>Điểm danh {lesson.class_code}</h2>
    <p>{lesson.day} · {lesson.room} · {lesson.start_time}–{lesson.end_time}</p>
    <button className="outline" disabled={busy} onClick={onClose}>Đóng điểm danh</button>
    {!rows && <p>Đang tải danh sách lớp…</p>}
    {rows?.length === 0 && <p>Lớp chưa có học viên.</p>}
    {rows?.length > 0 && <form onSubmit={save}>
      <p>Có mặt {present}/{rows.length}</p>
      <fieldset disabled={busy}>
        {rows.map(row => <label className="attendance-row" key={row.id}>
          {row.full_name} · {row.student_code}
          <select value={statuses[row.id]} onChange={event => {
            setStatuses({ ...statuses, [row.id]: event.target.value });
            setSaved(false);
          }}>
            {Object.entries(choices).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>)}
        <button className="outline" type="button" onClick={() => {
          setStatuses(Object.fromEntries(rows.map(row => [row.id, 'PRESENT'])));
          setSaved(false);
        }}>Đánh dấu tất cả có mặt</button>
        <button>Lưu điểm danh</button>
      </fieldset>
    </form>}
    {saved && <p role="status">Đã lưu điểm danh cả lớp.</p>}
  </section>;
}
