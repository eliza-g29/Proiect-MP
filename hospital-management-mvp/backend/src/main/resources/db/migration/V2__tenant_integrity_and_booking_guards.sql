-- Additive migration: preserve the original Flyway checksum and existing data.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE doctors ADD CONSTRAINT uq_doctors_hospital_id UNIQUE (hospital_id, id);
ALTER TABLE patients ADD CONSTRAINT uq_patients_hospital_id UNIQUE (hospital_id, id);
ALTER TABLE app_users ADD CONSTRAINT uq_users_hospital_id UNIQUE (hospital_id, id);
ALTER TABLE appointments ADD CONSTRAINT uq_appointments_identity UNIQUE (hospital_id, id, patient_id, doctor_id);
ALTER TABLE encounters ADD CONSTRAINT uq_encounters_hospital_id UNIQUE (hospital_id, id);
ALTER TABLE ai_analyses ADD CONSTRAINT uq_analyses_hospital_id UNIQUE (hospital_id, id);
ALTER TABLE app_users ADD CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','DOCTOR','PATIENT'));
ALTER TABLE app_users ADD CONSTRAINT ck_user_profile CHECK (
  (role = 'ADMIN' AND doctor_id IS NULL AND patient_id IS NULL) OR
  (role = 'DOCTOR' AND doctor_id IS NOT NULL AND patient_id IS NULL) OR
  (role = 'PATIENT' AND patient_id IS NOT NULL AND doctor_id IS NULL));
ALTER TABLE app_users ADD FOREIGN KEY (hospital_id, doctor_id) REFERENCES doctors(hospital_id,id);
ALTER TABLE app_users ADD FOREIGN KEY (hospital_id, patient_id) REFERENCES patients(hospital_id,id);
ALTER TABLE appointments ADD CONSTRAINT ck_appointment_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED'));
ALTER TABLE appointments ADD FOREIGN KEY (hospital_id, patient_id) REFERENCES patients(hospital_id,id);
ALTER TABLE appointments ADD FOREIGN KEY (hospital_id, doctor_id) REFERENCES doctors(hospital_id,id);
ALTER TABLE encounters ADD FOREIGN KEY (hospital_id, patient_id) REFERENCES patients(hospital_id,id);
ALTER TABLE encounters ADD FOREIGN KEY (hospital_id, doctor_id) REFERENCES doctors(hospital_id,id);
ALTER TABLE encounters ADD FOREIGN KEY (hospital_id, appointment_id, patient_id, doctor_id)
  REFERENCES appointments(hospital_id,id,patient_id,doctor_id);
ALTER TABLE ai_analyses ADD FOREIGN KEY (hospital_id, encounter_id) REFERENCES encounters(hospital_id,id);
ALTER TABLE audit_events ADD FOREIGN KEY (hospital_id, actor_id) REFERENCES app_users(hospital_id,id);
-- Half-open intervals allow adjacent appointments; PostgreSQL serializes conflicts.
ALTER TABLE appointments ADD CONSTRAINT no_overlapping_doctor_appointments
  EXCLUDE USING gist (hospital_id WITH =, doctor_id WITH =,
    tsrange(start_at, start_at + interval '30 minutes', '[)') WITH &&)
  WHERE (status = 'SCHEDULED');
CREATE INDEX ix_encounters_hospital_doctor ON encounters(hospital_id,doctor_id,created_at);
CREATE INDEX ix_appointments_hospital_doctor ON appointments(hospital_id,doctor_id,start_at);
CREATE INDEX ix_audit_patient_creator ON audit_events(hospital_id,actor_id,action,resource);
