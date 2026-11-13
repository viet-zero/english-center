import React, { useEffect, useState } from 'react';
import { attendanceLabels } from '../components/StatusBadge.jsx';
import { api } from '../services/api.js';



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

  useEffect(() => {
    if (!saved) return;
    const timer = setTimeout(() => setSaved(false), 4000);
    return () => clearTimeout(timer);
  }, [saved]);

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
        {rows.map(row => <div className="attendance-row" key={row.id}>
          <strong id={'attendance-' + row.id}>{row.full_name} · {row.student_code}</strong>
          <div className="attendance-options" role="group" aria-labelledby={'attendance-' + row.id}>
            {Object.entries(attendanceLabels).map(([value, label]) =>
              <button key={value} type="button" className="outline" data-status={value}
                aria-pressed={statuses[row.id] === value} onClick={() => {
                  setStatuses(current => ({ ...current, [row.id]: value }));
                  setSaved(false);
                }}>{label}</button>)}
          </div>
        </div>)}
        <button className="outline" type="button" onClick={() => {
          setStatuses(Object.fromEntries(rows.map(row => [row.id, 'PRESENT'])));
          setSaved(false);
        }}>Đánh dấu tất cả có mặt</button>
        <button>Lưu điểm danh</button>
      </fieldset>
    </form>}
    {saved && <p className="attendance-toast" role="status">Đã lưu điểm danh cả lớp.</p>}
  </section>;
}
