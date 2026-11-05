-- ============================================================
-- WIL Connect & Sign-Off Portal
-- schema.sql  |  Phase 4, section 4.5
-- Target: MySQL 8.0.16+
-- ============================================================

DROP DATABASE IF EXISTS wil_connect;

CREATE DATABASE wil_connect
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
  -- MariaDB users: change the collation to utf8mb4_general_ci

USE wil_connect;

-- ------------------------------------------------------------
-- 1. USER_ACCOUNT  (table 4.5.1)
-- ------------------------------------------------------------
CREATE TABLE USER_ACCOUNT (
  user_id       INT AUTO_INCREMENT NOT NULL,
  email         VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(20) NOT NULL,

  PRIMARY KEY (user_id),

  CONSTRAINT uq_user_account_email
    UNIQUE (email),

  CONSTRAINT chk_user_account_role
    CHECK (role IN ('Student', 'Supervisor', 'Employer', 'Coordinator'))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. EMPLOYER  (table 4.5.3)
-- ------------------------------------------------------------
CREATE TABLE EMPLOYER (
  employer_id    INT AUTO_INCREMENT NOT NULL,
  user_id        INT NOT NULL,
  employer_name  VARCHAR(100) NOT NULL,
  address        VARCHAR(255) NOT NULL,
  contact_email  VARCHAR(100) NOT NULL,
  contact_number VARCHAR(20) NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'Pending',

  PRIMARY KEY (employer_id),

  CONSTRAINT uq_employer_user
    UNIQUE (user_id),

  CONSTRAINT fk_employer_user
    FOREIGN KEY (user_id)
    REFERENCES USER_ACCOUNT (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT chk_employer_status
    CHECK (status IN ('Pending', 'Approved', 'Inactive'))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. STUDENT  (table 4.5.2)
-- ------------------------------------------------------------
CREATE TABLE STUDENT (
  student_id     INT AUTO_INCREMENT NOT NULL,
  user_id        INT NOT NULL,
  student_number VARCHAR(20) NOT NULL,
  first_name     VARCHAR(50) NOT NULL,
  last_name      VARCHAR(50) NOT NULL,

  PRIMARY KEY (student_id),

  CONSTRAINT uq_student_user
    UNIQUE (user_id),

  CONSTRAINT uq_student_number
    UNIQUE (student_number),

  CONSTRAINT fk_student_user
    FOREIGN KEY (user_id)
    REFERENCES USER_ACCOUNT (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. SUPERVISOR  (table 4.5.4)
-- ------------------------------------------------------------
CREATE TABLE SUPERVISOR (
  supervisor_id         INT AUTO_INCREMENT NOT NULL,
  user_id               INT NOT NULL,
  first_name            VARCHAR(50) NOT NULL,
  last_name             VARCHAR(50) NOT NULL,
  phone                 VARCHAR(20) NOT NULL,
  verification_pin_hash VARCHAR(255) NOT NULL,
  employer_id           INT NOT NULL,

  PRIMARY KEY (supervisor_id),

  CONSTRAINT uq_supervisor_user
    UNIQUE (user_id),

  CONSTRAINT fk_supervisor_user
    FOREIGN KEY (user_id)
    REFERENCES USER_ACCOUNT (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT fk_supervisor_employer
    FOREIGN KEY (employer_id)
    REFERENCES EMPLOYER (employer_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. PLACEMENT_REQUEST  (table 4.5.5)
-- ------------------------------------------------------------
CREATE TABLE PLACEMENT_REQUEST (
  request_id  INT AUTO_INCREMENT NOT NULL,
  student_id  INT NOT NULL,
  employer_id INT NOT NULL,
  request_date DATE NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'Pending',

  PRIMARY KEY (request_id),

  CONSTRAINT fk_pr_student
    FOREIGN KEY (student_id)
    REFERENCES STUDENT (student_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT fk_pr_employer
    FOREIGN KEY (employer_id)
    REFERENCES EMPLOYER (employer_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT chk_pr_status
    CHECK (status IN (
      'Pending',
      'Meeting Requested',
      'Accepted',
      'Rejected'
    ))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. PLACEMENT  (table 4.5.6)
-- ------------------------------------------------------------
CREATE TABLE PLACEMENT (
  placement_id  INT AUTO_INCREMENT NOT NULL,
  student_id    INT NOT NULL,
  employer_id   INT NOT NULL,
  supervisor_id INT NOT NULL,
  start_date    DATE NOT NULL,
  end_date      DATE NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'Pending',

  PRIMARY KEY (placement_id),

  CONSTRAINT fk_placement_student
    FOREIGN KEY (student_id)
    REFERENCES STUDENT (student_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT fk_placement_employer
    FOREIGN KEY (employer_id)
    REFERENCES EMPLOYER (employer_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT fk_placement_supervisor
    FOREIGN KEY (supervisor_id)
    REFERENCES SUPERVISOR (supervisor_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT chk_placement_dates
    CHECK (end_date >= start_date),

  CONSTRAINT chk_placement_status
    CHECK (status IN (
      'Pending',
      'Active',
      'Completed',
      'Cancelled'
    ))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 7. DUTY_LOG  (table 4.5.7)
-- ------------------------------------------------------------
CREATE TABLE DUTY_LOG (
  log_id           INT AUTO_INCREMENT NOT NULL,
  placement_id     INT NOT NULL,
  log_date         DATE NOT NULL,
  task_description TEXT NOT NULL,
  skills_learnt    TEXT NOT NULL,
  hours_worked     DECIMAL(5,2) NOT NULL,
  status           VARCHAR(20) NOT NULL DEFAULT 'Pending',
  supervisor_comment TEXT NULL,

  PRIMARY KEY (log_id),

  CONSTRAINT uq_dutylog_placement_date
    UNIQUE (placement_id, log_date),

  CONSTRAINT fk_dutylog_placement
    FOREIGN KEY (placement_id)
    REFERENCES PLACEMENT (placement_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT chk_dutylog_hours
    CHECK (hours_worked > 0 AND hours_worked <= 24),

  CONSTRAINT chk_dutylog_status
    CHECK (status IN ('Pending', 'Signed', 'Rejected'))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 8. PROOF_OF_WORK  (table 4.5.8)
-- ------------------------------------------------------------
CREATE TABLE PROOF_OF_WORK (
  proof_id    INT AUTO_INCREMENT NOT NULL,
  log_id      INT NOT NULL,
  file_path   VARCHAR(255) NOT NULL,
  upload_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (proof_id),

  CONSTRAINT fk_pow_log
    FOREIGN KEY (log_id)
    REFERENCES DUTY_LOG (log_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 9. SIGN_OFF  (table 4.5.9)
-- log_id is not unique because corrected logs may be signed again
-- ------------------------------------------------------------
CREATE TABLE SIGN_OFF (
  signoff_id          INT AUTO_INCREMENT NOT NULL,
  log_id              INT NOT NULL,
  supervisor_id       INT NOT NULL,
  signoff_date        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  verification_method VARCHAR(20) NOT NULL,
  signature_data      TEXT NULL,

  PRIMARY KEY (signoff_id),

  CONSTRAINT fk_signoff_log
    FOREIGN KEY (log_id)
    REFERENCES DUTY_LOG (log_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT fk_signoff_supervisor
    FOREIGN KEY (supervisor_id)
    REFERENCES SUPERVISOR (supervisor_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

  CONSTRAINT chk_signoff_method
    CHECK (verification_method IN ('PIN', 'Signature')),

  CONSTRAINT chk_signoff_method_data
    CHECK (
      (verification_method = 'PIN' AND signature_data IS NULL)
      OR
      (verification_method = 'Signature' AND signature_data IS NOT NULL)
    )
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 10. NOTIFICATION  (table 4.5.10)
-- ------------------------------------------------------------
CREATE TABLE NOTIFICATION (
  notification_id INT AUTO_INCREMENT NOT NULL,
  user_id         INT NOT NULL,
  message         VARCHAR(255) NOT NULL,
  is_read         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (notification_id),

  CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id)
    REFERENCES USER_ACCOUNT (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Additional indexes  (table 4.5.11)
-- ------------------------------------------------------------
CREATE INDEX idx_employer_status_name
  ON EMPLOYER (status, employer_name);

CREATE INDEX idx_pr_student_emp_status
  ON PLACEMENT_REQUEST (student_id, employer_id, status);

CREATE INDEX idx_pr_emp_status
  ON PLACEMENT_REQUEST (employer_id, status);

CREATE INDEX idx_placement_student_status
  ON PLACEMENT (student_id, status);

CREATE INDEX idx_placement_supervisor_status
  ON PLACEMENT (supervisor_id, status);

CREATE INDEX idx_dutylog_placement_status
  ON DUTY_LOG (placement_id, status);

CREATE INDEX idx_signoff_log_date
  ON SIGN_OFF (log_id, signoff_date);

CREATE INDEX idx_signoff_supervisor_date
  ON SIGN_OFF (supervisor_id, signoff_date);

CREATE INDEX idx_notification_user_read
  ON NOTIFICATION (user_id, is_read);

-- The PROOF_OF_WORK(log_id) foreign key automatically receives
-- an InnoDB index, so an additional duplicate index is unnecessary.