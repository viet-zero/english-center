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
## Nút thanh toán demo

Đặt PAYOS_DEMO=true khi chạy Spring Boot. Chế độ này dùng MySQL demo và không cần key payOS. Học viên chọn khóa học, lớp, bấm Thanh toán rồi Thanh toán demo. Backend lấy số tiền từ đơn thuộc học viên đăng nhập và chạy cùng nghiệp vụ xác nhận thanh toán. Bấm lặp dùng cùng mã giao dịch để tránh thu hoặc ghi danh hai lần. Khi lớp đầy, kế toán xem danh sách đối soát. Khi PAYOS_DEMO=false, endpoint thanh toán demo trả 404.

Kiểm thử hai thanh toán demo đồng thời bằng node database/demo-payment-race.mjs. Đặt DEMO_PASSWORD và ba biến DEMO_COURSE_ID, DEMO_MODULE_ID, DEMO_CLASS_ID trỏ đến khóa, học phần và lớp demo mới chỉ có một chỗ. Lớp cần một lịch học tại Phong 101 để khớp dữ liệu seed. Backend phải bật PAYOS_DEMO=true. Bài test tạo hai đơn, gửi hai yêu cầu thanh toán đồng thời, kiểm tra một người ghi danh, hóa đơn còn 0 đồng, giao dịch kia vào danh sách kế toán, gửi lặp và quyền truy cập.

## Kế toán xử lý giao dịch lớp đầy

Chạy 13-payment-review.sql một lần sau migration 12, trước khi chạy bản mới. Kế toán mở mục Đối soát và lịch sử giao dịch payOS, chọn chuyển sang lớp còn chỗ hoặc ghi nhận hoàn tiền demo, nhập ghi chú rồi xác nhận. Lớp nhận phải khác lớp gốc, cùng học phần và học phí bằng khoản đã nhận. Hệ thống kiểm tra lại sức chứa, điều kiện đăng ký và trùng lịch trong transaction. Ghi nhận hoàn tiền demo yêu cầu PAYOS_DEMO=true và đơn có link demo. Lịch sử lưu người xử lý, thời điểm, ghi chú, lớp nhận và hóa đơn. Đây là ghi nhận mô phỏng hoàn tiền, không thực hiện chuyển khoản ngân hàng.

Bài test database/demo-review-smoke.mjs kiểm tra lại hai giao dịch đã xử lý trên MySQL demo. Đặt DEMO_PASSWORD, DEMO_TRANSFER_REFERENCE, DEMO_REFUND_REFERENCE và DEMO_TARGET_CLASS_ID theo dữ liệu đã thử trên web, rồi chạy node database/demo-review-smoke.mjs. Tài khoản demo.student2 phải là người sở hữu hóa đơn chuyển lớp. Test kiểm tra phân quyền, gửi lặp đồng thời, chặn đổi quyết định và công nợ hóa đơn bằng 0.

## Quản lý tài khoản và đổi mật khẩu

Chạy 14-account-management.sql một lần trước khi khởi động bản mới. Admin mở Quản lý tài khoản để tìm theo tên đăng nhập/họ tên, sửa họ tên, điện thoại, email, CCCD, trạng thái và vai trò. Vai trò phải khớp hồ sơ học viên/nhân viên đã tạo. Hệ thống giữ ít nhất một admin hoạt động và từ chối lưu phiên bản chỉnh sửa đã cũ.

Cả năm vai trò có mục Đổi mật khẩu. Nhập mật khẩu hiện tại, mật khẩu mới và xác nhận. Mật khẩu mới có ít nhất 8 ký tự, chữ hoa, chữ thường, chữ số và tối đa 72 byte UTF-8. Đổi mật khẩu yêu cầu đăng nhập lại. Khóa tài khoản, đổi quyền hoặc lưu thông tin tài khoản làm phiên cũ hết hiệu lực khi gửi yêu cầu tiếp theo, kể cả sau khi tài khoản được mở lại. Phiên cũ từ bản ứng dụng trước cũng phải đăng nhập lại.

Đặt DEMO_PASSWORD bằng mật khẩu tài khoản demo.admin rồi chạy node database/demo-account-smoke.mjs. Test tạo riêng tài khoản nhân viên QA, kiểm tra danh sách, sửa, quyền, khóa/mở, phiên đồng thời, đổi mật khẩu, bản sửa cũ, giữ admin cuối và dữ liệu không hợp lệ.

## Hủy lớp, bảo lưu và hoàn tiền demo

Chạy migration 15-student-requests.sql một lần sau migration 14. Học viên mở Hủy / Bảo lưu, chọn đăng ký đã thanh toán đủ, loại yêu cầu và lý do. Chỉ được gửi khi lớp đã có lịch và số buổi đã kết thúc không quá 50% tổng buổi không bị hủy. Đào tạo mở Duyệt yêu cầu học viên để duyệt hoặc từ chối, bắt buộc có ghi chú. Hệ thống kiểm tra lại điều kiện tại thời điểm duyệt. Mỗi đăng ký có tối đa một yêu cầu chờ duyệt.

Chính sách demo tính giá trị còn lại bằng số tiền thực đóng nhân số buổi còn lại chia tổng buổi, làm tròn xuống đồng. Số buổi đã học được xác định bằng thời điểm kết thúc buổi trên lịch, kể cả khi học viên vắng mặt. Duyệt hủy đặt đăng ký thành CANCELLED và chuyển khoản được hoàn sang kế toán. Duyệt bảo lưu đặt ON_HOLD, lưu giá trị bảo lưu và giải phóng chỗ. Học viên hết quyền xem nội dung lớp đã hủy/bảo lưu. Khôi phục học lại từ bảo lưu là nghiệp vụ tiếp theo.

Kế toán mở Hoàn tiền yêu cầu hủy để xác nhận hoàn tiền demo cho yêu cầu hủy đã duyệt, với ghi chú/chứng từ demo. Cần PAYOS_DEMO=true. Hệ thống không chuyển tiền ngân hàng. Sổ hoàn tiền lưu riêng, giữ nguyên hóa đơn thu gốc. Hóa đơn hiển thị thêm số tiền đã hoàn demo và giá trị bảo lưu. Gửi lặp hoặc xác nhận đồng thời không sinh hai khoản hoàn.

Kiểm thử node database/demo-student-requests.mjs cần DEMO_PASSWORD, DEMO_REQUEST_PREFIX và tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị bốn khóa/học phần/lớp demo mới, ID và mã lớp là prefix nối c, h, r, p; học phí 10000, sức chứa 2. Mỗi lớp có hai buổi, ba lớp c/h/r có một buổi đã kết thúc và một buổi tương lai, lớp p có cả hai buổi đã kết thúc. Cần lịch không trùng đăng ký khác của demo.student. Bài test thanh toán demo rồi kiểm tra hủy, bảo lưu, từ chối, vượt 50%, quyền sở hữu, gửi trùng, hoàn đồng thời, số chỗ và số tiền trên hóa đơn.

## Kết quả cuối khóa và chứng chỉ

Chạy migration 16-completion-certificates.sql sau migration 15. Đào tạo mở Chốt kết quả cuối khóa, chọn lớp và cấu hình điểm đạt tối thiểu, tỷ lệ chuyên cần và hệ số từng kỳ thi. Mặc định demo là điểm 5/10, chuyên cần 80%, hệ số 1. Giảng viên xem điểm tổng kết của lớp mình. Điểm tổng kết học phần là tổng (điểm thi nhân hệ số) chia tổng hệ số, làm tròn hai chữ số. Chuyên cần tính PRESENT và LATE trên toàn bộ buổi không bị hủy; EXCUSED và ABSENT không cộng số buổi tham dự.

Chỉ chốt khi lớp đến ngày kết thúc, tất cả buổi/kỳ thi đã kết thúc, có ít nhất một buổi và một kỳ thi, và từng học viên đang học đã được nhập đủ điểm và điểm danh. Chốt khóa điểm, điểm danh, hệ số và lịch của lớp. Học viên không đạt nhận kết quả FAIL. Đạt học tập nhưng còn thiếu học phí nhận FINANCIAL_HOLD; sau khi kế toán thu đủ, đào tạo bấm Kiểm tra và cấp chứng chỉ để xác nhận hoàn thành học phần.

Chứng chỉ khóa học chỉ phát sinh khi tất cả học phần ACTIVE của khóa đã có kết quả PASS/đăng ký COMPLETED của học viên, có đủ tiền thực thu và chưa hoàn tiền. Điểm trên chứng chỉ là trung bình điểm học phần theo thời lượng giờ đã lưu khi chốt; chuyên cần trên chứng chỉ tính tổng buổi tham dự/tổng buổi. Một học viên có một chứng chỉ cho mỗi khóa học. PDF lưu cố định trong MySQL cùng thông tin tại ngày cấp. Học viên vào Kết quả và chứng chỉ để tải PDF của mình.

CERTIFICATES_PUBLIC_BASE_URL mặc định http://127.0.0.1:5175, tạo URL tra cứu trong QR. Đặt thành địa chỉ web mà điện thoại truy cập được khi triển khai. Quét QR mở trang tra cứu công khai, chỉ hiện mã, tên học viên, tên khóa và ngày cấp. Tải PDF vẫn yêu cầu tài khoản học viên sở hữu. PDF dùng Noto Sans với giấy phép kèm trong src/main/resources/fonts/LICENSE.

Chạy node database/demo-completion-smoke.mjs với DEMO_PASSWORD, DEMO_CERTIFICATE_PREFIX và tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị lớp mới có ID/mã prefix nối a/b/c/d, hai buổi và hai kỳ thi đã kết thúc cho mỗi lớp. Mỗi học phần học phí 10000, thời lượng 24 giờ. a,b,c thuộc ba khóa riêng, d là học phần thứ hai của khóa c; các lớp có giáo viên demo-teacher và sức chứa ít nhất 2. Lớp b cần sẵn đăng ký chưa trả của demo-student, ID prefix+b-e, hóa đơn prefix+b-i. Lịch không trùng các lớp đã có của tài khoản demo. Test kiểm tra hệ số, thiếu dữ liệu, chuyên cần, công nợ, đủ học phần, khóa sửa, chốt đồng thời, quyền tải PDF và trang tra cứu. CertificatePdfTest render PDF và giải mã QR từ chính trang PDF.

## Chỗ điền key payOS cho thành viên nhóm

Copy config/payos.example.ps1 thành config/payos.local.ps1 và điền PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY từ cùng kênh thanh toán. File local được .gitignore loại khỏi Git. Chạy . .\config\payos.local.ps1 trong PowerShell sẽ dùng để chạy backend. Đổi PAYOS_DEMO=false khi muốn sử dụng payOS thật, cập nhật PAYOS_RETURN_URL và đăng ký webhook https://TEN_MIEN_BACKEND/api/payments/payos/webhook. Kênh thanh toán do thành viên nhóm sở hữu sẽ nhận tiền về tài khoản ngân hàng liên kết của thành viên đó.

## Ca thu tiền và báo cáo doanh thu

Chạy 17-cash-shifts.sql sau migration 16. Kế toán mở Ca thu tiền và nhập tiền mặt đầu ca. Mỗi người chỉ có một ca OPEN. Thu tại quầy và hoàn tiền demo cần ca đang mở; thanh toán payOS tự động hoạt động độc lập với ca nhân viên. Thu và hoàn tiền mặt được gắn vào ca. Hoàn yêu cầu hủy có thể chọn CASH hoặc BANK_TRANSFER; hoàn giao dịch lớp đầy là BANK_TRANSFER. Ca chỉ tính tiền mặt của chính kế toán đăng nhập. Tiền mặt theo hệ thống = đầu ca + thu tiền mặt - hoàn tiền mặt. Chốt nhập tiền thực nộp, chênh lệch khác 0 bắt buộc ghi lý do. Số liệu ca đã chốt được lưu cố định và không sửa. Mở/chốt/thu/hoàn được khóa trong transaction để tránh ghi thêm vào ca đã đóng. Đăng xuất khi ca chưa chốt có cảnh báo.

Báo cáo doanh thu tính dòng tiền theo ngày thu hoặc hoàn, khoảng ngày gồm cả ngày đầu và ngày cuối. Tổng tiền nhận gồm các giao dịch thành công tại quầy và các biên nhận payOS, bao gồm tiền nhận đang chờ đối soát. Thu ròng = tổng tiền nhận - hoàn tiền yêu cầu hủy - hoàn tiền giao dịch lớp đầy trong kỳ. Chuyển khoản đã ghi nhận trong biên nhận payOS không bị cộng lại khi kế toán chuyển lớp và tạo hóa đơn. Khoản nhận trong kỳ hiện chờ đối soát được hiển thị riêng. Tiền đầu ca và chênh lệch quỹ không tính vào doanh thu. Hoàn ở kỳ khác làm giảm thu ròng ở kỳ hoàn. Dữ liệu cũ chưa gắn ca vẫn thuộc báo cáo nhưng không được tự gán vào ca mới; hoàn demo cũ mặc định chuyển khoản.

Chạy node database/demo-cash-shifts.mjs với DEMO_PASSWORD, DEMO_CASH_PREFIX, tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị lớp/khóa/học phần ID và mã prefix nối c,b,r,p,q; học phí c/r/p/q=10000, b=20000. c/b/r có đăng ký demo-student ID lớp+e và hóa đơn chưa trả ID lớp+i, hai buổi gồm một quá khứ và một tương lai. p/q sức chứa một, còn trống, không có lịch trùng. Thêm lớp prefix+target cùng học phần p để chuyển lớp. Test kiểm tra mở/chốt lặp, chưa mở ca, thu tiền mặt/chuyển khoản, hoàn cả hai nguồn, thu ròng, không đếm trùng khi chuyển lớp, lý do chênh lệch, quyền sở hữu và thu đồng thời chốt ca.
