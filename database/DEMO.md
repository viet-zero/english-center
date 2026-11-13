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

Mở http://127.0.0.1:5175/. Đăng nhập bằng `demo.student`, chọn khóa học, chọn lớp và bấm **Tạo mã QR payOS**. Học phần hiển thị theo lớp. Chỗ trống được kiểm tra lại khi payOS xác nhận tiền về.

Bài test tích hợp cần dữ liệu demo và lớp `demo-last-seat` còn đúng một chỗ. Trong terminal chạy Spring Boot, đặt `PAYOS_DEMO=true` và `PAYOS_CHECKSUM_KEY` bằng một khóa giả chỉ dùng cục bộ. Trong terminal test, đặt cùng `PAYOS_CHECKSUM_KEY` và `DEMO_PASSWORD`, rồi chạy `node database/demo-smoke.mjs`. Bài test gửi webhook có chữ ký qua web demo và tạo dữ liệu học phí, nên dùng schema demo mới nếu cần chạy lại từ đầu.

Với schema cũ, chạy `06-academics.sql` đúng một lần trước khi khởi động bản mới. Hóa đơn cũ được cấp mã ngắn tự động. Có thể tra cứu và thu học phí bằng mã dạng HD1 hoặc UUID cũ.

Dùng `demo.teacher` để mở lớp được phân công, tạo lịch thi, điểm danh theo buổi và nhập điểm theo kỳ thi. Học viên thấy lịch thi và kết quả riêng trong mục Lớp của tôi. Phòng thi hiện là tên nhập trực tiếp và kiểm tra trùng giờ áp dụng trong cùng lớp.

Chạy thêm `07-learning.sql` đúng một lần để tạo bảng tài liệu và bài nộp. Sau khi nạp `demo-seed.sql`, chạy `demo-learning.sql` để thêm một tài liệu và hai bài tập vào lớp DEMO-GT01. Tài khoản học viên cần đăng ký lớp này trước khi xem hoặc nộp bài. Trong màn hình lớp, giảng viên có thể đăng nội dung và xem bài nộp. Học viên nộp hoặc cập nhật bài làm dạng văn bản.

Chạy thêm 08-exam-management.sql đúng một lần. Nhân viên đào tạo mở Lịch thi để cập nhật phòng, hình thức và giám thị của kỳ thi đã tạo.

Chạy 09-notifications.sql một lần trước khi sử dụng chuông thông báo học viên.

Chạy 10-booking-staff-exam-invigilators.sql một lần sau các migration trước. Migration chuyển giám thị cũ sang bảng phân công nhiều người. Các buổi xếp trước khi có tính năng này chưa có mã người xếp. Có thể chạy demo-catalog.sql sau demo-seed.sql để tạo bốn khóa và lớp mẫu.

Học phí trong demo-catalog.sql là số liệu minh họa, theo từng học phần. Giá giao tiếp và IELTS tham khảo bảng giá của [Langmaster](https://langmaster.edu.vn/hoc-phi-1-khoa-hoc-tieng-anh-giao-tiep-bao-nhieu-tien). Giá TOEIC tham khảo [ZIM](https://zim.vn/toeic). Mức nền tảng A1 là giả định cho bản demo.

Chạy 11-seat-holds-attendance-alerts.sql một lần trước khi dùng bản mới. Chuông gửi một cảnh báo mỗi đăng ký khi có ít nhất ba buổi đã đến ngày học bị đánh dấu vắng không phép. Quản trị viên dùng Quản lý khóa học để tạo khóa, học phần và lớp.

## Thanh toán QR payOS

Chạy migration `12-payos.sql` một lần sau `11-seat-holds-attendance-alerts.sql`. Migration xóa dữ liệu giữ chỗ cũ và thêm bảng đơn, biên nhận payOS.

Để dùng payOS thật, cấu hình `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` và `PAYOS_RETURN_URL` trong môi trường backend. Đăng ký webhook HTTPS công khai tại đường dẫn `/api/payments/payos/webhook`. Trang quay về chỉ hiển thị trạng thái từ backend. Đơn được ghi danh sau khi webhook hợp lệ báo đúng số tiền. Nếu thiếu tiền hoặc lớp đã đầy, kế toán xem giao dịch trong **Giao dịch payOS cần đối soát** và xử lý khoản chuyển thủ công.