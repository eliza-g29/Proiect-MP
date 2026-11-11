ALTER TABLE app_users ADD COLUMN staff_specialty VARCHAR(120);
CREATE TABLE staff_work_periods (
 id BIGSERIAL PRIMARY KEY,
 hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
 staff_id BIGINT NOT NULL,
 start_at TIMESTAMP NOT NULL,
 end_at TIMESTAMP NOT NULL,
 CHECK(end_at > start_at),
 FOREIGN KEY(hospital_id,staff_id) REFERENCES app_users(hospital_id,id),
 EXCLUDE USING gist(hospital_id WITH =,staff_id WITH =,tsrange(start_at,end_at,'[)') WITH &&)
);
CREATE INDEX ix_staff_work_hospital_date ON staff_work_periods(hospital_id,start_at);
