export function passwordError(next, confirm) {
  if (next !== confirm) return 'Xác nhận mật khẩu không khớp.';
  if (next.length < 8 || new TextEncoder().encode(next).length > 72
    || !/[A-Z]/.test(next) || !/[a-z]/.test(next) || !/[0-9]/.test(next)) {
    return 'Mật khẩu cần ít nhất 8 ký tự, chữ hoa, chữ thường, chữ số và tối đa 72 byte UTF-8.';
  }
  return '';
}
