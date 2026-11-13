import React, { useEffect, useState } from 'react';
import LessonAttendance from './LessonAttendance.jsx';
import { api } from '../services/api.js';

const days = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];
const shifts = [
  ['Ca sáng', '08:30–11:30', '12:00'],
  ['Ca chiều', '13:30–16:30', '17:00'],
  ['Ca tối', '18:00–20:00', '24:00'],
];

function addDays(date, count) {
  const next = new Date(date);
  next.setDate(next.getDate() + count);
  return next;
}
function dateKey(date) {
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    .map(value => String(value).padStart(2, '0')).join('-');
}
function monday() {
  const date = new Date();
  return addDays(date, -((date.getDay() + 6) % 7));
}
function eventColor(event) {
  if (event.kind === 'EXAM') return 'exam';
  if (/ielts/i.test(event.course)) return 'ielts';
  if (/toeic/i.test(event.course)) return 'toeic';
  return 'conversation';
}

export default function WeekPage({ scope, onError }) {
  const [lesson, setLesson] = useState(null);
  const [week, setWeek] = useState(monday);
  const [events, setEvents] = useState(null);
  const start = dateKey(week);
  const today = dateKey(new Date());
  const dates = days.map((_, index) => addDays(week, index));

  useEffect(() => {
    let active = true;
    api.timetable(scope, start).then(rows => { if (active) setEvents(rows); })
      .catch(error => { if (active) { setEvents([]); onError(error); } });
    return () => { active = false; };
  }, [scope, start, onError]);

  function change(next) {
    if (dateKey(next) === start) return;
    setLesson(null);
    setEvents(null);
    onError(null);
    setWeek(next);
  }

  return <section className="panel">
    <h1>Thời khóa biểu tuần</h1>
    <div className="week-controls">
      <button className="outline" onClick={() => change(addDays(week, -7))}>Tuần trước</button>
      <strong>{week.toLocaleDateString('vi-VN')} – {dates[6].toLocaleDateString('vi-VN')}</strong>
      <button className="outline" onClick={() => change(addDays(week, 7))}>Tuần sau</button>
      <button onClick={() => change(monday())}>Hôm nay</button>
    </div>
    {events === null && <p role="status">Đang tải lịch…</p>}
    {events?.length === 0 && <p>Tuần này chưa có lịch.</p>}
    <div className="table-scroll" tabIndex={0} aria-label="Bảng thời khóa biểu">
      <table className="week-table">
        <thead><tr><th scope="col">Ca học</th>{dates.map((date, index) =>
          <th key={dateKey(date)} scope="col" className={dateKey(date) === today ? 'today' : ''}>
            {days[index]}<small>{date.getDate()}/{date.getMonth() + 1}</small>
          </th>)}</tr></thead>
        <tbody>{shifts.map(([label, hours, end], index) => <tr key={label}>
          <th scope="row">{label}<small>{hours}</small></th>
          {dates.map(date => <td key={dateKey(date)} className={dateKey(date) === today ? 'today' : ''}>
            {events?.filter(event => event.day === dateKey(date) && event.start_time < end
              && (index === 0 || event.start_time >= shifts[index - 1][2]))
              .map(event => <div key={event.kind + event.id} className={'schedule-item ' + eventColor(event)}>
                <strong>{event.class_code}</strong>
                {event.title && <span>{event.title}</span>}
                <span>{event.room} · {event.start_time}–{event.end_time}</span>
                {scope === 'teacher' && event.kind === 'LESSON' && <button type="button" className="outline" onClick={() => { onError(null); setLesson(event); }}>Điểm danh</button>}
              </div>)}
          </td>)}
        </tr>)}</tbody>
      </table>
    </div>
    <div className="legend">{[['ielts', 'IELTS'], ['toeic', 'TOEIC'], ['conversation', 'Giao tiếp / khóa khác'], ['exam', 'Lịch thi']]
      .map(([color, label]) => <span className={'schedule-item ' + color} key={color}>{label}</span>)}</div>
    {lesson && <LessonAttendance key={lesson.id} lesson={lesson} onError={onError} onClose={() => setLesson(null)} />}
  </section>;
}
