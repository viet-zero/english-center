import React, { useCallback, useEffect, useState } from 'react';
import { api } from '../services/api.js';

export default function NotificationBell() {
  const [inbox, setInbox] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async signal => {
    try {
      const next = await api.notifications(signal);
      if (!signal?.aborted) {
        setInbox(next);
        setError('');
      }
    } catch {
      if (!signal?.aborted) setError('Không tải được thông báo.');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    refresh(controller.signal);
    const timer = setInterval(() => refresh(controller.signal), 15000);
    return () => { controller.abort(); clearInterval(timer); };
  }, [refresh]);

  async function markRead(id) {
    setBusy(true);
    try {
      await api.markNotificationRead(id);
      await refresh();
    } catch {
      setError('Không đánh dấu được thông báo.');
    } finally {
      setBusy(false);
    }
  }

  return <details className="notifications" onToggle={event => {
    if (event.currentTarget.open) refresh();
  }}>
    <summary aria-label={'Thông báo, ' + (inbox?.unread ?? 0) + ' chưa đọc'}>
      🔔 {inbox?.unread > 0 && <strong>{inbox.unread}</strong>}
    </summary>
    <div className="notification-list">
      <strong>Thông báo</strong>
      {error && <div role="alert">{error}</div>}
      {!inbox && !error && <div>Đang tải…</div>}
      {inbox?.items.length === 0 && <div>Chưa có thông báo.</div>}
      {inbox?.items.map(item => <article key={item.id}>
        <div>{item.message}</div>
        <small>{item.created_at}</small>
        {item.is_read ? <div>Đã đọc</div> :
          <button className="outline" disabled={busy} onClick={() => markRead(item.id)}>Đánh dấu đã đọc</button>}
      </article>)}
    </div>
  </details>;
}
