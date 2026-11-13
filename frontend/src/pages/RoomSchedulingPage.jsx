import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

const shifts = [
  ['MORNING', 'Ca sáng · 08:30–11:30'],
  ['AFTERNOON', 'Ca chiều · 13:30–16:30'],
  ['EVENING', 'Ca tối · 18:00–20:00'],
];

export default function RoomSchedulingPage({ onError }) {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState('');
  const [day, setDay] = useState('');
  const [shift, setShift] = useState('');
  const [rooms, setRooms] = useState([]);
  const [roomId, setRoomId] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [revision, setRevision] = useState(0);
  const [saved, setSaved] = useState(false);
  const [liveNotice, setLiveNotice] = useState('');
  const [connected, setConnected] = useState(false);
  const classroom = classes.find(item => item.id === classId);

  useEffect(() => {
    let active = true;
    api.schedulingClasses().then(rows => { if (active) setClasses(rows); })
      .catch(error => { if (active) onError(error); });
    return () => { active = false; };
  }, [onError]);

  useEffect(() => {
    if (!day) return;
    let active = true;
    setLoadingRooms(true);
    Promise.all(shifts.map(async ([key]) => [key, await api.schedulingRooms(day, key)]))
      .then(results => {
        if (!active) return;
        const byShift = Object.fromEntries(results.map(([key, rows]) => [key, new Map(rows.map(row => [row.id, row]))]));
        setRooms(results[0][1].map(room => ({
          ...room, bookings: Object.fromEntries(shifts.map(([key]) => [key, byShift[key].get(room.id)])),
        })));
      })
      .catch(error => { if (active) onError(error); })
      .finally(() => { if (active) setLoadingRooms(false); });
    return () => { active = false; };
  }, [day, revision, onError]);

  useEffect(() => {
    if (!day) return;
    let socket;
    let timer;
    let active = true;

    function connect() {
      const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
      socket = new WebSocket(protocol + '//' + location.host + '/api/training/room-updates');
      socket.onopen = () => {
        if (!active) return;
        setConnected(true);
        setRevision(value => value + 1);
      };
      socket.onmessage = event => {
        if (active && event.data === day) setRevision(value => value + 1);
      };
      socket.onclose = () => {
        if (!active) return;
        setConnected(false);
        timer = setTimeout(connect, 3000);
      };
    }

    connect();
    return () => {
      active = false;
      setConnected(false);
      clearTimeout(timer);
      socket?.close();
    };
  }, [day]);

  useEffect(() => {
    if (!roomId || busy || loadingRooms) return;
    if (rooms.some(room => room.id === roomId && room.bookings[shift]?.booked_classes)) {
      setRoomId('');
      setLiveNotice('Phòng vừa được xếp. Vui lòng chọn phòng khác.');
    }
  }, [rooms, roomId, shift, busy, loadingRooms]);

  function changeSlot(update, value) {
    setLiveNotice('');
    update(value);
    setRooms([]);
    setRoomId('');
    setSaved(false);
    onError(null);
  }

  async function save(event) {
    event.preventDefault();
    setLiveNotice('');
    setBusy(true);
    setSaved(false);
    onError(null);
    try {
      await api.scheduleRoom({ classId, roomId, day, shift });
      setSaved(true);
    } catch (error) {
      onError(error);
    } finally {
      setRoomId('');
      setRooms([]);
      setRevision(value => value + 1);
      setBusy(false);
    }
  }

  return <section className="panel">
    <h1>Xếp phòng học</h1>
    {day && <p>{connected ? 'Đang cập nhật phòng trực tiếp' : 'Đang kết nối cập nhật phòng…'}</p>}
    {liveNotice && <p role="alert" className="notice">{liveNotice}</p>}
    <form onSubmit={save}>
      <fieldset disabled={busy}>
        <div className="fields">
          <label>Lớp học<select value={classId} onChange={event => {
            setClassId(event.target.value);
            setDay(classes.find(item => item.id === event.target.value)?.start_date ?? '');
            setLoadingRooms(false);
            changeSlot(setRoomId, '');
            setRevision(value => value + 1);
          }} required>
            <option value="">Chọn lớp</option>
            {classes.map(item => <option key={item.id} value={item.id}>{item.class_code} · {item.max_students} học viên</option>)}
          </select></label>
          <label>Ngày học<input type="date" value={day} min={classroom?.start_date}
            max={classroom?.end_date} disabled={!classroom} required
            onInput={event => {
              const nextDay = event.currentTarget.value;
              if (nextDay === day) return;
              setLoadingRooms(Boolean(nextDay));
              changeSlot(setDay, nextDay);
            }} /></label>
        </div>
        {day && loadingRooms && <p role="status">Đang tải phòng…</p>}
        {day && !loadingRooms && rooms.length === 0 && <p>Chưa có phòng để hiển thị.</p>}
        {day && !loadingRooms && rooms.length > 0 && <div className="table-scroll" tabIndex={0} aria-label="Lưới xếp phòng">
          <table className="room-grid">
            <thead><tr><th scope="col">Phòng học</th>{shifts.map(([key, label]) =>
              <th key={key} scope="col">{label}</th>)}</tr></thead>
            <tbody>{rooms.map(room => <tr key={room.id}>
              <th scope="row">{room.name}<small>{room.capacity} chỗ</small></th>
              {shifts.map(([key, label]) => {
                const booking = room.bookings[key]?.booked_classes;
                const staffCodes = room.bookings[key]?.booked_by_codes;
                const reason = room.status !== 'AVAILABLE' ? 'Bảo trì'
                  : room.capacity < classroom.max_students ? 'Không đủ sức chứa' : '';
                const selected = roomId === room.id && shift === key;
                const text = booking || reason || (selected ? 'Đã chọn' : 'Trống');
                return <td key={key}>
                  <button type="button" className="room-slot" disabled={Boolean(reason || booking)}
                    data-state={booking ? 'booked' : reason ? 'unavailable' : 'free'}
                    aria-pressed={selected} aria-label={room.name + ', ' + label + ', ' + text}
                    onClick={() => { setRoomId(room.id); setShift(key); setSaved(false); setLiveNotice(''); }}>
                    {text}{booking && staffCodes && <small>Người xếp: {staffCodes}</small>}
                  </button>
                </td>;
              })}
            </tr>)}</tbody>
          </table>
        </div>}
        <button disabled={!classId || !day || !roomId || loadingRooms}>Xếp lớp vào phòng</button>
      </fieldset>
    </form>
    {saved && <p role="status">Đã xếp phòng. Buổi học đã có trên thời khóa biểu.</p>}
  </section>;
}
