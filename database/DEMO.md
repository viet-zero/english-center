# Demo tích hợp English Center

## Chạy chung bằng một file trên Windows

Bật dịch vụ MySQL và chuẩn bị schema demo cùng các migration trước. Nhấp đúp `start-demo.cmd` ở thư mục gốc dự án (hoặc chạy `.\start-demo.cmd` trong terminal). Nhập mật khẩu MySQL khi được hỏi; mật khẩu không được lưu vào file.

Script chọn JDK từ 21 trở lên, bật Spring Boot ở 8085 với SePay demo, bật React ở 5175 rồi mở trình duyệt. Nếu ứng dụng đã chạy thì dùng lại, không bật trùng. Log nằm trong `%TEMP%\english-center-demo`. Backend và frontend chạy nền sau khi cửa sổ script đóng; script không tự tạo hay xóa dữ liệu MySQL.

Frontend mặc định nối backend `http://127.0.0.1:8085`, không cần đặt `BACKEND_URL`. Biến này vẫn dùng được khi bạn muốn đổi backend khác.


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
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

Mở http://127.0.0.1:5175/. Đăng nhập bằng `demo.student`, chọn khóa học, chọn lớp và bấm **Tạo mã QR SePay**. Học phần hiển thị theo lớp. Chỗ trống được kiểm tra lại khi SePay xác nhận tiền về.

Bài test tích hợp cần dữ liệu demo và lớp `demo-last-seat` còn đúng một chỗ. Trong terminal chạy Spring Boot, đặt `SEPAY_DEMO=true` và `SEPAY_WEBHOOK_API_KEY` bằng một khóa giả chỉ dùng cục bộ. Trong terminal test, đặt cùng `SEPAY_WEBHOOK_API_KEY` và `DEMO_PASSWORD`, rồi chạy `node database/demo-smoke.mjs`. Bài test gửi webhook có xác thực API Key qua web demo và tạo dữ liệu học phí, nên dùng schema demo mới nếu cần chạy lại từ đầu.

Với schema cũ, chạy `06-academics.sql` đúng một lần trước khi khởi động bản mới. Hóa đơn cũ được cấp mã ngắn tự động. Có thể tra cứu và thu học phí bằng mã dạng HD1 hoặc UUID cũ.

Dùng `demo.teacher` để mở lớp được phân công, tạo lịch thi, điểm danh theo buổi và nhập điểm theo kỳ thi. Học viên thấy lịch thi và kết quả riêng trong mục Lớp của tôi. Phòng thi hiện là tên nhập trực tiếp và kiểm tra trùng giờ áp dụng trong cùng lớp.

Chạy thêm `07-learning.sql` đúng một lần để tạo bảng tài liệu và bài nộp. Sau khi nạp `demo-seed.sql`, chạy `demo-learning.sql` để thêm một tài liệu và hai bài tập vào lớp DEMO-GT01. Tài khoản học viên cần đăng ký lớp này trước khi xem hoặc nộp bài. Trong màn hình lớp, giảng viên có thể đăng nội dung và xem bài nộp. Học viên nộp hoặc cập nhật bài làm dạng văn bản.

Chạy thêm 08-exam-management.sql đúng một lần. Nhân viên đào tạo mở Lịch thi để cập nhật phòng, hình thức và giám thị của kỳ thi đã tạo.

Chạy 09-notifications.sql một lần trước khi sử dụng chuông thông báo học viên.

Chạy 10-booking-staff-exam-invigilators.sql một lần sau các migration trước. Migration chuyển giám thị cũ sang bảng phân công nhiều người. Các buổi xếp trước khi có tính năng này chưa có mã người xếp. Có thể chạy demo-catalog.sql sau demo-seed.sql để tạo bốn khóa và lớp mẫu.

Học phí trong demo-catalog.sql là số liệu minh họa, theo từng học phần. Giá giao tiếp và IELTS tham khảo bảng giá của [Langmaster](https://langmaster.edu.vn/hoc-phi-1-khoa-hoc-tieng-anh-giao-tiep-bao-nhieu-tien). Giá TOEIC tham khảo [ZIM](https://zim.vn/toeic). Mức nền tảng A1 là giả định cho bản demo.

Chạy 11-seat-holds-attendance-alerts.sql một lần trước khi dùng bản mới. Chuông gửi một cảnh báo mỗi đăng ký khi có ít nhất ba buổi đã đến ngày học bị đánh dấu vắng không phép. Quản trị viên dùng Quản lý khóa học để tạo khóa, học phần và lớp.

## Thanh toán QR SePay

Chạy migration 18-sepay.sql sau migration 17. Migration đổi tên bảng đơn/biên nhận thành don_thanh_toan và bien_nhan_thanh_toan, giữ nguyên các khoản thu và đối soát cũ với provider PAYOS. Đơn mới dùng SEPAY. Đơn payOS đang chờ cần tạo lại bằng SePay. Các migration 12-payos.sql và 13-payment-review.sql giữ nguyên tên để áp dụng theo thứ tự lịch sử.

Copy config/sepay.example.ps1 thành config/sepay.local.ps1. Điền SEPAY_WEBHOOK_API_KEY, SEPAY_BANK và SEPAY_ACCOUNT_NUMBER. File local đã được .gitignore loại khỏi Git. Chạy . ./config/sepay.local.ps1 trong PowerShell dùng để chạy Spring Boot. Bật SEPAY_DEMO=true khi thử cục bộ, đặt false khi dùng SePay thật.

Trên SePay tạo webhook tiền vào, định dạng JSON, chọn đúng tài khoản nhận. Chọn xác thực API Key và dùng cùng khóa SEPAY_WEBHOOK_API_KEY. Đăng ký URL HTTPS công khai /api/payments/sepay/webhook, cấu hình tiền tố mã thanh toán EC và hậu tố số. Bật retry khi endpoint trả lỗi ngoài 2xx. Backend trả 201 và {"success":true}. Khóa webhook được chọn trong cấu hình webhook, không phải API Token có quyền truy vấn toàn bộ giao dịch.

QR tạo theo https://vietqr.app/img gồm ngân hàng, tài khoản, số tiền và nội dung EC + mã đơn. API Token truy vấn giao dịch không cần cho luồng này. Chỉ webhook hợp lệ, đúng tài khoản và đúng số tiền mới ghi danh. Giao dịch thiếu/thừa tiền hoặc lớp đầy vào danh sách đối soát; cùng ID SePay gửi lại không thu/ghi danh lần nữa. Payload không khớp mã đơn được lưu trong danh sách SePay chưa khớp đơn của kế toán. Giao dịch tiền ra được ghi nhận là IGNORED và không ghi danh.
## Nút thanh toán demo

Đặt SEPAY_DEMO=true khi chạy Spring Boot. Chế độ này dùng MySQL demo và không cần tài khoản SePay. Học viên chọn khóa học, lớp, bấm Thanh toán rồi Thanh toán demo. Backend lấy số tiền từ đơn thuộc học viên đăng nhập và chạy cùng nghiệp vụ xác nhận thanh toán. Bấm lặp dùng cùng mã giao dịch để tránh thu hoặc ghi danh hai lần. Khi lớp đầy, kế toán xem danh sách đối soát. Khi SEPAY_DEMO=false, endpoint thanh toán demo trả 404.

Kiểm thử hai thanh toán demo đồng thời bằng node database/demo-payment-race.mjs. Đặt DEMO_PASSWORD và ba biến DEMO_COURSE_ID, DEMO_MODULE_ID, DEMO_CLASS_ID trỏ đến khóa, học phần và lớp demo mới chỉ có một chỗ. Lớp cần một lịch học tại Phong 101 để khớp dữ liệu seed. Backend phải bật SEPAY_DEMO=true. Bài test tạo hai đơn, gửi hai yêu cầu thanh toán đồng thời, kiểm tra một người ghi danh, hóa đơn còn 0 đồng, giao dịch kia vào danh sách kế toán, gửi lặp và quyền truy cập.

## Kế toán xử lý giao dịch lớp đầy

Chạy 13-payment-review.sql một lần sau migration 12, trước khi chạy bản mới. Kế toán mở mục Đối soát và lịch sử giao dịch thanh toán, chọn chuyển sang lớp còn chỗ hoặc ghi nhận hoàn tiền demo, nhập ghi chú rồi xác nhận. Lớp nhận phải khác lớp gốc, cùng học phần và học phí bằng khoản đã nhận. Hệ thống kiểm tra lại sức chứa, điều kiện đăng ký và trùng lịch trong transaction. Ghi nhận hoàn tiền demo yêu cầu SEPAY_DEMO=true và đơn có link demo. Lịch sử lưu người xử lý, thời điểm, ghi chú, lớp nhận và hóa đơn. Đây là ghi nhận mô phỏng hoàn tiền, không thực hiện chuyển khoản ngân hàng.

Bài test database/demo-review-smoke.mjs kiểm tra lại hai giao dịch đã xử lý trên MySQL demo. Đặt DEMO_PASSWORD, DEMO_TRANSFER_REFERENCE, DEMO_REFUND_REFERENCE và DEMO_TARGET_CLASS_ID theo dữ liệu đã thử trên web, rồi chạy node database/demo-review-smoke.mjs. Tài khoản demo.student2 phải là người sở hữu hóa đơn chuyển lớp. Test kiểm tra phân quyền, gửi lặp đồng thời, chặn đổi quyết định và công nợ hóa đơn bằng 0.

## Quản lý tài khoản và đổi mật khẩu

Chạy 14-account-management.sql một lần trước khi khởi động bản mới. Admin mở Quản lý tài khoản để tìm theo tên đăng nhập/họ tên, sửa họ tên, điện thoại, email, CCCD, trạng thái và vai trò. Vai trò phải khớp hồ sơ học viên/nhân viên đã tạo. Hệ thống giữ ít nhất một admin hoạt động và từ chối lưu phiên bản chỉnh sửa đã cũ.

Cả năm vai trò có mục Đổi mật khẩu. Nhập mật khẩu hiện tại, mật khẩu mới và xác nhận. Mật khẩu mới có ít nhất 8 ký tự, chữ hoa, chữ thường, chữ số và tối đa 72 byte UTF-8. Đổi mật khẩu yêu cầu đăng nhập lại. Khóa tài khoản, đổi quyền hoặc lưu thông tin tài khoản làm phiên cũ hết hiệu lực khi gửi yêu cầu tiếp theo, kể cả sau khi tài khoản được mở lại. Phiên cũ từ bản ứng dụng trước cũng phải đăng nhập lại.

Đặt DEMO_PASSWORD bằng mật khẩu tài khoản demo.admin rồi chạy node database/demo-account-smoke.mjs. Test tạo riêng tài khoản nhân viên QA, kiểm tra danh sách, sửa, quyền, khóa/mở, phiên đồng thời, đổi mật khẩu, bản sửa cũ, giữ admin cuối và dữ liệu không hợp lệ.

## Hủy lớp, bảo lưu và hoàn tiền demo

Chạy migration 15-student-requests.sql một lần sau migration 14. Học viên mở Hủy / Bảo lưu, chọn đăng ký đã thanh toán đủ, loại yêu cầu và lý do. Chỉ được gửi khi lớp đã có lịch và số buổi đã kết thúc không quá 50% tổng buổi không bị hủy. Đào tạo mở Duyệt yêu cầu học viên để duyệt hoặc từ chối, bắt buộc có ghi chú. Hệ thống kiểm tra lại điều kiện tại thời điểm duyệt. Mỗi đăng ký có tối đa một yêu cầu chờ duyệt.

Chính sách demo tính giá trị còn lại bằng số tiền thực đóng nhân số buổi còn lại chia tổng buổi, làm tròn xuống đồng. Số buổi đã học được xác định bằng thời điểm kết thúc buổi trên lịch, kể cả khi học viên vắng mặt. Duyệt hủy đặt đăng ký thành CANCELLED và chuyển khoản được hoàn sang kế toán. Duyệt bảo lưu đặt ON_HOLD, lưu giá trị bảo lưu và giải phóng chỗ. Học viên hết quyền xem nội dung lớp đã hủy/bảo lưu. Khôi phục học lại từ bảo lưu là nghiệp vụ tiếp theo.

Kế toán mở Hoàn tiền yêu cầu hủy để xác nhận hoàn tiền demo cho yêu cầu hủy đã duyệt, với ghi chú/chứng từ demo. Cần SEPAY_DEMO=true. Hệ thống không chuyển tiền ngân hàng. Sổ hoàn tiền lưu riêng, giữ nguyên hóa đơn thu gốc. Hóa đơn hiển thị thêm số tiền đã hoàn demo và giá trị bảo lưu. Gửi lặp hoặc xác nhận đồng thời không sinh hai khoản hoàn.

Kiểm thử node database/demo-student-requests.mjs cần DEMO_PASSWORD, DEMO_REQUEST_PREFIX và tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị bốn khóa/học phần/lớp demo mới, ID và mã lớp là prefix nối c, h, r, p; học phí 10000, sức chứa 2. Mỗi lớp có hai buổi, ba lớp c/h/r có một buổi đã kết thúc và một buổi tương lai, lớp p có cả hai buổi đã kết thúc. Cần lịch không trùng đăng ký khác của demo.student. Bài test thanh toán demo rồi kiểm tra hủy, bảo lưu, từ chối, vượt 50%, quyền sở hữu, gửi trùng, hoàn đồng thời, số chỗ và số tiền trên hóa đơn.

## Kết quả cuối khóa và chứng chỉ

Chạy migration 16-completion-certificates.sql sau migration 15. Đào tạo mở Chốt kết quả cuối khóa, chọn lớp và cấu hình điểm đạt tối thiểu, tỷ lệ chuyên cần và hệ số từng kỳ thi. Mặc định demo là điểm 5/10, chuyên cần 80%, hệ số 1. Giảng viên xem điểm tổng kết của lớp mình. Điểm tổng kết học phần là tổng (điểm thi nhân hệ số) chia tổng hệ số, làm tròn hai chữ số. Chuyên cần tính PRESENT và LATE trên toàn bộ buổi không bị hủy; EXCUSED và ABSENT không cộng số buổi tham dự.

Chỉ chốt khi lớp đến ngày kết thúc, tất cả buổi/kỳ thi đã kết thúc, có ít nhất một buổi và một kỳ thi, và từng học viên đang học đã được nhập đủ điểm và điểm danh. Chốt khóa điểm, điểm danh, hệ số và lịch của lớp. Học viên không đạt nhận kết quả FAIL. Đạt học tập nhưng còn thiếu học phí nhận FINANCIAL_HOLD; sau khi kế toán thu đủ, đào tạo bấm Kiểm tra và cấp chứng chỉ để xác nhận hoàn thành học phần.

Chứng chỉ khóa học chỉ phát sinh khi tất cả học phần ACTIVE của khóa đã có kết quả PASS/đăng ký COMPLETED của học viên, có đủ tiền thực thu và chưa hoàn tiền. Điểm trên chứng chỉ là trung bình điểm học phần theo thời lượng giờ đã lưu khi chốt; chuyên cần trên chứng chỉ tính tổng buổi tham dự/tổng buổi. Một học viên có một chứng chỉ cho mỗi khóa học. PDF lưu cố định trong MySQL cùng thông tin tại ngày cấp. Học viên vào Kết quả và chứng chỉ để tải PDF của mình.

CERTIFICATES_PUBLIC_BASE_URL mặc định http://127.0.0.1:5175, tạo URL tra cứu trong QR. Đặt thành địa chỉ web mà điện thoại truy cập được khi triển khai. Quét QR mở trang tra cứu công khai, chỉ hiện mã, tên học viên, tên khóa và ngày cấp. Tải PDF vẫn yêu cầu tài khoản học viên sở hữu. PDF dùng Noto Sans với giấy phép kèm trong src/main/resources/fonts/LICENSE.

Chạy node database/demo-completion-smoke.mjs với DEMO_PASSWORD, DEMO_CERTIFICATE_PREFIX và tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị lớp mới có ID/mã prefix nối a/b/c/d, hai buổi và hai kỳ thi đã kết thúc cho mỗi lớp. Mỗi học phần học phí 10000, thời lượng 24 giờ. a,b,c thuộc ba khóa riêng, d là học phần thứ hai của khóa c; các lớp có giáo viên demo-teacher và sức chứa ít nhất 2. Lớp b cần sẵn đăng ký chưa trả của demo-student, ID prefix+b-e, hóa đơn prefix+b-i. Lịch không trùng các lớp đã có của tài khoản demo. Test kiểm tra hệ số, thiếu dữ liệu, chuyên cần, công nợ, đủ học phần, khóa sửa, chốt đồng thời, quyền tải PDF và trang tra cứu. CertificatePdfTest render PDF và giải mã QR từ chính trang PDF.

## Ca thu tiền và báo cáo doanh thu

Chạy 17-cash-shifts.sql sau migration 16. Kế toán mở Ca thu tiền và nhập tiền mặt đầu ca. Mỗi người chỉ có một ca OPEN. Thu tại quầy và hoàn tiền demo cần ca đang mở; thanh toán SePay tự động hoạt động độc lập với ca nhân viên. Thu và hoàn tiền mặt được gắn vào ca. Hoàn yêu cầu hủy có thể chọn CASH hoặc BANK_TRANSFER; hoàn giao dịch lớp đầy là BANK_TRANSFER. Ca chỉ tính tiền mặt của chính kế toán đăng nhập. Tiền mặt theo hệ thống = đầu ca + thu tiền mặt - hoàn tiền mặt. Chốt nhập tiền thực nộp, chênh lệch khác 0 bắt buộc ghi lý do. Số liệu ca đã chốt được lưu cố định và không sửa. Mở/chốt/thu/hoàn được khóa trong transaction để tránh ghi thêm vào ca đã đóng. Đăng xuất khi ca chưa chốt có cảnh báo.

Báo cáo doanh thu tính dòng tiền theo ngày thu hoặc hoàn, khoảng ngày gồm cả ngày đầu và ngày cuối. Tổng tiền nhận gồm các giao dịch thành công tại quầy và các biên nhận thanh toán, bao gồm tiền nhận đang chờ đối soát. Thu ròng = tổng tiền nhận - hoàn tiền yêu cầu hủy - hoàn tiền giao dịch lớp đầy trong kỳ. Chuyển khoản đã ghi nhận trong biên nhận thanh toán không bị cộng lại khi kế toán chuyển lớp và tạo hóa đơn. Khoản nhận trong kỳ hiện chờ đối soát được hiển thị riêng. Tiền đầu ca và chênh lệch quỹ không tính vào doanh thu. Hoàn ở kỳ khác làm giảm thu ròng ở kỳ hoàn. Dữ liệu cũ chưa gắn ca vẫn thuộc báo cáo nhưng không được tự gán vào ca mới; hoàn demo cũ mặc định chuyển khoản.

Chạy node database/demo-cash-shifts.mjs với DEMO_PASSWORD, DEMO_CASH_PREFIX, tùy chọn DEMO_TRAINING_USERNAME. Chuẩn bị lớp/khóa/học phần ID và mã prefix nối c,b,r,p,q; học phí c/r/p/q=10000, b=20000. c/b/r có đăng ký demo-student ID lớp+e và hóa đơn chưa trả ID lớp+i, hai buổi gồm một quá khứ và một tương lai. p/q sức chứa một, còn trống, không có lịch trùng. Thêm lớp prefix+target cùng học phần p để chuyển lớp. Test kiểm tra mở/chốt lặp, chưa mở ca, thu tiền mặt/chuyển khoản, hoàn cả hai nguồn, thu ròng, không đếm trùng khi chuyển lớp, lý do chênh lệch, quyền sở hữu và thu đồng thời chốt ca.

Bài kiểm thử SePay đầy đủ chạy bằng node database/demo-sepay-smoke.mjs, với DEMO_PASSWORD, SEPAY_WEBHOOK_API_KEY và DEMO_COURSE_ID/DEMO_MODULE_ID/DEMO_CLASS_ID của một lớp demo mới còn một chỗ. Backend cần SEPAY_DEMO=true và cùng SEPAY_WEBHOOK_API_KEY giả dùng cục bộ. Bài test gửi webhook qua HTTP, kiểm tra tiền ra, tài khoản sai, key sai, mã không khớp, nội dung có nhiều mã, code=null, thiếu tiền, tranh chỗ cuối, gửi lặp và thay đổi dữ liệu trên ID cũ. Bật demo cục bộ không cần key để dùng nút Thanh toán demo, nhưng webhook bên ngoài luôn cần key. Chỉ giao dịch PROCESSED mới có thể ghi danh. Giao dịch UNMATCHED hiển thị riêng ở kế toán và được tính là khoản nhận đang chờ đối soát trong báo cáo dòng tiền, không tạo hóa đơn hoặc đăng ký.

Chưa kiểm thử kết nối SePay/ngân hàng thật khi chưa có cấu hình của nhóm. Chế độ SEPAY_DEMO là giả lập trong đồ án; SePay Test Mode là môi trường thử riêng trên tài khoản SePay, cần cấu hình webhook/API Key đúng như môi trường thật.

## Quản lý danh mục đào tạo

Nhân viên đào tạo mở Danh mục đào tạo, admin mở Quản lý khóa học. Hai vai trò dùng chung màn hình để tìm khóa học, thêm/sửa tên, mô tả và trạng thái khóa; thêm học phần theo thứ tự, sửa tên, học phí, thời lượng và trạng thái học phần; thêm lớp với giảng viên, ngày học và sức chứa, sửa sĩ số tối đa và trạng thái lớp. Lớp đã chốt kết quả hoặc đã hủy hiển thị khóa chỉnh sửa. Sau khi tạo lớp, nhân viên đào tạo tiếp tục xếp phòng và lịch học trong phân hệ Xếp phòng.

Ngừng mở khóa hoặc học phần sẽ ẩn khỏi danh mục đăng ký mới. Hóa đơn cũ lưu học phí áp dụng lúc đăng ký. Backend chặn trùng tên khóa, thứ tự học phần, mã lớp, ngày kết thúc trước ngày bắt đầu và sĩ số thấp hơn số học viên đã đăng ký.

Đặt DEMO_PASSWORD rồi chạy node database/demo-catalog-smoke.mjs để kiểm tra bằng tài khoản demo.training.mweokbew (hoặc DEMO_TRAINING_USERNAME), demo.admin, demo.student và demo.student2. Cần SEPAY_DEMO=true để kiểm thử hai đăng ký thanh toán demo. Bài test tạo danh mục riêng, kiểm tra thêm/sửa, phân quyền, dữ liệu không hợp lệ, sức chứa, hiển thị danh mục ngừng mở và học phí trên hóa đơn đã tạo.

## Quên mật khẩu và khóa sau 5 lần sai

Chạy migration 19-password-recovery.sql sau migration 18. Nạp config/auth-demo.example.ps1 vào terminal Spring Boot để bật AUTH_EMAIL_DEMO=true. AUTH_RESET_BASE_URL là URL React mà người kiểm thử mở được. Cập nhật email trong Quản lý tài khoản hoặc nhập Email khôi phục khi tạo tài khoản.

Nhập sai mật khẩu liên tiếp 5 lần sẽ đặt LOCKED với lý do FAILED_ATTEMPTS và tăng auth_version để phiên cũ hết hiệu lực ở yêu cầu tiếp theo. Đăng nhập thành công, đổi mật khẩu thành công hoặc admin mở khóa sẽ đặt lại bộ đếm. Tài khoản không tồn tại và tài khoản bị khóa đều nhận thông báo đăng nhập chung.

Bấm Quên mật khẩu ở màn hình đăng nhập và nhập email. Phản hồi luôn giống nhau cho email đã đăng ký hoặc chưa đăng ký. Admin mở Email khôi phục demo để đọc email giả lập và mở liên kết. Token được tạo bằng SecureRandom 256 bit, chỉ lưu SHA-256 trong password_reset, có hiệu lực 5 phút và dùng một lần. Yêu cầu mới thay thế token cũ; đổi mật khẩu hoặc admin chỉnh tài khoản làm token đang chờ hết hiệu lực. Liên kết mở lại được tài khoản tự khóa do nhập sai, còn tài khoản admin khóa phải được admin mở khóa. Đặt lại mật khẩu yêu cầu mật khẩu mới khác mật khẩu cũ, ít nhất 8 ký tự, có chữ hoa/chữ thường/chữ số và tối đa 72 byte UTF-8.

Email giả lập lưu liên kết trong demo_email_outbox và chỉ admin đọc được khi chế độ demo bật. AUTH_EMAIL_DEMO=false tắt hộp thư và việc tạo email giả lập. Gửi email SMTP thật chưa được cấu hình. Liên kết email được đọc vào bộ nhớ giao diện rồi xóa khỏi thanh địa chỉ. Các API khôi phục công khai vẫn yêu cầu CSRF.

Chạy node database/demo-password-recovery.mjs với DEMO_PASSWORD và DEMO_DB_PASSWORD. Test dùng tài khoản QA mới, email tên miền example.test, kiểm tra ngưỡng 5 lần, reset bộ đếm, nhiều phiên, email không tiết lộ tồn tại tài khoản, token thay thế/hết hạn/dùng lại, đặt lại đồng thời, khóa bởi admin, quyền hộp thư và CSRF. DEMO_DB_PASSWORD chỉ dùng để mô phỏng hết hạn trực tiếp trong english_center_demo cho tài khoản demo.recovery vừa tạo. Có thể đặt MYSQL_EXE nếu MySQL nằm ở vị trí khác. Không cần chờ thực tế 5 phút.

## Mã giảm giá đăng ký lớp

Chạy migration 20-discount-codes.sql sau migration 19. Admin mở Mã giảm giá để tạo mã theo số tiền VND cho một khóa học, thời gian bắt đầu/hết hạn, tổng lượt tối đa và trạng thái. Mặc định giao diện là một lượt; mỗi học viên dùng cùng mã tối đa một lần. Admin điều chỉnh thời gian, giới hạn lượt và bật/tắt mã. Mã, khóa học và mức giảm được cố định khi tạo.

Học viên chọn khóa/lớp và nhập mã trước khi tạo đơn. Backend kiểm tra khóa áp dụng, thời hạn, trạng thái, lượt dùng và số tiền giảm không vượt học phí. Đơn lưu học phí gốc, giảm giá và số tiền sau giảm. QR SePay dùng số tiền sau giảm. Hóa đơn và chi tiết hóa đơn lưu học phí gốc cùng khoản giảm để đối soát.

Tạo đơn không giữ lượt mã và không đánh dấu đã dùng. Khi nhận đủ tiền, hệ thống kiểm tra lại mã và sĩ số trong transaction, sau đó ghi danh, tạo hóa đơn và ghi lượt dùng cùng nhau. Khóa dòng mã và ràng buộc UNIQUE ngăn vượt tổng lượt hoặc dùng lặp cho một học viên. Nếu mã hết hạn/hết lượt lúc tiền về, lớp đầy hoặc số tiền không đúng, giao dịch chờ kế toán đối soát và mã chưa được dùng. Chuyển sang lớp còn chỗ cùng học phần vẫn áp dụng giá của đơn và kiểm tra lại mã; mã được dùng khi chuyển lớp thành công. Hoàn tiền một giao dịch chưa ghi danh không dùng mã. Mã đã dùng cho đăng ký thành công vẫn có lịch sử lượt dùng khi học viên hủy lớp sau đó.

Nếu mã giảm toàn bộ học phí, đơn có số tiền 0 và học viên bấm Xác nhận học phí 0 ₫. Backend kiểm tra lại điều kiện rồi ghi danh/hóa đơn PAID 0 đồng và dùng mã trong cùng transaction. Đơn 0 đồng không tạo giao dịch ngân hàng giả. Đơn thu tiền thông thường vẫn cần SePay xác nhận đủ tiền.

Đặt DEMO_PASSWORD và SEPAY_WEBHOOK_API_KEY rồi chạy node database/demo-coupons.mjs với SEPAY_DEMO=true và cùng key webhook giả lập trên backend. Bài test tự tạo danh mục/mã riêng bằng API admin, kiểm tra hạn dùng, khóa học, quyền, báo giá phía server, thanh toán sai, webhook lặp, hai lớp tranh lượt cuối, mã hết hạn trong khi chờ tiền, chuyển lớp/hoàn tiền và mã giảm toàn phần. CouponServiceTest kiểm tra không dùng lại mã cho cùng học viên khi tổng lượt còn trống.
