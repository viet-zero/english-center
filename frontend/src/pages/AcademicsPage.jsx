import React, { useEffect, useState } from 'react';
import LearningContent from './LearningContent.jsx';
import StatusBadge, { attendanceLabels, examStatus } from '../components/StatusBadge.jsx';
import { api } from '../services/api.js';


const formatTime = value => String(value).replace('T', ' ').slice(0, 16);

export default function AcademicsPage({ teacher, onError }) {
  const scope = teacher ? 'teacher' : 'student';
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState('');
  const [view, setView] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function load(action) {
    setBusy(true);
    onError(null);
    setMessage('');
    try {
      await action();
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    let active = true;
    api.academicClasses(scope).then(result => { if (active) setClasses(result); })
      .catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [scope, onError]);

  function choose(event) {
    const next = event.target.value;
    setClassId(next);
    setView(null);
    if (next) load(async () => setView(await api.academicClass(scope, next)));
  }

  function submit(action) {
    return event => {
      event.preventDefault();
      const form = event.currentTarget;
      const values = Object.fromEntries(new FormData(form));
      load(async () => {
        await action(values);
        setMessage('Đã lưu.');
        setView(await api.academicClass(scope, classId));
        form.reset();
      });
    };
  }

  return <section className="panel">
    <h2>{teacher ? 'Quản lý lớp giảng dạy' : 'Lớp của tôi'}</h2>
    <label>Lớp<select value={classId} onChange={choose} disabled={busy}>
      <option value="">Chọn lớp</option>
      {classes.map(item => <option key={item.id} value={item.id}>{item.class_code}</option>)}
    </select></label>
    <button type="button" disabled={busy} onClick={() => load(async () => setClasses(await api.academicClasses(scope)))}>Tải lại danh sách lớp</button>
    {message && <p role="status">{message}</p>}
    {view && <>
      <LearningContent key={classId} classId={classId} teacher={teacher} onError={onError} />
      <h3>Lịch thi và điểm thi</h3>
      {view.exams.length === 0 && <p>Chưa có lịch thi.</p>}
      <ul className="exam-list">{view.exams.map(exam => <li key={exam.id}>
        <time className="exam-date" dateTime={exam.starts_at.slice(0, 10)}>
          <strong>{exam.starts_at.slice(8, 10)}</strong>Tháng {Number(exam.starts_at.slice(5, 7))}
        </time>
        <div>
          <strong>{exam.title}</strong>
          <div>{formatTime(exam.starts_at)} – {formatTime(exam.ends_at)} · {exam.room} · {exam.exam_format === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</div>
          {exam.invigilator && <small>Giám thị: {exam.invigilator}</small>}
          {!teacher && <div>Điểm: {view.grades.find(grade => grade.exam_id === exam.id)?.score ?? 'Chưa có'}</div>}
        </div>
        <StatusBadge status={examStatus(exam)} />
      </li>)}</ul>
      <h3>Điểm danh</h3>
      <ul>{view.lessons.map(lesson => <li key={lesson.id}>
        {lesson.lesson_date} · {lesson.start_time}–{lesson.end_time}
        {!teacher && <StatusBadge status={view.attendance.find(row => row.lesson_id === lesson.id)?.attendance_status ?? 'UNMARKED'} />}
      </li>)}</ul>
      {teacher && <div className="fields">
        <form onSubmit={submit(values => api.createExam(classId, values))}>
          <fieldset disabled={busy}>
            <legend>Tạo lịch thi</legend>
            <label>Tên kỳ thi<input name="title" required maxLength={150} /></label>
            <label>Bắt đầu<input name="startsAt" type="datetime-local" required /></label>
            <label>Kết thúc<input name="endsAt" type="datetime-local" required /></label>
            <label>Phòng thi<input name="room" required maxLength={100} /></label>
            <button>Lưu lịch thi</button>
          </fieldset>
        </form>
        <form onSubmit={submit(values => api.attendance(classId, values.lessonId, values.enrollmentId, values.status))}>
          <fieldset disabled={busy || !view.lessons.length || !view.students.length}>
            <legend>Điểm danh</legend>
            <label>Buổi học<select name="lessonId" required>
              {view.lessons.map(item => <option key={item.id} value={item.id}>{item.lesson_date} · {item.start_time}</option>)}
            </select></label>
            <StudentSelect students={view.students} />
            <label>Trạng thái<select name="status">
              {Object.entries(attendanceLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
            </select></label>
            <button>Lưu điểm danh</button>
          </fieldset>
        </form>
        <form onSubmit={submit(values => api.grade(classId, values.examId, values.enrollmentId, values.score))}>
          <fieldset disabled={busy || !view.exams.length || !view.students.length}>
            <legend>Nhập điểm</legend>
            <label>Kỳ thi<select name="examId" required>
              {view.exams.map(item => <option key={item.id} value={item.id}>{item.title} · {formatTime(item.starts_at)}</option>)}
            </select></label>
            <StudentSelect students={view.students} />
            <label>Điểm (0–10)<input name="score" type="number" required min="0" max="10" step="0.01" /></label>
            <button>Lưu điểm</button>
          </fieldset>
        </form>
      </div>}
      {teacher && (view.attendance.length > 0 || view.grades.length > 0) && <>
        <h3>Kết quả đã lưu</h3>
        <ul>{view.attendance.map(row => <li key={row.lesson_id + row.enrollment_id}>
          {view.students.find(item => item.id === row.enrollment_id)?.full_name ?? row.enrollment_id}
          {' · '}{view.lessons.find(item => item.id === row.lesson_id)?.lesson_date}
          {' · '}<StatusBadge status={row.attendance_status} />
        </li>)}
        {view.grades.map(row => <li key={row.exam_id + row.enrollment_id}>
          {view.students.find(item => item.id === row.enrollment_id)?.full_name ?? row.enrollment_id}
          {' · '}{view.exams.find(item => item.id === row.exam_id)?.title}
          {' · '}{row.score}/10
        </li>)}</ul>
      </>}
    </>}
  </section>;
}

function StudentSelect({ students }) {
  return <label>Học viên<select name="enrollmentId" required>
    {students.map(student => <option key={student.id} value={student.id}>{student.full_name}</option>)}
  </select></label>;
}
