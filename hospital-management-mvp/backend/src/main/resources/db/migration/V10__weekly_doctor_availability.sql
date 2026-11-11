-- Weekday templates for synthetic doctors, with concrete periods used by booking guards.
CREATE TABLE doctor_weekly_hours (
 id BIGSERIAL PRIMARY KEY,hospital_id BIGINT NOT NULL,doctor_id BIGINT NOT NULL,
 weekday SMALLINT NOT NULL CHECK(weekday BETWEEN 1 AND 7),
 start_time TIME NOT NULL,end_time TIME NOT NULL,CHECK(end_time>start_time),
 FOREIGN KEY(hospital_id,doctor_id) REFERENCES doctors(hospital_id,id),
 UNIQUE(hospital_id,doctor_id,weekday,start_time)
);
CREATE INDEX ix_weekly_doctors ON doctor_weekly_hours(hospital_id,doctor_id,weekday);
CREATE TABLE doctor_schedule_exceptions (
 hospital_id BIGINT NOT NULL,doctor_id BIGINT NOT NULL,date DATE NOT NULL,
 PRIMARY KEY(hospital_id,doctor_id,date),
 FOREIGN KEY(hospital_id,doctor_id) REFERENCES doctors(hospital_id,id)
);
CREATE FUNCTION ensure_demo_weekly_hours(h BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO doctor_weekly_hours(hospital_id,doctor_id,weekday,start_time,end_time)
 SELECT d.hospital_id,d.id,day::smallint,
  CASE ((d.id+day)%3) WHEN 0 THEN TIME '08:00' WHEN 1 THEN TIME '10:00' ELSE TIME '14:00' END,
  CASE ((d.id+day)%3) WHEN 0 THEN TIME '14:00' WHEN 1 THEN TIME '16:00' ELSE TIME '20:00' END
 FROM doctors d CROSS JOIN generate_series(1,5) AS g(day)
 WHERE d.hospital_id=h AND (d.email LIKE '%@medflow.example.test' OR
  (d.email IN ('andrei@demo.ro','maria@demo.ro','elena@demo.ro') AND EXISTS(SELECT FROM app_users u WHERE u.hospital_id=h AND u.email='admin@demo.ro')))
 AND ((d.id+day)%6)<>0 ON CONFLICT DO NOTHING;
 INSERT INTO doctor_weekly_hours(hospital_id,doctor_id,weekday,start_time,end_time)
 SELECT d.hospital_id,d.id,6,TIME '09:00',TIME '13:00' FROM doctors d WHERE d.hospital_id=h
 AND d.email LIKE '%@medflow.example.test' AND d.id%5=0 ON CONFLICT DO NOTHING;
END $$;

CREATE FUNCTION materialize_doctor_hours(h BIGINT, first_day DATE,last_day DATE) RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE added INTEGER;
BEGIN
 IF last_day<first_day OR last_day-first_day>31 THEN RAISE EXCEPTION 'Invalid availability range'; END IF;
 PERFORM pg_advisory_xact_lock(1170010,h::int);
 PERFORM ensure_demo_weekly_hours(h);
 PERFORM normalize_demo_doctor_hours(h);
 INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at)
 SELECT h,r.doctor_id,day::date+r.start_time,day::date+r.end_time
 FROM doctor_weekly_hours r CROSS JOIN generate_series(first_day::timestamp,last_day::timestamp,interval '1 day') AS g(day)
 WHERE r.hospital_id=h AND r.weekday=extract(isodow FROM day)
 -- A configured date is an exception to the template, including split shifts.
 AND NOT EXISTS(SELECT FROM doctor_schedule_exceptions ex WHERE ex.hospital_id=h AND ex.doctor_id=r.doctor_id AND ex.date=day::date)
 AND NOT EXISTS(SELECT FROM work_periods w WHERE w.hospital_id=h AND w.doctor_id=r.doctor_id
   AND w.start_at<day::date+interval '1 day' AND w.end_at>day::date)
 ON CONFLICT DO NOTHING;
 GET DIAGNOSTICS added=ROW_COUNT;RETURN added;
END $$;

-- Normalize only recognizable broad fixture shifts; booked visits remain covered.
CREATE FUNCTION normalize_demo_doctor_hours(h BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE w RECORD;pattern RECORD;first_booking TIMESTAMP;last_booking TIMESTAMP;new_start TIMESTAMP;new_end TIMESTAMP;
BEGIN
 FOR w IN SELECT wp.* FROM work_periods wp JOIN doctors d ON d.id=wp.doctor_id AND d.hospital_id=wp.hospital_id
 WHERE wp.hospital_id=h AND wp.start_at>=current_date
 AND NOT EXISTS(SELECT FROM doctor_schedule_exceptions ex WHERE ex.hospital_id=h AND ex.doctor_id=wp.doctor_id AND ex.date=wp.start_at::date) AND
 (d.email LIKE '%@medflow.example.test' OR (d.email IN ('andrei@demo.ro','maria@demo.ro','elena@demo.ro') AND EXISTS(SELECT FROM app_users u WHERE u.hospital_id=d.hospital_id AND u.email='admin@demo.ro')))
 AND ((wp.start_at::time=TIME '08:00' AND wp.end_at::time IN (TIME '18:00',TIME '20:00') AND wp.end_at::date=wp.start_at::date)
 OR (wp.start_at::time=TIME '00:00' AND wp.end_at=wp.start_at+interval '1 day')) ORDER BY wp.id LOOP
  SELECT min(start_at),max(start_at+interval '30 minutes') INTO first_booking,last_booking FROM appointments a
   WHERE a.hospital_id=w.hospital_id AND a.doctor_id=w.doctor_id AND a.status='SCHEDULED'
   AND a.start_at>=w.start_at AND a.start_at+interval '30 minutes'<=w.end_at;
  SELECT * INTO pattern FROM doctor_weekly_hours r WHERE r.hospital_id=w.hospital_id AND r.doctor_id=w.doctor_id
   AND r.weekday=extract(isodow FROM w.start_at) ORDER BY start_time LIMIT 1;
  IF pattern.id IS NULL AND first_booking IS NULL THEN DELETE FROM work_periods WHERE id=w.id;
  ELSE
   new_start:=CASE WHEN pattern.id IS NULL THEN first_booking ELSE GREATEST(w.start_at,w.start_at::date+pattern.start_time) END;
   new_end:=CASE WHEN pattern.id IS NULL THEN last_booking ELSE LEAST(w.end_at,w.start_at::date+pattern.end_time) END;
   new_start:=LEAST(new_start,first_booking);new_end:=GREATEST(new_end,last_booking);
   UPDATE work_periods SET start_at=new_start,end_at=new_end WHERE id=w.id;
  END IF;
 END LOOP;
END $$;

DO $$ DECLARE h BIGINT; BEGIN
 FOR h IN SELECT DISTINCT hospital_id FROM doctors LOOP PERFORM ensure_demo_weekly_hours(h); PERFORM normalize_demo_doctor_hours(h); END LOOP;
END $$;
