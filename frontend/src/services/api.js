export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(path, options) {
  const response = await fetch('/api' + path, {
    credentials: 'same-origin',
    cache: 'no-store',
    ...options,
  });
  if (!response.ok) {
    const messages = {
      400: 'Dữ liệu gửi lên chưa hợp lệ.',
      401: 'Phiên đăng nhập đã hết hạn hoặc thông tin đăng nhập không đúng.',
      403: 'Bạn không có quyền thực hiện thao tác này.',
      404: 'Không tìm thấy dữ liệu.',
      409: 'Dữ liệu đã thay đổi hoặc không đáp ứng điều kiện xử lý.',
    };
    const problem = await response.json().catch(() => null);
    const message = response.status === 409 && problem?.detail
      ? problem.detail
      : messages[response.status] ?? 'Máy chủ chưa xử lý được yêu cầu.';
    throw new ApiError(response.status, message);
  }
  return response.status === 204 ? undefined : response.json();
}

async function post(path, body, contentType) {
  const csrf = await request('/auth/csrf');
  const headers = { [csrf.headerName]: csrf.token };
  if (contentType) headers['Content-Type'] = contentType;
  return request(path, { method: 'POST', headers, body });
}

const id = encodeURIComponent;
const json = (path, data) => post(path, JSON.stringify(data), 'application/json');

export const api = {
  me: () => request('/auth/me'),
  async login(username, password) {
    await post('/auth/login', new URLSearchParams({ username, password }),
      'application/x-www-form-urlencoded');
    return request('/auth/me');
  },
  logout: () => post('/auth/logout'),
  courses: () => request('/catalog/courses'),
  modules: courseId => request('/catalog/courses/' + id(courseId) + '/modules'),
  classes: moduleId => request('/catalog/modules/' + id(moduleId) + '/classes'),
  enroll: classId => json('/enrollments', { classId }),
  invoice: invoiceId => request('/invoices/' + id(invoiceId)),
  pay: (invoiceId, payment) => json('/invoices/' + id(invoiceId) + '/payments', payment),
};
