-- Demo prices in VND, not actual offers.
START TRANSACTION;
INSERT INTO khoa_hoc (id,name,description) VALUES
('demo-foundation','Tiếng Anh nền tảng','Phát âm, từ vựng và ngữ pháp cơ bản.'),
('demo-speaking','Tiếng Anh giao tiếp thực hành','Luyện nghe nói theo tình huống thực tế.'),
('demo-toeic','Luyện thi TOEIC','Luyện nghe và đọc theo cấu trúc đề TOEIC.'),
('demo-ielts','Luyện thi IELTS','Phát triển bốn kỹ năng và làm quen cấu trúc đề IELTS.');
INSERT INTO hoc_phan (id,course_id,name,sequence_number,tuition,duration_hours) VALUES
('demo-foundation-1','demo-foundation','Foundation A1',1,3500000,32),
('demo-speaking-1','demo-speaking','Giao tiếp thực hành',1,4900000,36),
('demo-toeic-1','demo-toeic','TOEIC Listening & Reading 450+',1,2900000,32),
('demo-ielts-1','demo-ielts','IELTS Foundation',1,5900000,40);
INSERT INTO lop_hoc (id,module_id,teacher_id,class_code,start_date,end_date,max_students) VALUES
('demo-fnd-class','demo-foundation-1','demo-teacher','FND-A1-01',CURRENT_DATE + INTERVAL 7 DAY,CURRENT_DATE + INTERVAL 63 DAY,12),
('demo-speaking-class','demo-speaking-1','demo-teacher','GT-TH-01',CURRENT_DATE + INTERVAL 7 DAY,CURRENT_DATE + INTERVAL 63 DAY,12),
('demo-toeic-class','demo-toeic-1','demo-teacher','TOEIC-450-01',CURRENT_DATE + INTERVAL 7 DAY,CURRENT_DATE + INTERVAL 63 DAY,12),
('demo-ielts-class','demo-ielts-1','demo-teacher','IELTS-FND-01',CURRENT_DATE + INTERVAL 7 DAY,CURRENT_DATE + INTERVAL 63 DAY,12);
COMMIT;
