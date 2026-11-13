import React, { useState } from 'react';
import { api } from '../services/api.js';

const roles = { TEACHER: 'Giảng viên', TRAINING_STAFF: 'Đào tạo', ACCOUNTANT: 'Kế toán', ADMIN: 'Quản trị viên' };

export default function AdminPage({ onError }) {
  const [employee, setEmployee] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const account = Object.fromEntries(['fullName', 'username', 'password'].map(key => [key, fields.get(key)]));
    const selected = fields.getAll('roles');
    setSaved(false);
    if (employee && !selected.length) {
      onError(new Error('Chọn ít nhất một vai trò.'));
      return;
    }
    setBusy(true);
    onError(null);
    try {
      if (employee) await api.createEmployee({ employee: { account, employeeCode: fields.get('code') }, roles: selected });
      else await api.createStudent({ account, studentCode: fields.get('code') });
      form.reset();
      setSaved(true);
    } catch (error) {
      onError(error);
    } finally {
      setBusy(false);
    }
  }

  return <section className="panel">
    <h1>Tạo tài khoản</h1>
    <label>Loại tài khoản<select disabled={busy} value={employee ? 'employee' : 'student'}
      onChange={event => { setEmployee(event.target.value === 'employee'); setSaved(false); }}>
      <option value="student">Học viên</option><option value="employee">Nhân viên</option>
    </select></label>
    <form key={String(employee)} onSubmit={submit}>
      <fieldset disabled={busy}>
        <label>Họ và tên<input name="fullName" required maxLength={150} /></label>
        <label>Tên đăng nhập<input name="username" required maxLength={100} autoComplete="off" /></label>
        <label>Mật khẩu<input name="password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <label>{employee ? 'Mã nhân viên' : 'Mã học viên'}<input name="code" required maxLength={30} /></label>
        {employee && <fieldset><legend>Vai trò</legend>
          {Object.entries(roles).map(([id, name]) => <label className="check" key={id}>
            <input type="checkbox" name="roles" value={id} />{name}
          </label>)}
        </fieldset>}
        <button>Tạo tài khoản</button>
      </fieldset>
    </form>
    {saved && <p role="status">Đã tạo tài khoản.</p>}
  </section>;
}
