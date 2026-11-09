-- ============================================================
-- WIL Connect & Sign-Off Portal
-- seed.sql  |  development / demo data
-- Run AFTER schema.sql
-- ============================================================
USE wil_connect;

-- Allow full-table resets regardless of Workbench Safe Updates mode
SET SQL_SAFE_UPDATES = 0;

-- Reset in child -> parent order so the script can be re-run
DELETE FROM NOTIFICATION;
DELETE FROM SIGN_OFF;
DELETE FROM PROOF_OF_WORK;
DELETE FROM DUTY_LOG;
DELETE FROM PLACEMENT;
DELETE FROM PLACEMENT_REQUEST;
DELETE FROM SUPERVISOR;
DELETE FROM STUDENT;
DELETE FROM EMPLOYER;
DELETE FROM USER_ACCOUNT;

-- ------------------------------------------------------------
-- USER_ACCOUNT
-- 1=Coordinator 2,3=Students 4,5=Supervisors 6,7=Employers
-- Password for every account in this seed is: password
-- ------------------------------------------------------------
INSERT INTO USER_ACCOUNT (user_id, email, password_hash, role) VALUES
(1,'coordinator@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','COORDINATOR'),
(2,'john.smith@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','STUDENT'),
(3,'sarah.jones@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','STUDENT'),
(4,'michael.brown@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','SUPERVISOR'),
(5,'linda.davis@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','SUPERVISOR'),
(6,'hr.nexus@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','EMPLOYER'),
(7,'hr.brightworks@richfield.co.za','$2a$10$n.NJWnQX8FzJIP5C9AkNxOsEQpI7yoTFHfa4ZGGZr8MBQ4nYsFAIa','EMPLOYER');

-- ------------------------------------------------------------
-- EMPLOYER
-- ------------------------------------------------------------
INSERT INTO EMPLOYER (employer_id, user_id, employer_name, address, contact_email, contact_number, status) VALUES
(1,6,'Nexus Tech Solutions','12 Rivonia Road, Sandton, Johannesburg','hr@nexustech.co.za','0115550100','Approved'),
(2,7,'BrightWorks Solutions','45 Loop Street, Cape Town','hr@brightworks.co.za','0215550199','Pending');

-- ------------------------------------------------------------
-- STUDENT
-- ------------------------------------------------------------
INSERT INTO STUDENT (student_id, user_id, student_number, first_name, last_name) VALUES
(1,2,'20240123','John','Smith'),
(2,3,'20240187','Sarah','Jones');

-- ------------------------------------------------------------
-- SUPERVISOR  (demo PIN = 1234)
-- before running C5's PIN sign-off flow. See README.
-- ------------------------------------------------------------
INSERT INTO SUPERVISOR (supervisor_id, user_id, first_name, last_name, phone, verification_pin_hash, employer_id) VALUES
(1,4,'Michael','Brown','0821234567','$2a$10$CTTG.yRERmFoMi1jYD7MCubkIARKujqvhmBDx9gTcDE3Lz5.d33Su',1),
(2,5,'Linda','Davis','0837654321','$2a$10$CTTG.yRERmFoMi1jYD7MCubkIARKujqvhmBDx9gTcDE3Lz5.d33Su',1);

-- ------------------------------------------------------------
-- PLACEMENT_REQUEST
-- ------------------------------------------------------------
INSERT INTO PLACEMENT_REQUEST (request_id, student_id, employer_id, request_date, status) VALUES
(1,1,1,'2026-08-10','Accepted'),
(2,2,1,'2026-10-20','Meeting Requested'),
(3,1,2,'2026-08-05','Rejected');

-- ------------------------------------------------------------
-- PLACEMENT  (active placement for the demo)
-- ------------------------------------------------------------
INSERT INTO PLACEMENT (placement_id, student_id, employer_id, supervisor_id, start_date, end_date, status) VALUES
(1,1,1,1,'2026-09-01','2026-11-30','Active');

-- ------------------------------------------------------------
-- DUTY_LOG  (12 signed = 96.00 h, 1 rejected, 1 pending)
-- ------------------------------------------------------------
INSERT INTO DUTY_LOG (log_id, placement_id, log_date, task_description, skills_learnt, hours_worked, status, supervisor_comment) VALUES
(1 ,1,'2026-09-01','Orientation and system access set up','Company onboarding and development environment setup',8.00,'Signed',NULL),
(2 ,1,'2026-09-02','Reviewed the database design document','Relational modelling and normalisation review',8.00,'Signed',NULL),
(3 ,1,'2026-09-03','Wrote SQL queries for customer reports','SQL joins and aggregate functions',8.00,'Signed',NULL),
(4 ,1,'2026-09-04','Fixed data entry validation errors','Form validation and error handling',8.00,'Signed',NULL),
(5 ,1,'2026-09-07','Tested the report export function','Functional testing and test documentation',8.00,'Signed',NULL),
(6 ,1,'2026-09-08','Added indexes to slow database tables','Query performance and indexing',8.00,'Signed',NULL),
(7 ,1,'2026-09-09','Wrote test cases for the login page','Test case design and boundary testing',8.00,'Signed',NULL),
(8 ,1,'2026-09-10','Fixed bugs found in testing','Debugging and defect resolution',8.00,'Signed',NULL),
(9 ,1,'2026-10-21','Updated the user guide for the reporting tool','Technical writing and version control',8.00,'Signed',NULL),
(10,1,'2026-10-22','Reviewed code changes with the team lead','Code review and team collaboration',8.00,'Signed',NULL),
(11,1,'2026-10-23','Wrote 10 new test cases','Test coverage and regression planning',8.00,'Signed',NULL),
(12,1,'2026-10-27','Fixed errors found in system testing','System testing and bug fixing',8.00,'Signed',NULL),
(13,1,'2026-10-28','Attended the weekly project stand up','Agile ceremonies and progress reporting',8.00,'Rejected','Please attach the timesheet as proof for this day.'),
(14,1,'2026-10-29','Updated the customer database schema, wrote 12 test queries and attended the team stand up meeting.','Schema migration and SQL testing',7.50,'Pending',NULL);

-- ------------------------------------------------------------
-- PROOF_OF_WORK
-- Put matching placeholder files in the app's upload folder
-- ------------------------------------------------------------
INSERT INTO PROOF_OF_WORK (proof_id, log_id, file_path, upload_date) VALUES
(1,1 ,'uploads/proof/2026-09-01_site_photo.jpg','2026-09-01 16:40:00'),
(2,5 ,'uploads/proof/2026-09-07_timesheet.pdf','2026-09-07 16:55:00'),
(3,12,'uploads/proof/2026-10-27_test_report.pdf','2026-10-27 17:05:00'),
(4,14,'uploads/proof/2026-10-29_site_photo.jpg','2026-10-29 17:20:00'),
(5,14,'uploads/proof/2026-10-29_timesheet.pdf','2026-10-29 17:21:00');

-- ------------------------------------------------------------
-- SIGN_OFF  (one row per signed log; alternating PIN / Signature)
-- ------------------------------------------------------------
INSERT INTO SIGN_OFF (log_id, supervisor_id, signoff_date, verification_method, signature_data) VALUES
(1 ,1,'2026-09-02 08:14:00','PIN',NULL),
(2 ,1,'2026-09-03 08:55:00','Signature','data:image/png;base64,SEED_SIGNATURE_002'),
(3 ,1,'2026-09-04 10:02:00','PIN',NULL),
(4 ,1,'2026-09-07 08:47:00','PIN',NULL),
(5 ,1,'2026-09-08 09:30:00','Signature','data:image/png;base64,SEED_SIGNATURE_005'),
(6 ,1,'2026-09-09 09:05:00','PIN',NULL),
(7 ,1,'2026-09-10 08:58:00','Signature','data:image/png;base64,SEED_SIGNATURE_007'),
(8 ,1,'2026-09-11 09:21:00','PIN',NULL),
(9 ,1,'2026-10-22 09:20:00','Signature','data:image/png;base64,SEED_SIGNATURE_009'),
(10,1,'2026-10-23 09:10:00','PIN',NULL),
(11,1,'2026-10-24 08:55:00','Signature','data:image/png;base64,SEED_SIGNATURE_011'),
(12,1,'2026-10-28 08:30:00','PIN',NULL);

-- ------------------------------------------------------------
-- NOTIFICATION
-- ------------------------------------------------------------
INSERT INTO NOTIFICATION (user_id, message, is_read, created_at) VALUES
(4,'A new log from John Smith needs sign off.',FALSE,'2026-10-29 17:22:00'),
(2,'Your log for 27 October 2026 was signed off.',TRUE,'2026-10-28 08:30:00'),
(2,'Your log for 28 October 2026 was rejected: Please attach the timesheet as proof for this day.',FALSE,'2026-10-28 09:15:00'),
(6,'New placement request from Sarah Jones.',FALSE,'2026-10-20 11:05:00');

-- Restore the safer default for this session
SET SQL_SAFE_UPDATES = 1;