# Huong dan Controller Spring Boot cho English Center

## Cac thanh phan
EnglishCenterApplication.java la diem khoi dong Spring Boot da co san.
Controller nhan HTTP va kiem tra DTO bang @Valid.
CurrentUserService lay ma hoc vien/nhan vien tu Principal da xac thuc.
Service xu ly nghiep vu va transaction, Repository truy van MySQL.
Khong gui studentId hay employeeId tu form de tu nhan danh nguoi khac.

## API dang ky
POST /api/enrollments
Content-Type application/json
Body
{ "classId": "MA_LOP" }

Tra HTTP 200 voi enrollmentId va invoiceId. Gui lai cung lop tra ket qua cu.
RegistrationService thuc te ten la EnrollmentService. Service khoa hoc vien roi khoa lop,
kiem tra si so, tien quyet, lich trung va tao hoa don trong cung transaction.

## API hoc phi
GET /api/invoices/MA_HOA_DON
Chi hoc vien so huu hoa don duoc xem. Khong tim thay hoac khong so huu tra 404.
Tra totalDue, totalPaid, remainingAmount va status.

POST /api/invoices/MA_HOA_DON/payments
Body
{ "amount": 500000, "method": "BANK_TRANSFER", "referenceCode": "THU-2026-0001" }

Chi nhan vien co vai tro ACCOUNTANT dang hoat dong duoc ghi nhan.
Day la ghi nhan khoan thu da xac nhan cua ke toan.
Gui lai phai giu nguyen referenceCode va noi dung de tranh thu trung.
Service khoa hoa don, chan vuot cong no va luu khoan thu cung cap nhat hoa don.
API tra HTTP 200 cho ca lan ghi nhan dau va lan gui lai hop le.

## Dieu kien truoc khi su dung API
Phan dang nhap va Spring Security chua duoc trien khai.
Cac API hien yeu cau Principal, goi an danh se nhan 401.
Can cau hinh xac thuc tao Principal voi username khop tai_khoan.username.
Neu dung cookie/session can giu bao ve CSRF cho cac thao tac ghi.
Can tao schema, cap tai khoan va vai tro ACCOUNTANT truoc khi chay that.
Hien tai chua tao hay chay database theo yeu cau.

## Loi
400 khi JSON hoac gia tri dau vao khong hop le.
401 khi chua dang nhap, 403 khi ho so khong hop le.
409 khi vi pham nghiep vu, trung du lieu hoac tranh chap khoa.
Loi SQL va thong tin noi bo khong duoc tra ve client.
Quyen ke toan con duoc PaymentService kiem tra; hien tra 409 neu khong dat.

## Kiem tra ma nguon khong can database
Dat JAVA_HOME toi JDK 21 hoac cao hon.
Chay trong thu muc goc du an
mvnw.cmd -DskipTests compile
mvnw.cmd -Dtest=PaymentServiceTest test

Khong chay spring-boot:run hoac bai test EnglishCenterApplicationTests khi chua muon ket noi MySQL.
Kiem thu dong thoi, SQL native va transaction thuc te can MySQL o giai doan sau.
