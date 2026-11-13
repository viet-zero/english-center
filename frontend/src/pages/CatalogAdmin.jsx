import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function CatalogAdmin({ onError }) {
  const [courses, setCourses] = useState([]);
  const [modules, setModules] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [course, setCourse] = useState('');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState('');

  useEffect(() => {
    Promise.all([api.trainingCourses(), api.invigilators()])
      .then(([list, staff]) => { setCourses(list); setTeachers(staff); }).catch(onError);
  }, [onError]);

  async function run(action) {
    setBusy(true);
    setSaved('');
    onError(null);
    try { await action(); } catch (error) { onError(error); } finally { setBusy(false); }
  }

  function submit(action, message) {
    return event => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = Object.fromEntries(new FormData(form));
      run(async () => { await action(data); form.reset(); setSaved(message); });
    };
  }

  return <section className="panel">
    <h1>Quản lý khóa học</h1>
    {saved && <p role="status">{saved}</p>}
    <form onSubmit={submit(async data => {
      await api.createCourse({ ...data, status: 'ACTIVE' });
      setCourses(await api.trainingCourses());
    }, 'Đã thêm khóa học.')}>
      <fieldset disabled={busy}>
        <legend>Thêm khóa học</legend>
        <label>Tên khóa học<input name="name" required maxLength={150} /></label>
        <label>Mô tả<textarea name="description" maxLength={5000} /></label>
        <button>Thêm khóa học</button>
      </fieldset>
    </form>
    <label>Khóa học<select value={course} disabled={busy} onChange={event => {
      const value = event.target.value;
      setCourse(value);
      setModules([]);
      if (value) run(async () => setModules(await api.trainingModules(value)));
    }}>
      <option value="">Chọn khóa học</option>
      {courses.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
    </select></label>
    <form onSubmit={submit(async data => {
      await api.createModule(course, {
        sequenceNumber: Number(data.sequenceNumber),
        details: { name: data.name, tuition: data.tuition, durationHours: Number(data.hours), status: 'ACTIVE' },
      });
      setModules(await api.trainingModules(course));
    }, 'Đã thêm học phần.')}>
      <fieldset disabled={busy || !course}>
        <legend>Thêm học phần</legend>
        <label>Tên học phần<input name="name" required maxLength={150} /></label>
        <label>Thứ tự<input name="sequenceNumber" type="number" min="1" required /></label>
        <label>Học phí<input name="tuition" type="number" min="0" step="0.01" required /></label>
        <label>Thời lượng (giờ)<input name="hours" type="number" min="1" required /></label>
        <button>Thêm học phần</button>
      </fieldset>
    </form>
    <form onSubmit={submit(data => api.createClass(data.moduleId, {
      teacherId: data.teacherId, classCode: data.classCode, startDate: data.startDate, endDate: data.endDate,
      details: { maxStudents: Number(data.capacity), status: 'OPEN' },
    }), 'Đã thêm lớp.')}>
      <fieldset disabled={busy || !modules.length || !teachers.length}>
        <legend>Thêm lớp</legend>
        <label>Học phần<select name="moduleId">{modules.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Giảng viên<select name="teacherId">{teachers.map(item => <option key={item.id} value={item.id}>{item.full_name}</option>)}</select></label>
        <label>Mã lớp<input name="classCode" maxLength={30} required /></label>
        <label>Ngày bắt đầu<input name="startDate" type="date" required /></label>
        <label>Ngày kết thúc<input name="endDate" type="date" required /></label>
        <label>Sĩ số tối đa<input name="capacity" type="number" min="1" required /></label>
        <button>Thêm lớp</button>
      </fieldset>
    </form>
  </section>;
}
