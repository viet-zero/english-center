import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

export default function CoursesPage({ onInvoice, onError }) {
  const [courses, setCourses] = useState([]);
  const [modules, setModules] = useState([]);
  const [classes, setClasses] = useState([]);
  const [courseId, setCourseId] = useState('');
  const [moduleId, setModuleId] = useState('');
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    api.courses().then(items => { if (active) setCourses(items); })
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
    setModuleId('');
    setModules([]);
    setClasses([]);
    if (value) load(async () => setModules(await api.modules(value)));
  }

  function chooseModule(event) {
    const value = event.target.value;
    setModuleId(value);
    setClasses([]);
    if (value) load(async () => setClasses(await api.classes(value)));
  }

  function register(classId) {
    load(async () => {
      const result = await api.enroll(classId);
      onInvoice(result.invoiceId);
      setClasses(await api.classes(moduleId));
    });
  }

  return <section className="panel">
    <h2>Chọn lớp học</h2>
    <div className="fields">
      <label>Khóa học<select value={courseId} onChange={chooseCourse} disabled={busy}>
        <option value="">Chọn khóa học</option>
        {courses.map(course => <option key={course.id} value={course.id}>{course.name}</option>)}
      </select></label>
      <label>Học phần<select value={moduleId} onChange={chooseModule} disabled={busy || !courseId}>
        <option value="">Chọn học phần</option>
        {modules.map(module => <option key={module.id} value={module.id}>{module.name}</option>)}
      </select></label>
    </div>
    {busy && <p role="status">Đang xử lý…</p>}
    {!busy && moduleId && classes.length === 0 && <p>Chưa có lớp mở đăng ký.</p>}
    <div className="class-grid">{classes.map(item => <article key={item.id}>
      <h3>{item.classCode}</h3>
      <p>{item.teacherName} · {item.startDate} đến {item.endDate}</p>
      <p><strong>{money(item.tuition)}</strong> · Còn {item.remainingSeats} chỗ</p>
      {item.lessons.length > 0 ? <details><summary>Lịch học</summary><ul>
        {item.lessons.map((lesson, index) => <li key={index}>
          {lesson.date}, {lesson.startTime.slice(0, 5)}–{lesson.endTime.slice(0, 5)} · {lesson.roomName}
        </li>)}
      </ul></details> : <p>Chưa có lịch học.</p>}
      <button disabled={busy || item.remainingSeats <= 0} onClick={() => register(item.id)}>Đăng ký lớp</button>
    </article>)}</div>
  </section>;
}
