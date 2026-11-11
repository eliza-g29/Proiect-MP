-- Verificare dupa import. Fara modificari de date.
\pset border 2
\echo '=== Numarul total de inregistrari ==='
SELECT 'hospitals' AS tabela,COUNT(*) AS total FROM hospitals
UNION ALL SELECT 'doctors',COUNT(*) FROM doctors
UNION ALL SELECT 'patients',COUNT(*) FROM patients
UNION ALL SELECT 'app_users',COUNT(*) FROM app_users
UNION ALL SELECT 'appointments',COUNT(*) FROM appointments
UNION ALL SELECT 'encounters',COUNT(*) FROM encounters
UNION ALL SELECT 'ai_analyses',COUNT(*) FROM ai_analyses
UNION ALL SELECT 'audit_events',COUNT(*) FROM audit_events;
\echo '=== Inregistrari vizibile din admin@demo.ro (Spitalul Demo Bucuresti) ==='
SELECT h.name AS spital,
  (SELECT count(*) FROM doctors d WHERE d.hospital_id=h.id) AS medici,
  (SELECT count(*) FROM patients p WHERE p.hospital_id=h.id) AS pacienti,
  (SELECT count(*) FROM appointments a WHERE a.hospital_id=h.id) AS programari,
  (SELECT count(*) FROM encounters e WHERE e.hospital_id=h.id) AS consultatii,
  (SELECT count(*) FROM ai_analyses x WHERE x.hospital_id=h.id) AS analize_ai,
  (SELECT count(*) FROM app_users u WHERE u.hospital_id=h.id) AS conturi,
  (SELECT count(*) FROM audit_events ae WHERE ae.hospital_id=h.id) AS audit
FROM hospitals h WHERE h.id=COALESCE((SELECT hospital_id FROM app_users WHERE lower(email)='admin@demo.ro' AND role='ADMIN' LIMIT 1),(SELECT id FROM hospitals WHERE name='Spitalul Demo București' ORDER BY id LIMIT 1));
\echo '=== Conturi demo noi pe rol ==='
SELECT role, count(*) FROM app_users WHERE email LIKE '%@medflow.example.test' GROUP BY role ORDER BY role;
\echo '=== Test integritate legaturi intre spitale: toate trebuie sa dea zero ==='
SELECT 'appointments_patient_hospital_mismatch' AS verificare, count(*) AS erori FROM appointments a
 JOIN patients p ON p.id=a.patient_id WHERE a.hospital_id<>p.hospital_id
UNION ALL SELECT 'appointments_doctor_hospital_mismatch',count(*) FROM appointments a
 JOIN doctors d ON d.id=a.doctor_id WHERE a.hospital_id<>d.hospital_id
UNION ALL SELECT 'encounters_patient_hospital_mismatch',count(*) FROM encounters e
 JOIN patients p ON p.id=e.patient_id WHERE e.hospital_id<>p.hospital_id
UNION ALL SELECT 'encounters_doctor_hospital_mismatch',count(*) FROM encounters e
 JOIN doctors d ON d.id=e.doctor_id WHERE e.hospital_id<>d.hospital_id
UNION ALL SELECT 'encounters_appointment_hospital_mismatch',count(*) FROM encounters e
 JOIN appointments a ON a.id=e.appointment_id WHERE e.hospital_id<>a.hospital_id
UNION ALL SELECT 'analyses_hospital_mismatch',count(*) FROM ai_analyses x
 JOIN encounters e ON e.id=x.encounter_id WHERE x.hospital_id<>e.hospital_id
UNION ALL SELECT 'audit_hospital_mismatch',count(*) FROM audit_events a
 JOIN app_users u ON u.id=a.actor_id WHERE a.hospital_id<>u.hospital_id;

\echo '=== Programari importate fara interval de lucru: trebuie zero ==='
SELECT count(*) AS erori FROM appointments a WHERE a.reason LIKE '[MFSEED:%' AND a.status='SCHEDULED'
 AND NOT EXISTS(SELECT FROM work_periods w WHERE w.hospital_id=a.hospital_id AND w.doctor_id=a.doctor_id
 AND w.start_at<=a.start_at AND w.end_at>=a.start_at+interval '30 minutes');

\echo '=== Dubluri in import: toate trebuie zero ==='
SELECT 'doctor_email' AS verificare,count(*) AS erori FROM (
 SELECT hospital_id,lower(email) FROM doctors WHERE email LIKE '%@medflow.example.test' GROUP BY 1,2 HAVING count(*)>1
) d
UNION ALL SELECT 'patient_email',count(*) FROM (
 SELECT hospital_id,lower(email) FROM patients WHERE email LIKE '%@medflow.example.test' GROUP BY 1,2 HAVING count(*)>1
) d
UNION ALL SELECT 'seed_appointment',count(*) FROM (
 SELECT hospital_id,reason FROM appointments WHERE reason LIKE '[MFSEED:%' GROUP BY 1,2 HAVING count(*)>1
) d
UNION ALL SELECT 'seed_encounter',count(*) FROM (
 SELECT e.hospital_id,e.appointment_id FROM encounters e JOIN appointments a ON a.id=e.appointment_id
 WHERE a.reason LIKE '[MFSEED:%' GROUP BY 1,2 HAVING count(*)>1
) d
UNION ALL SELECT 'clinical_context',count(*) FROM (
 SELECT e.symptoms,e.observations FROM encounters e JOIN appointments a ON a.id=e.appointment_id
 WHERE a.reason LIKE '[MFSEED:%' GROUP BY 1,2 HAVING count(*)>1
) d;

\echo '=== Dosare longitudinale sintetice ==='
SELECT count(DISTINCT e.patient_id) AS pacienti_cu_istoric,count(*) AS consultatii_istorice,
 (SELECT count(*) FROM diagnoses WHERE coding_system='MEDFLOW-SYNTHETIC') AS diagnostice_scenariu,
 (SELECT count(*) FROM lab_results WHERE coding_system='MEDFLOW-SYNTHETIC') AS analize,
 (SELECT count(*) FROM observations WHERE coding_system='MEDFLOW-SYNTHETIC') AS semne_vitale,
 (SELECT count(*) FROM prescriptions WHERE instructions LIKE '%MFHIST-v1%') AS planuri_tratament
FROM encounters e WHERE e.observations LIKE 'MFHIST-v1:%';
SELECT hospital_id,patient_id,count(*) AS consultatii FROM encounters WHERE observations LIKE 'MFHIST-v1:%'
 GROUP BY hospital_id,patient_id HAVING count(*) NOT BETWEEN 2 AND 4;
\echo '=== Marker longitudinal duplicat: trebuie zero ==='
SELECT count(*) AS erori FROM (
 SELECT hospital_id,patient_id,split_part(observations,' |',1) FROM encounters
 WHERE observations LIKE 'MFHIST-v1:%' GROUP BY 1,2,3 HAVING count(*)>1
) d;
