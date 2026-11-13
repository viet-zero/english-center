import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function LearningContent({ classId, teacher, onError }) {
  const scope = teacher ? 'teacher' : 'student';
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    api.learningContent(scope, classId).then(result => {
      if (active) setData(result);
    }).catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [classId, scope, onError]);

  function submit(action, confirmation) {
    return async event => {
      event.preventDefault();
      const form = event.currentTarget;
      const values = Object.fromEntries(new FormData(form));
      setBusy(true);
      setMessage('');
      onError(null);
      try {
        await action(values);
        setData(await api.learningContent(scope, classId));
        setMessage(confirmation);
        if (teacher) form.reset();
      } catch (error) {
        onError(error);
      } finally {
        setBusy(false);
      }
    };
  }

  const items = data?.items;
  return <section>
    <h3>Tài liệu và bài tập</h3>
    {teacher && <form onSubmit={submit(values => api.publishLearningContent(classId, values), 'Đã đăng nội dung.')}>
      <fieldset disabled={busy}>
        <label>Loại nội dung<select name="kind">
          <option value="MATERIAL">Tài liệu</option><option value="ASSIGNMENT">Bài tập</option>
        </select></label>
        <label>Tiêu đề<input name="title" maxLength={150} required /></label>
        <label>Nội dung hoặc liên kết<textarea name="content" maxLength={4000} required /></label>
        <button>Đăng nội dung</button>
      </fieldset>
    </form>}
    {message && <p role="status">{message}</p>}
    {items?.length === 0 && <p>Chưa có tài liệu hoặc bài tập.</p>}
    {items?.map(item => {
      const assignment = item.kind === 'ASSIGNMENT';
      const answer = data?.submissions?.find(row => row.content_id === item.id)?.answer;
      const link = !assignment && /^https?:\/\/[^\s]+$/i.test(item.content);
      return <article key={item.id}>
        <h4>{assignment ? 'Bài tập' : 'Tài liệu'} · {item.title}</h4>
        <p>{link ? <a href={item.content} target="_blank" rel="noreferrer">Mở tài liệu</a> : item.content}</p>
        {teacher && assignment && <details><summary>Bài nộp</summary>
          {data.submissions.filter(row => row.content_id === item.id).map(row =>
            <p key={row.enrollment_id}><strong>{row.full_name}</strong> · {row.answer}</p>)}
        </details>}
        {!teacher && assignment && <form onSubmit={submit(
          values => api.submitAssignment(classId, item.id, values.answer), 'Đã lưu bài nộp.',
        )}>
          <label>Bài làm<textarea name="answer" defaultValue={answer ?? ''} maxLength={4000} required /></label>
          <button disabled={busy}>{answer ? 'Cập nhật bài nộp' : 'Nộp bài'}</button>
        </form>}
      </article>;
    })}
  </section>;
}
