-- Run against a migrated database with psql -v ON_ERROR_STOP=1. All fixture rows roll back.
BEGIN;
DO $$
DECLARE h1 BIGINT; h2 BIGINT; d BIGINT; p1 BIGINT; p2 BIGINT; a BIGINT; e BIGINT; staff BIGINT;
BEGIN
 INSERT INTO hospitals(name) VALUES ('Guard test A') RETURNING id INTO h1;
 INSERT INTO hospitals(name) VALUES ('Guard test B') RETURNING id INTO h2;
 INSERT INTO doctors(hospital_id,name,specialty) VALUES(h1,'Test','Demo') RETURNING id INTO d;
 INSERT INTO patients(hospital_id,first_name,last_name,birth_date) VALUES(h1,'Test','A','2000-01-01') RETURNING id INTO p1;
 INSERT INTO patients(hospital_id,first_name,last_name,birth_date) VALUES(h1,'Test','B','2000-01-01') RETURNING id INTO p2;
 INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at) VALUES(h1,d,'2099-01-01','2099-01-03');
 INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at) VALUES(h1,p1,d,'2099-01-01 10:00') RETURNING id INTO a;
 BEGIN
  INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at) VALUES(h1,p2,d,'2099-01-01 10:29:59');
  RAISE EXCEPTION 'FAIL: overlapping appointment accepted';
 EXCEPTION WHEN exclusion_violation THEN NULL; END;
 INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at) VALUES(h1,p2,d,'2099-01-01 10:30');
 BEGIN
  INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at,status) VALUES(h1,p1,d,'2099-01-02','INVALID');
  RAISE EXCEPTION 'FAIL: invalid status accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at,status) VALUES(h2,p1,d,'2099-01-02','CANCELLED');
  RAISE EXCEPTION 'FAIL: cross-hospital references accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO encounters(hospital_id,patient_id,doctor_id,appointment_id,symptoms) VALUES(h1,p2,d,a,'Demo');
  RAISE EXCEPTION 'FAIL: mismatched appointment patient accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 INSERT INTO encounters(hospital_id,patient_id,doctor_id,symptoms) VALUES(h1,p1,d,'Demo') RETURNING id INTO e;
 BEGIN
  INSERT INTO lab_results(hospital_id,patient_id,encounter_id,test_code,result,unit,coding_system,coding_version)
   VALUES(h1,p2,e,'DEMO','demo','demo','LOCAL','1');
  RAISE EXCEPTION 'FAIL: lab attached to another patient encounter';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO appointments(hospital_id,patient_id,doctor_id,start_at) VALUES(h1,p1,d,'2099-01-04 10:00');
  RAISE EXCEPTION 'FAIL: appointment outside work hours accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  DELETE FROM work_periods WHERE hospital_id=h1 AND doctor_id=d;
  RAISE EXCEPTION 'FAIL: booked work period deleted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 INSERT INTO app_users(hospital_id,name,email,password_hash,role) VALUES(h1,'Schema staff','schema-check-'||h1||'@example.test','not-a-login-hash','STAFF') RETURNING id INTO staff;
 INSERT INTO staff_work_periods(hospital_id,staff_id,start_at,end_at) VALUES(h1,staff,'2099-01-01 09:00','2099-01-01 17:00');
 BEGIN
  INSERT INTO staff_work_periods(hospital_id,staff_id,start_at,end_at) VALUES(h1,staff,'2099-01-01 16:00','2099-01-01 18:00');
  RAISE EXCEPTION 'FAIL: overlapping staff shifts accepted';
 EXCEPTION WHEN exclusion_violation THEN NULL; END;
 BEGIN
  INSERT INTO staff_work_periods(hospital_id,staff_id,start_at,end_at) VALUES(h2,staff,'2099-01-02 09:00','2099-01-02 17:00');
  RAISE EXCEPTION 'FAIL: cross-hospital staff shift accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 -- Explicitly preserve the documented optional appointment and 1:N appointment->encounters relation.
 INSERT INTO encounters(hospital_id,patient_id,doctor_id,appointment_id,symptoms) VALUES(h1,p1,d,a,'Demo one');
 INSERT INTO encounters(hospital_id,patient_id,doctor_id,appointment_id,symptoms) VALUES(h1,p1,d,a,'Demo two');
 RAISE NOTICE 'Schema guard checks passed';
END $$;
ROLLBACK;
