import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { money } from './TuitionPage.jsx';

const classStatuses = { OPEN: 'Mở đăng ký', IN_PROGRESS: 'Đang học', CLOSED: 'Đóng đăng ký', CANCELLED: 'Đã hủy' };

export default function CatalogAdmin({ onError }) {
  const [courses, setCourses] = useState([]);
  const [modules, setModules] = useState([]);
  const [classes, setClasses] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [courseId, setCourseId] = useState('');
  const [moduleId, setModuleId] = useState('');
  const [classId, setClassId] = useState('');
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([api.trainingCourses(), api.invigilators()])
      .then(([courses, teachers]) => {
        if (active) { setCourses(courses); setTeachers(teachers); }
      })
      .catch(error => { if (active) onError(error); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [onError]);

  async function run(action, confirmation = '') {
    setBusy(true);
    setMessage('');
    onError(null);
    try { await action(); setMessage(confirmation); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  function submit(action, confirmation) {
    return event => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = Object.fromEntries(new FormData(form));
      run(async () => { await action(data); form.reset(); }, confirmation);
    };
  }

  function chooseCourse(event) {
    const id = event.target.value;
    setCourseId(id);
    setModuleId('');
    setClassId('');
    setModules([]);
    setClasses([]);
    if (id) run(async () => setModules(await api.trainingModules(id)));
  }

  function chooseModule(event) {
    const id = event.target.value;
    setModuleId(id);
    setClassId('');
    setClasses([]);
    if (id) run(async () => setClasses(await api.trainingClasses(id)));
  }

  const course = courses.find(item => item.id === courseId);
  const module = modules.find(item => item.id === moduleId);
  const classroom = classes.find(item => item.id === classId);
  const visibleCourses = courses.filter(item =>
    item.id === courseId || item.name.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')));

  return <section className="panel">
    <h1>Quản lý khóa học, học phần và lớp</h1>
    <p>Chọn khóa học, học phần và lớp để chỉnh sửa. Ngừng mở danh mục sẽ ẩn danh mục đó trên trang đăng ký mới.</p>
    {busy && <p role="status">Đang xử lý…</p>}
    {message && <p role="status">{message}</p>}
    <label>Tìm khóa học<input value={query} disabled={busy} onChange={event => setQuery(event.target.value)} /></label>
    <label>Khóa học quản lý<select value={courseId} disabled={busy} onChange={chooseCourse}>
      <option value="">Chọn khóa học</option>
      {visibleCourses.map(item => <option key={item.id} value={item.id}>
        {item.name} · {item.status === 'ACTIVE' ? 'Đang mở' : 'Ngừng mở'}
      </option>)}
    </select></label>
    <details><summary>Thêm khóa học</summary>
      <form onSubmit={submit(async data => {
        const created = await api.createCourse({ ...data, status: 'ACTIVE' });
        setCourses(await api.trainingCourses());
        setCourseId(created.id);
        setModules([]);
        setModuleId('');
        setClassId('');
        setClasses([]);
      }, 'Đã thêm khóa học.')}><fieldset disabled={busy}>
        <CourseFields />
        <button>Thêm khóa học</button>
      </fieldset></form>
    </details>

    {course && <>
      <h2>Thông tin khóa học</h2>
      <form key={JSON.stringify(course)} onSubmit={submit(async data => {
        await api.updateCourse(courseId, data);
        setCourses(await api.trainingCourses());
      }, 'Đã cập nhật khóa học.')}><fieldset disabled={busy}>
        <CourseFields value={course} />
        <CatalogStatus value={course.status} />
        <button>Lưu khóa học</button>
      </fieldset></form>
      <label>Học phần quản lý<select value={moduleId} disabled={busy} onChange={chooseModule}>
        <option value="">Chọn học phần</option>
        {modules.map(item => <option key={item.id} value={item.id}>
          {item.sequenceNumber}. {item.name} · {money(item.tuition)} · {item.status === 'ACTIVE' ? 'Đang mở' : 'Ngừng mở'}
        </option>)}
      </select></label>
      <details><summary>Thêm học phần vào khóa học</summary>
        <form onSubmit={submit(async data => {
          const created = await api.createModule(courseId, {
            sequenceNumber: Number(data.sequenceNumber),
            details: { name: data.name, tuition: data.tuition, durationHours: Number(data.durationHours), status: 'ACTIVE' },
          });
          setModules(await api.trainingModules(courseId));
          setModuleId(created.id);
          setClasses([]);
          setClassId('');
        }, 'Đã thêm học phần.')}><fieldset disabled={busy}>
          <label>Thứ tự học phần<input name="sequenceNumber" type="number" min="1" required /></label>
          <ModuleFields />
          <button>Thêm học phần</button>
        </fieldset></form>
      </details>
    </>}

    {module && <>
      <h2>Thông tin học phần {module.sequenceNumber}</h2>
      <form key={JSON.stringify(module)} onSubmit={submit(async data => {
        await api.updateModule(moduleId, {
          name: data.name, tuition: data.tuition, durationHours: Number(data.durationHours), status: data.status,
        });
        setModules(await api.trainingModules(courseId));
      }, 'Đã cập nhật học phần.')}><fieldset disabled={busy}>
        <ModuleFields value={module} />
        <CatalogStatus value={module.status} />
        <button>Lưu học phần</button>
      </fieldset></form>
      <label>Lớp quản lý<select value={classId} disabled={busy} onChange={event => setClassId(event.target.value)}>
        <option value="">Chọn lớp</option>
        {classes.map(item => <option key={item.id} value={item.id}>
          {item.classCode} · {classStatuses[item.status] ?? item.status} · Tối đa {item.maxStudents} học viên
        </option>)}
      </select></label>
      {classes.length === 0 && !busy && <p>Học phần chưa có lớp.</p>}
      <details><summary>Thêm lớp cho học phần</summary>
        <form onSubmit={submit(async data => {
          const created = await api.createClass(moduleId, {
            teacherId: data.teacherId, classCode: data.classCode, startDate: data.startDate, endDate: data.endDate,
            details: { maxStudents: Number(data.maxStudents), status: 'OPEN' },
          });
          setClasses(await api.trainingClasses(moduleId));
          setClassId(created.id);
        }, 'Đã thêm lớp. Nhân viên đào tạo tiếp tục xếp phòng và lịch học.')}><fieldset disabled={busy || teachers.length === 0}>
          <label>Mã lớp mới<input name="classCode" maxLength={30} required /></label>
          <label>Giảng viên lớp mới<select name="teacherId" required>
            <option value="">Chọn giảng viên</option>
            {teachers.map(item => <option key={item.id} value={item.id}>{item.full_name} · {item.employee_code}</option>)}
          </select></label>
          <label>Ngày bắt đầu<input name="startDate" type="date" required /></label>
          <label>Ngày kết thúc<input name="endDate" type="date" required /></label>
          <label>Sĩ số tối đa<input name="maxStudents" type="number" min="1" required /></label>
          <button>Thêm lớp</button>
        </fieldset></form>
      </details>
    </>}

    {classroom && <>
      <h2>Thông tin lớp {classroom.classCode}</h2>
      <p>{classroom.startDate} đến {classroom.endDate} · Giảng viên {
        teachers.find(item => item.id === classroom.teacherId)?.full_name ?? classroom.teacherId
      }</p>
      {classroom.finalizedAt || classroom.status === 'CANCELLED'
        ? <p>Lớp đã chốt kết quả hoặc đã hủy và được khóa chỉnh sửa.</p>
        : <form key={JSON.stringify(classroom)} onSubmit={submit(async data => {
          await api.updateClass(classId, { maxStudents: Number(data.maxStudents), status: data.status });
          setClasses(await api.trainingClasses(moduleId));
        }, 'Đã cập nhật lớp.')}><fieldset disabled={busy}>
          <label>Sĩ số tối đa<input name="maxStudents" type="number" min="1" required defaultValue={classroom.maxStudents} /></label>
          <label>Trạng thái lớp<select name="status" defaultValue={classroom.status}>
            <option value="OPEN">Đang mở đăng ký</option>
            <option value="IN_PROGRESS">Đang học</option>
            <option value="CLOSED">Đóng đăng ký</option>
          </select></label>
          <button>Lưu lớp</button>
        </fieldset></form>}
    </>}
  </section>;
}

function CourseFields({ value }) {
  return <>
    <label>Tên khóa học<input name="name" maxLength={150} required defaultValue={value?.name ?? ''} /></label>
    <label>Mô tả khóa học<textarea name="description" maxLength={5000} defaultValue={value?.description ?? ''} /></label>
  </>;
}

function ModuleFields({ value }) {
  return <>
    <label>Tên học phần<input name="name" maxLength={150} required defaultValue={value?.name ?? ''} /></label>
    <label>Học phí (₫)<input name="tuition" type="number" min="0" step="0.01" required defaultValue={value?.tuition ?? ''} /></label>
    <label>Thời lượng (giờ)<input name="durationHours" type="number" min="1" required defaultValue={value?.durationHours ?? ''} /></label>
  </>;
}

function CatalogStatus({ value }) {
  return <label>Trạng thái danh mục<select name="status" defaultValue={value}>
    <option value="ACTIVE">Đang mở</option>
    <option value="INACTIVE">Ngừng mở</option>
  </select></label>;
}
