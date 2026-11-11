ALTER TABLE encounters ADD CONSTRAINT uq_encounter_patient UNIQUE(hospital_id,id,patient_id);
CREATE TABLE departments (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
 name VARCHAR(160) NOT NULL, UNIQUE(hospital_id,id), UNIQUE(hospital_id,name)
);
CREATE TABLE staff_departments (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
 staff_id BIGINT NOT NULL, department_id BIGINT NOT NULL,
 valid_from DATE NOT NULL, valid_to DATE,
 CHECK(valid_to IS NULL OR valid_to >= valid_from),
 FOREIGN KEY(hospital_id,staff_id) REFERENCES app_users(hospital_id,id),
 FOREIGN KEY(hospital_id,department_id) REFERENCES departments(hospital_id,id),
 UNIQUE(hospital_id,staff_id,department_id,valid_from)
);
CREATE TABLE allergies (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), patient_id BIGINT NOT NULL,
 substance VARCHAR(200) NOT NULL, reaction TEXT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE','RESOLVED')),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,patient_id) REFERENCES patients(hospital_id,id)
);
CREATE TABLE diagnoses (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), encounter_id BIGINT NOT NULL,
 code VARCHAR(80) NOT NULL, description TEXT NOT NULL, confirmed_by BIGINT NOT NULL,
 coding_system VARCHAR(120) NOT NULL, coding_version VARCHAR(80) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,encounter_id) REFERENCES encounters(hospital_id,id),
 FOREIGN KEY(hospital_id,confirmed_by) REFERENCES app_users(hospital_id,id)
);
CREATE TABLE observations (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), encounter_id BIGINT NOT NULL,
 type VARCHAR(120) NOT NULL, value VARCHAR(300) NOT NULL, unit VARCHAR(80) NOT NULL,
 measured_at TIMESTAMP NOT NULL, coding_system VARCHAR(120) NOT NULL, coding_version VARCHAR(80) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,encounter_id) REFERENCES encounters(hospital_id,id)
);
CREATE TABLE lab_results (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), patient_id BIGINT NOT NULL,
 encounter_id BIGINT, test_code VARCHAR(120) NOT NULL, result TEXT NOT NULL, unit VARCHAR(80) NOT NULL,
 coding_system VARCHAR(120) NOT NULL, coding_version VARCHAR(80) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,patient_id) REFERENCES patients(hospital_id,id),
 FOREIGN KEY(hospital_id,encounter_id) REFERENCES encounters(hospital_id,id)
);
ALTER TABLE lab_results ADD FOREIGN KEY(hospital_id,encounter_id,patient_id)
 REFERENCES encounters(hospital_id,id,patient_id);
CREATE TABLE prescriptions (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), encounter_id BIGINT NOT NULL,
 medicine VARCHAR(200) NOT NULL, dosage VARCHAR(300) NOT NULL, instructions TEXT NOT NULL,
 prescribed_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,encounter_id) REFERENCES encounters(hospital_id,id),
 FOREIGN KEY(hospital_id,prescribed_by) REFERENCES app_users(hospital_id,id)
);
CREATE TABLE medical_documents (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), patient_id BIGINT NOT NULL,
 storage_key VARCHAR(100) NOT NULL UNIQUE, document_type VARCHAR(120) NOT NULL,
 original_name VARCHAR(255) NOT NULL, content_type VARCHAR(120) NOT NULL, size_bytes BIGINT NOT NULL CHECK(size_bytes > 0),
 uploaded_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,patient_id) REFERENCES patients(hospital_id,id),
 FOREIGN KEY(hospital_id,uploaded_by) REFERENCES app_users(hospital_id,id)
);
CREATE TABLE ai_reviews (
 id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id), analysis_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL, decision VARCHAR(30) NOT NULL CHECK(decision IN ('ACCEPTED','REJECTED','NEEDS_REVIEW')),
 notes TEXT NOT NULL, reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hospital_id,analysis_id) REFERENCES ai_analyses(hospital_id,id),
 FOREIGN KEY(hospital_id,reviewer_id) REFERENCES app_users(hospital_id,id)
);
CREATE INDEX ix_allergies_patient ON allergies(hospital_id,patient_id);
CREATE INDEX ix_diagnoses_encounter ON diagnoses(hospital_id,encounter_id);
CREATE INDEX ix_observations_encounter ON observations(hospital_id,encounter_id);
CREATE INDEX ix_labs_patient ON lab_results(hospital_id,patient_id);
CREATE INDEX ix_prescriptions_encounter ON prescriptions(hospital_id,encounter_id);
CREATE INDEX ix_documents_patient ON medical_documents(hospital_id,patient_id);
CREATE INDEX ix_reviews_analysis ON ai_reviews(hospital_id,analysis_id);
-- The runtime account must not be able to change migration history.
DO $$ BEGIN
 IF EXISTS (SELECT FROM pg_roles WHERE rolname='hospital_app') AND to_regclass('public.flyway_schema_history') IS NOT NULL THEN
  EXECUTE 'REVOKE ALL ON TABLE public.flyway_schema_history FROM hospital_app';
 END IF;
END $$;
