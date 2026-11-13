# Demo tích hợp English Center

Tạo một schema MySQL trống tên `english_center_demo` và chọn nó làm mặc định trong Workbench. Chạy `schema.sql`, sau đó đặt biến phiên MySQL `@demo_password_hash` thành hash BCrypt do bạn tạo cục bộ. Chạy `demo-seed.sql` trong cùng kết nối. Có thể tạo hash bằng `new BCryptPasswordEncoder().encode(password)` trong Java với thư viện Spring Security Crypto của dự án. Giữ mật khẩu và hash bên ngoài Git.

Từ thư mục gốc dự án, đặt `DB_PASSWORD` bằng mật khẩu MySQL trên máy rồi chạy trong PowerShell.

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/english_center_demo?useSSL=false&allowPublicKeyRetrieval=true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8085'
.\mvnw.cmd spring-boot:run
```

Mở terminal khác trong thư mục frontend.

```powershell
npm ci
$env:BACKEND_URL = 'http://127.0.0.1:8085'
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

Mở http://127.0.0.1:5175/. Đăng nhập bằng `demo.student` và mật khẩu demo đã chọn. Chọn khóa học, học phần và đăng ký lớp. Sao chép mã hóa đơn rồi đăng nhập bằng `demo.accountant` để ghi nhận khoản thu mẫu. Đăng nhập lại bằng học viên và tra cứu hóa đơn để xem công nợ.

Bài test tích hợp cần schema demo vừa nạp seed. Đặt biến môi trường `DEMO_PASSWORD` bằng mật khẩu demo đã chọn rồi chạy `node database/demo-smoke.mjs` từ thư mục gốc. Bài test tạo đăng ký và giao dịch mẫu để kiểm tra tranh chỗ cuối, quyền xem hóa đơn, gửi đồng thời cùng mã tham chiếu và công nợ về 0. Dùng schema demo mới nếu cần chạy lại từ đầu.
