import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function TrainingPage({ onError }) {
  const [courses, setCourses] = useState([]);
  const [modules, setModules] = useState([]);
  const [classes, setClasses] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    api.trainingCourses().then(rows => { if (active) setCourses(rows); })
      .catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [onError]);

  async function load(fetchItems, update) {
    setBusy(true);
    onError(null);
    try {
      update(await fetchItems());
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  return <section className="panel">
    <h1>Danh mục đào tạo</h1>
    <div className="fields">
      <label>Khóa học<select disabled={busy} onChange={event => {
        const id = event.target.value;
        setModules([]);
        setClasses([]);
        if (id) load(() => api.trainingModules(id), setModules);
      }}>
        <option value="">Chọn khóa học</option>
        {courses.map(course => <option key={course.id} value={course.id}>{course.name}</option>)}
      </select></label>
      <label>Học phần<select key={modules[0]?.id} disabled={busy || !modules.length} onChange={event => {
        const id = event.target.value;
        setClasses([]);
        if (id) load(() => api.trainingClasses(id), setClasses);
      }}>
        <option value="">Chọn học phần</option>
        {modules.map(module => <option key={module.id} value={module.id}>{module.name}</option>)}
      </select></label>
    </div>
    <ul>{classes.map(item => <li key={item.id}>
      {item.classCode} · {item.startDate} – {item.endDate} · Sĩ số tối đa {item.maxStudents}
    </li>)}</ul>
  </section>;
}
