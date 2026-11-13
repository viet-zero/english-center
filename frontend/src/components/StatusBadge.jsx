import React from 'react';

const labels = {
  UNPAID: ['Chưa thanh toán', 'error'],
  PARTIAL: ['Đóng một phần', 'warning'],
  PAID: ['Đã thanh toán', 'success'],
  PRESENT: ['Có mặt', 'success'],
  ABSENT: ['Vắng không phép', 'error'],
  EXCUSED: ['Vắng có phép', 'warning'],
  LATE: ['Đi muộn', 'warning'],
  UNMARKED: ['Chưa điểm danh', 'info'],
  UPCOMING: ['Sắp tới', 'info'],
  IN_PROGRESS: ['Đang diễn ra', 'warning'],
  ENDED: ['Đã kết thúc', 'success'],
};

export const attendanceLabels = Object.fromEntries(
  ['PRESENT', 'ABSENT', 'EXCUSED', 'LATE'].map(status => [status, labels[status][0]]),
);

export function examStatus(exam, now = new Date()) {
  const start = new Date(exam.starts_at.replace(' ', 'T'));
  const end = new Date(exam.ends_at.replace(' ', 'T'));
  if (now < start) return 'UPCOMING';
  return now < end ? 'IN_PROGRESS' : 'ENDED';
}

export default function StatusBadge({ status }) {
  const [label, tone] = labels[status] ?? ['Chưa xác định', 'info'];
  return <span className={'status-badge status-' + tone}>{label}</span>;
}
