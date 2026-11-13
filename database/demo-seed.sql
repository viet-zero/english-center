-- Set @demo_password_hash to a locally generated BCrypt hash before running this file.
INSERT INTO nguoi_dung (id, full_name) VALUES
 ('demo-user-student','Hoc vien Demo'), ('demo-user-racer','Hoc vien Demo 2'),
 ('demo-user-teacher','Giang vien Demo'), ('demo-user-accountant','Ke toan Demo'),
 ('demo-user-admin','Quan tri vien Demo');
INSERT INTO tai_khoan (id,user_id,username,password_hash) VALUES
 ('demo-acc-student','demo-user-student','demo.student',@demo_password_hash),
 ('demo-acc-racer','demo-user-racer','demo.student2',@demo_password_hash),
 ('demo-acc-teacher','demo-user-teacher','demo.teacher',@demo_password_hash),
 ('demo-acc-accountant','demo-user-accountant','demo.accountant',@demo_password_hash),
 ('demo-acc-admin','demo-user-admin','demo.admin',@demo_password_hash);
INSERT INTO vai_tro (id,name) VALUES
 ('STUDENT','Student'),('TEACHER','Teacher'),('ACCOUNTANT','Accountant'),
 ('ADMIN','Administrator'),('TRAINING_STAFF','Training staff');
INSERT INTO tai_khoan_vai_tro (account_id,role_id) VALUES
 ('demo-acc-student','STUDENT'),('demo-acc-racer','STUDENT'),('demo-acc-teacher','TEACHER'),
 ('demo-acc-accountant','ACCOUNTANT'),('demo-acc-admin','ADMIN');
INSERT INTO hoc_vien (id,user_id,student_code) VALUES
 ('demo-student','demo-user-student','DEMO-HV01'),('demo-racer','demo-user-racer','DEMO-HV02');
INSERT INTO nhan_vien (id,user_id,employee_code) VALUES
 ('demo-teacher','demo-user-teacher','DEMO-GV01'),('demo-accountant','demo-user-accountant','DEMO-KT01'),
 ('demo-admin','demo-user-admin','DEMO-AD01');
INSERT INTO khoa_hoc (id,name) VALUES ('demo-course','Tieng Anh giao tiep');
INSERT INTO hoc_phan (id,course_id,name,sequence_number,tuition,duration_hours)
 VALUES ('demo-module','demo-course','Giao tiep can ban',1,1000000.00,24);
INSERT INTO lop_hoc (id,module_id,teacher_id,class_code,start_date,end_date,max_students) VALUES
 ('demo-class','demo-module','demo-teacher','DEMO-GT01',CURRENT_DATE + INTERVAL 7 DAY,CURRENT_DATE + INTERVAL 35 DAY,12),
 ('demo-last-seat','demo-module','demo-teacher','DEMO-GT02',CURRENT_DATE + INTERVAL 8 DAY,CURRENT_DATE + INTERVAL 36 DAY,1);
INSERT INTO phong_hoc (id,name,capacity) VALUES ('demo-room','Phong 101',15);
INSERT INTO lich_hoc (id,class_id,room_id,lesson_date,start_time,end_time) VALUES
 ('demo-lesson','demo-class','demo-room',CURRENT_DATE + INTERVAL 7 DAY,'18:00','20:00'),
 ('demo-lesson-race','demo-last-seat','demo-room',CURRENT_DATE + INTERVAL 8 DAY,'18:00','20:00');
