import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

const roleNames = { STUDENT: 'Học viên', TEACHER: 'Giảng viên', TRAINING_STAFF: 'Đào tạo', ACCOUNTANT: 'Kế toán', ADMIN: 'Quản trị viên' };

export default function AccountsPage({ onError }) {
  const [accounts, setAccounts] = useState([]);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  useEffect(() => { api.accounts().then(setAccounts).catch(onError); }, [onError]);

  async function refresh() {
    setBusy(true);
    try { setAccounts(await api.accounts()); setSelected(null); }
    catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  async function save(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setSaved(false);
    onError(null);
    try {
      await api.updateAccount(selected.id, {
        fullName: data.get('fullName'), phone: data.get('phone'), email: data.get('email'),
        identityNumber: data.get('identityNumber'), status: data.get('status'),
        roles: data.getAll('roles'), version: selected.version,
      });
      setAccounts(await api.accounts());
      setSelected(null);
      setSaved(true);
    } catch (error) { onError(error); }
    finally { setBusy(false); }
  }

  return <section className="panel">
    <h1>Quản lý tài khoản</h1>
    <label>Tìm tài khoản<input value={query} onChange={event => setQuery(event.target.value)} /></label>
    <button className="outline" onClick={refresh} disabled={busy}>Tải lại danh sách</button>
    {saved && <p role="status">Đã lưu tài khoản. Các phiên đăng nhập cũ đã hết hiệu lực.</p>}
    <ul>{accounts.filter(item => (item.username + ' ' + item.fullName).toLowerCase().includes(query.toLowerCase())).map(item =>
      <li key={item.id}>
        <strong>{item.username}</strong> · {item.fullName} · {item.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}
        {' · '}{item.roles?.split(',').map(role => roleNames[role]).join(', ')}
        <button className="outline" disabled={busy} onClick={() => { setSelected(item); setSaved(false); }}>Sửa {item.username}</button>
      </li>)}</ul>
    {selected && <form key={selected.id + '-' + selected.version} onSubmit={save}>
      <h2>Sửa tài khoản {selected.username}</h2>
      <fieldset disabled={busy}>
        <label>Họ và tên<input name="fullName" required maxLength={150} defaultValue={selected.fullName} /></label>
        <label>Số điện thoại<input name="phone" pattern="0[0-9]{9}" defaultValue={selected.phone ?? ''} /></label>
        <label>Email<input name="email" type="email" maxLength={254} defaultValue={selected.email ?? ''} /></label>
        <label>CCCD<input name="identityNumber" maxLength={20} defaultValue={selected.identityNumber ?? ''} /></label>
        <label>Trạng thái tài khoản<select name="status" defaultValue={selected.status}>
          <option value="ACTIVE">Đang hoạt động</option><option value="LOCKED">Đã khóa</option>
        </select></label>
        <fieldset><legend>Vai trò tài khoản</legend>
          {Object.entries(roleNames).filter(([role]) => role === 'STUDENT' ? selected.student : selected.employee).map(([role, name]) =>
            <label className="check" key={role}><input name="roles" type="checkbox" value={role}
              defaultChecked={selected.roles?.split(',').includes(role)} />{name}</label>)}
        </fieldset>
        <button>Lưu tài khoản</button>
        <button type="button" className="outline" onClick={() => setSelected(null)}>Đóng chỉnh sửa</button>
      </fieldset>
    </form>}
  </section>;
}
