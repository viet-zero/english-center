# Demo tích hợp English Center

Tạo một schema MySQL trống tên `english_center_demo` và chọn nó làm mặc định trong Workbench. Chạy `schema.sql` rồi `06-academics.sql`, sau đó đặt biến phiên MySQL `@demo_password_hash` thành hash BCrypt do bạn tạo cục bộ. Chạy `demo-seed.sql` trong cùng kết nối. Có thể tạo hash bằng `new BCryptPasswordEncoder().encode(password)` trong Java với thư viện Spring Security Crypto của dự án. Giữ mật khẩu và hash bên ngoài Git.

Mở PowerShell tại thư mục gốc dự án và chạy các lệnh dưới đây. Lệnh đầu đặt biến môi trường `DB_PASSWORD` cho phiên PowerShell hiện tại. Khi được hỏi, nhập mật khẩu MySQL của tài khoản cấu hình trong ứng dụng.

```powershell
$env:DB_PASSWORD = Read-Host -MaskInput 'Nhập mật khẩu MySQL'
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

Với schema cũ, chạy `06-academics.sql` đúng một lần trước khi khởi động bản mới. Hóa đơn cũ được cấp mã ngắn tự động. Có thể tra cứu và thu học phí bằng mã dạng HD1 hoặc UUID cũ.

Dùng `demo.teacher` để mở lớp được phân công, tạo lịch thi, điểm danh theo buổi và nhập điểm theo kỳ thi. Học viên thấy lịch thi và kết quả riêng trong mục Lớp của tôi. Phòng thi hiện là tên nhập trực tiếp và kiểm tra trùng giờ áp dụng trong cùng lớp.

Chạy thêm `07-learning.sql` đúng một lần để tạo bảng tài liệu và bài nộp. Sau khi nạp `demo-seed.sql`, chạy `demo-learning.sql` để thêm một tài liệu và hai bài tập vào lớp DEMO-GT01. Tài khoản học viên cần đăng ký lớp này trước khi xem hoặc nộp bài. Trong màn hình lớp, giảng viên có thể đăng nội dung và xem bài nộp. Học viên nộp hoặc cập nhật bài làm dạng văn bản.

Chạy thêm 08-exam-management.sql đúng một lần. Nhân viên đào tạo mở Lịch thi để cập nhật phòng, hình thức và giám thị của kỳ thi đã tạo.

Chạy 09-notifications.sql một lần trước khi sử dụng chuông thông báo học viên.
