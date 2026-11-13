import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function RoomSchedulingPage({ onError }) {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState('');
  const [day, setDay] = useState('');
  const [shift, setShift] = useState('MORNING');
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
    api.schedulingRooms(day, shift).then(rows => { if (active) setRooms(rows); })
      .catch(error => { if (active) onError(error); })
      .finally(() => { if (active) setLoadingRooms(false); });
    return () => { active = false; };
  }, [day, shift, revision, onError]);

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
    if (rooms.some(room => room.id === roomId && room.occupied)) {
      setRoomId('');
      setLiveNotice('Phòng vừa được xếp. Vui lòng chọn phòng khác.');
    }
  }, [rooms, roomId, busy, loadingRooms]);

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
          }} required>
            <option value="">Chọn lớp</option>
            {classes.map(item => <option key={item.id} value={item.id}>{item.class_code} · {item.max_students} học viên</option>)}
          </select></label>
          <label>Ngày học<input type="date" value={day} min={classroom?.start_date}
            max={classroom?.end_date} disabled={!classroom} required
            onChange={event => { setLoadingRooms(Boolean(event.target.value)); changeSlot(setDay, event.target.value); }} /></label>
          <label>Ca học<select value={shift} onChange={event => changeSlot(setShift, event.target.value)}>
            <option value="MORNING">Ca sáng · 08:30–11:30</option>
            <option value="AFTERNOON">Ca chiều · 13:30–16:30</option>
            <option value="EVENING">Ca tối · 18:00–20:00</option>
          </select></label>
        </div>
        {day && loadingRooms && <p role="status">Đang tải phòng…</p>}
        {day && !loadingRooms && <fieldset>
          <legend>Phòng học</legend>
          {rooms.length === 0 && <p>Chưa có phòng để hiển thị.</p>}
          {rooms.map(room => {
            const reason = room.status !== 'AVAILABLE' ? 'Bảo trì'
              : room.capacity < classroom.max_students ? 'Không đủ sức chứa'
                : room.occupied ? 'Đã có lịch' : '';
            return <label className="check" key={room.id}>
              <input type="radio" name="room" checked={roomId === room.id}
                disabled={Boolean(reason)} onChange={() => { setRoomId(room.id); setSaved(false); setLiveNotice(''); }} />
              {room.name} · {room.capacity} chỗ · {reason || 'Trống'}
            </label>;
          })}
        </fieldset>}
        <button disabled={!classId || !day || !roomId || loadingRooms}>Xếp lớp vào phòng</button>
      </fieldset>
    </form>
    {saved && <p role="status">Đã xếp phòng. Buổi học đã có trên thời khóa biểu.</p>}
  </section>;
}
