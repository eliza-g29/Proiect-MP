ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users DROP CONSTRAINT ck_user_role;
ALTER TABLE app_users DROP CONSTRAINT ck_user_profile;
ALTER TABLE app_users ADD CONSTRAINT ck_user_role CHECK(role IN ('ADMIN','DOCTOR','STAFF','PATIENT'));
ALTER TABLE app_users ADD CONSTRAINT ck_user_profile CHECK(
 (role IN ('ADMIN','STAFF') AND doctor_id IS NULL AND patient_id IS NULL) OR
 (role='DOCTOR' AND doctor_id IS NOT NULL AND patient_id IS NULL) OR
 (role='PATIENT' AND patient_id IS NOT NULL AND doctor_id IS NULL));
CREATE UNIQUE INDEX uq_users_email_normalized ON app_users(lower(email));
CREATE TABLE notifications (
 id BIGSERIAL PRIMARY KEY,hospital_id BIGINT NOT NULL REFERENCES hospitals(id),user_id BIGINT NOT NULL,
 title VARCHAR(180) NOT NULL,message TEXT NOT NULL,read_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,user_id) REFERENCES app_users(hospital_id,id)
);
CREATE INDEX ix_notifications_user ON notifications(hospital_id,user_id,created_at DESC);
CREATE TABLE work_periods (
 id BIGSERIAL PRIMARY KEY,hospital_id BIGINT NOT NULL REFERENCES hospitals(id),doctor_id BIGINT NOT NULL,
 start_at TIMESTAMP NOT NULL,end_at TIMESTAMP NOT NULL,CHECK(end_at>start_at),
 FOREIGN KEY(hospital_id,doctor_id) REFERENCES doctors(hospital_id,id),
 EXCLUDE USING gist(hospital_id WITH =,doctor_id WITH =,tsrange(start_at,end_at,'[)') WITH &&)
);
-- Preserve existing reservations by generating working periods that contain them.
INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at)
 SELECT hospital_id,doctor_id,date_trunc('day',start_at),date_trunc('day',start_at)+interval '1 day'
 FROM appointments GROUP BY hospital_id,doctor_id,date_trunc('day',start_at);
CREATE TABLE patient_access_grants (
 id BIGSERIAL PRIMARY KEY,hospital_id BIGINT NOT NULL REFERENCES hospitals(id),patient_id BIGINT NOT NULL,
 grantee_id BIGINT,department_id BIGINT,permission VARCHAR(10) NOT NULL CHECK(permission IN ('READ','WRITE')),
 purpose VARCHAR(300) NOT NULL,expires_at TIMESTAMP NOT NULL,revoked_at TIMESTAMP,
 granted_by BIGINT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((grantee_id IS NULL) <> (department_id IS NULL)),
 FOREIGN KEY(hospital_id,patient_id) REFERENCES patients(hospital_id,id),
 FOREIGN KEY(hospital_id,grantee_id) REFERENCES app_users(hospital_id,id),
 FOREIGN KEY(hospital_id,department_id) REFERENCES departments(hospital_id,id),
 FOREIGN KEY(hospital_id,granted_by) REFERENCES app_users(hospital_id,id)
);
CREATE INDEX ix_access_patient ON patient_access_grants(hospital_id,patient_id,expires_at);
ALTER TABLE ai_reviews DROP CONSTRAINT ai_reviews_decision_check;
ALTER TABLE ai_reviews ADD CHECK(decision IN ('ACCEPTED','MODIFIED','REJECTED','NEEDS_REVIEW'));
ALTER TABLE ai_reviews ADD COLUMN revised_recommendation TEXT;
ALTER TABLE ai_analyses ADD COLUMN details TEXT NOT NULL DEFAULT '{}';
CREATE TABLE record_revisions (
 id BIGSERIAL PRIMARY KEY,hospital_id BIGINT NOT NULL REFERENCES hospitals(id),record_type VARCHAR(60) NOT NULL,
 record_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,previous_data JSONB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,actor_id) REFERENCES app_users(hospital_id,id)
);
-- Work availability is a DB invariant, including concurrent changes to working periods.
CREATE FUNCTION check_appointment_work_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='SCHEDULED' THEN
  PERFORM id FROM doctors WHERE id=NEW.doctor_id AND hospital_id=NEW.hospital_id FOR UPDATE;
  IF NOT EXISTS(SELECT FROM work_periods WHERE hospital_id=NEW.hospital_id AND doctor_id=NEW.doctor_id
    AND start_at<=NEW.start_at AND end_at>=NEW.start_at+interval '30 minutes') THEN
   RAISE EXCEPTION 'Appointment outside working hours' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER appointment_work_period BEFORE INSERT OR UPDATE ON appointments
 FOR EACH ROW EXECUTE FUNCTION check_appointment_work_period();
CREATE FUNCTION protect_booked_work_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM id FROM doctors WHERE id=OLD.doctor_id AND hospital_id=OLD.hospital_id FOR UPDATE;
 IF EXISTS(SELECT FROM appointments a WHERE a.hospital_id=OLD.hospital_id AND a.doctor_id=OLD.doctor_id
   AND a.status='SCHEDULED' AND a.start_at>=OLD.start_at AND a.start_at+interval '30 minutes'<=OLD.end_at
   AND NOT EXISTS(SELECT FROM work_periods w WHERE w.id<>OLD.id AND w.hospital_id=a.hospital_id
    AND w.doctor_id=a.doctor_id AND w.start_at<=a.start_at AND w.end_at>=a.start_at+interval '30 minutes')
   AND (TG_OP='DELETE' OR NOT (NEW.hospital_id=a.hospital_id AND NEW.doctor_id=a.doctor_id
     AND NEW.start_at<=a.start_at AND NEW.end_at>=a.start_at+interval '30 minutes'))) THEN
  RAISE EXCEPTION 'Working period has scheduled appointments' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protect_work_period BEFORE UPDATE OR DELETE ON work_periods
 FOR EACH ROW EXECUTE FUNCTION protect_booked_work_period();
