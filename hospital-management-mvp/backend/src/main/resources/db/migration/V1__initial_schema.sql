CREATE TABLE hospitals (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(180) NOT NULL, address VARCHAR(240)
);
CREATE TABLE doctors (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  name VARCHAR(160) NOT NULL, specialty VARCHAR(120) NOT NULL, email VARCHAR(180)
);
CREATE TABLE patients (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL,
  birth_date DATE NOT NULL, phone VARCHAR(50), email VARCHAR(180),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app_users (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  name VARCHAR(160) NOT NULL, email VARCHAR(180) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL,
  doctor_id BIGINT REFERENCES doctors(id), patient_id BIGINT REFERENCES patients(id)
);
CREATE TABLE appointments (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  patient_id BIGINT NOT NULL REFERENCES patients(id), doctor_id BIGINT NOT NULL REFERENCES doctors(id),
  start_at TIMESTAMP NOT NULL, reason VARCHAR(300), status VARCHAR(25) NOT NULL DEFAULT 'SCHEDULED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE encounters (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  patient_id BIGINT NOT NULL REFERENCES patients(id), doctor_id BIGINT NOT NULL REFERENCES doctors(id),
  appointment_id BIGINT REFERENCES appointments(id), symptoms TEXT NOT NULL, observations TEXT,
  assessment TEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE ai_analyses (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  encounter_id BIGINT NOT NULL REFERENCES encounters(id), category VARCHAR(40) NOT NULL,
  summary TEXT NOT NULL, evidence TEXT, model_version VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY, hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
  actor_id BIGINT NOT NULL REFERENCES app_users(id), action VARCHAR(100) NOT NULL,
  resource VARCHAR(120) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_doctors_hospital ON doctors(hospital_id);
CREATE INDEX ix_patients_hospital ON patients(hospital_id);
CREATE INDEX ix_appointments_hospital_date ON appointments(hospital_id, start_at);
CREATE INDEX ix_appointments_patient ON appointments(patient_id);
CREATE INDEX ix_encounters_patient ON encounters(patient_id);
CREATE INDEX ix_ai_encounter ON ai_analyses(encounter_id);
CREATE INDEX ix_audit_hospital_date ON audit_events(hospital_id, created_at);
