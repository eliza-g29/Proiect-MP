"""Generate idempotent histories for synthetic identities only, from local TRAIN cases."""
from pathlib import Path
import csv,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
rows=list(csv.DictReader((ROOT/'ai-service/data/medflow_3000/train.csv').open(encoding='utf-8-sig')))
quote=lambda s:"'"+str(s).replace("'","''")+"'"
# Non-urgent outpatient scenarios; source labels remain explicitly synthetic.
rows=[r for r in rows if r['target_triage_category']!='ATTENTION_FLAG' and r['target_syndrome_code']!='D01']
fields=['case_id','symptoms_text','symptom_duration_days','reported_severity_0_10','chronic_conditions','family_history','temperature_c','heart_rate_bpm','systolic_bp_mmhg','diastolic_bp_mmhg','spo2_percent','glucose_mg_dl','hemoglobin_g_dl','wbc_10e9_l','crp_mg_l','target_syndrome_code','target_syndrome_label']
values=',\n'.join('('+str(i)+','+','.join(quote(r[f]) for f in fields)+')' for i,r in enumerate(rows,1))
sql='''-- Synthetic longitudinal histories; no existing medical record is overwritten.
CREATE TEMP TABLE mf_history_cases(seq int PRIMARY KEY,'''+','.join(f+' text' for f in fields)+''') ON COMMIT DROP;
INSERT INTO mf_history_cases VALUES
'''+values+''';
DO $history$
DECLARE p record; c record; clinician bigint; doc bigint; episode int; visits int; n int;
 e bigint; base_seq int; anchor timestamp; stamp timestamp; marker text; allergy text; reaction text; medicine text; chronic text; family text;
BEGIN
 PERFORM pg_advisory_xact_lock(760011170009::bigint);
 FOR p IN SELECT * FROM patients WHERE email LIKE '%@medflow.example.test' OR (email IN ('ana@example.test','mihai@example.test','ioana@example.test','victor@example.test','alexandra@example.test') AND hospital_id IN (SELECT hospital_id FROM app_users WHERE email='admin@demo.ro')) ORDER BY hospital_id,id LOOP
  SELECT u.id,u.doctor_id INTO clinician,doc FROM app_users u
   WHERE u.hospital_id=p.hospital_id AND u.active AND u.role='DOCTOR' ORDER BY md5(u.id::text||p.email) LIMIT 1;
  IF clinician IS NULL THEN
   SELECT u.id INTO clinician FROM app_users u WHERE u.hospital_id=p.hospital_id AND u.active AND u.role='ADMIN' ORDER BY u.id LIMIT 1;
   SELECT d.id INTO doc FROM doctors d WHERE d.hospital_id=p.hospital_id ORDER BY d.id LIMIT 1;
  END IF;
  IF clinician IS NULL OR doc IS NULL THEN CONTINUE; END IF;
  n:=abs(mod(hashtextextended(p.email,9),1000000))::int;
  visits:=2+n%3;
  SELECT COALESCE(min(created_at),current_timestamp::timestamp) INTO anchor FROM encounters WHERE hospital_id=p.hospital_id AND patient_id=p.id;
  allergy:=(ARRAY['polen de graminee','acarieni','latex','nichel','peniciline','cetirizină','paracetamol','fără alergii cunoscute'])[1+n%8];
  SELECT COALESCE(string_agg(substance,'; ' ORDER BY id),allergy) INTO allergy FROM allergies WHERE hospital_id=p.hospital_id AND patient_id=p.id AND status='ACTIVE';
  SELECT seq INTO base_seq FROM mf_history_cases WHERE glucose_mg_dl<>'' OR hemoglobin_g_dl<>'' OR wbc_10e9_l<>'' OR crp_mg_l<>'' ORDER BY seq OFFSET (n%(SELECT count(*) FROM mf_history_cases WHERE glucose_mg_dl<>'' OR hemoglobin_g_dl<>'' OR wbc_10e9_l<>'' OR crp_mg_l<>'')) LIMIT 1;
  SELECT chronic_conditions,family_history INTO chronic,family FROM mf_history_cases WHERE seq=base_seq;
  reaction:=(ARRAY['urticarie relatată după expunere','prurit și eritem local','rinoree și strănut sezonier','erupție cutanată relatată'])[1+(n/8)%4];
  reaction:=CASE WHEN allergy IN ('polen de graminee','acarieni') THEN 'rinoree, strănut și prurit ocular după expunere' WHEN allergy IN ('latex','nichel') THEN 'dermatită de contact cu eritem și prurit local' ELSE 'erupție cutanată și urticarie relatate după administrare' END;
  IF allergy<>'fără alergii cunoscute' AND NOT EXISTS(SELECT FROM allergies WHERE hospital_id=p.hospital_id AND patient_id=p.id AND status='ACTIVE') THEN
   INSERT INTO allergies(hospital_id,patient_id,substance,reaction,status,created_at,patient_visible)
    VALUES(p.hospital_id,p.id,allergy,reaction||'. Scenariu sintetic longitudinal MFHIST-v1.','ACTIVE',anchor-interval '400 days',true);
  END IF;
  FOR episode IN 1..visits LOOP
   -- First two encounters form a follow-up; later encounters explore a different profile.
   SELECT * INTO c FROM mf_history_cases WHERE seq=CASE WHEN episode<=2 THEN base_seq ELSE 1+((n+episode*137)%'''+str(len(rows))+''') END;
   marker:='MFHIST-v1:'||p.id||':'||episode;
   IF EXISTS(SELECT FROM encounters WHERE hospital_id=p.hospital_id AND patient_id=p.id AND observations LIKE marker||' |%') THEN CONTINUE; END IF;
   stamp:=anchor-make_interval(days=>420-CASE WHEN episode=1 THEN 0 WHEN episode=2 THEN 14 ELSE 14+(episode-2)*90 END+(n%29),hours=>n%6,mins=>(n/6)%60);
   INSERT INTO encounters(hospital_id,patient_id,doctor_id,symptoms,observations,assessment,created_at,patient_visible)
   VALUES(p.hospital_id,p.id,doc,
    CASE WHEN episode=2 THEN 'Control evolutiv: ' ELSE 'Evaluare inițială: ' END||c.symptoms_text||'. Durată relatată: '||c.symptom_duration_days||' zile; intensitate: '||c.reported_severity_0_10||'/10.',
    marker||' | Antecedente personale: '||chronic||'. Antecedente familiale: '||family||'. Alergii declarate: '||allergy||'. Context: '||(ARRAY['activitate predominant la birou','program de lucru în ture','activitate fizică recreativă','expunere sezonieră în aer liber','revenire după o călătorie','monitorizare periodică în ambulatoriu'])[1+(n/24)%6]||'. Sursa scenariului: '||c.case_id||' TRAIN.',
    c.target_syndrome_label||CASE WHEN episode=2 THEN '. Reevaluarea evoluției și a toleranței la conduita anterioară.' ELSE '. Evaluare inițială și plan de urmărire.' END,stamp,true) RETURNING id INTO e;
   INSERT INTO diagnoses(hospital_id,encounter_id,code,description,confirmed_by,coding_system,coding_version,created_at,patient_visible)
    VALUES(p.hospital_id,e,c.target_syndrome_code,c.target_syndrome_label||' — etichetă sintetică de scenariu',clinician,'MEDFLOW-SYNTHETIC','1',stamp,true);
   IF chronic NOT IN ('niciuna cunoscută','fără boli cronice cunoscute','') THEN
    INSERT INTO diagnoses(hospital_id,encounter_id,code,description,confirmed_by,coding_system,coding_version,created_at,patient_visible)
     VALUES(p.hospital_id,e,'HISTORY',chronic,clinician,'MEDFLOW-SYNTHETIC','1',stamp,true);
   END IF;
   INSERT INTO observations(hospital_id,encounter_id,type,value,unit,measured_at,coding_system,coding_version,created_at,patient_visible)
    SELECT p.hospital_id,e,t,v,u,stamp,'MEDFLOW-SYNTHETIC','1',stamp,true FROM (VALUES
     ('Temperatură',c.temperature_c,'°C'),('Puls',c.heart_rate_bpm,'/min'),
     ('Tensiune arterială',c.systolic_bp_mmhg||'/'||c.diastolic_bp_mmhg,'mmHg'),('SpO2',c.spo2_percent,'%')) AS o(t,v,u);
   INSERT INTO lab_results(hospital_id,patient_id,encounter_id,test_code,result,unit,coding_system,coding_version,created_at,patient_visible)
    SELECT p.hospital_id,p.id,e,t,v,u,'MEDFLOW-SYNTHETIC','1',stamp+interval '2 hours',true FROM (VALUES
     ('GLUCOSE',c.glucose_mg_dl,'mg/dl'),('HEMOGLOBIN',c.hemoglobin_g_dl,'g/dl'),('WBC',c.wbc_10e9_l,'10^9/l'),('CRP',c.crp_mg_l,'mg/l')) AS l(t,v,u) WHERE v<>'';
   medicine:=CASE c.target_syndrome_code WHEN 'D02' THEN 'Cetirizină' WHEN 'D05' THEN 'Alginat de sodiu' WHEN 'D07' THEN 'Macrogol' WHEN 'D04' THEN 'Paracetamol' WHEN 'D06' THEN 'Paracetamol' WHEN 'D03' THEN 'Paracetamol' ELSE NULL END;
   IF medicine IS NOT NULL AND lower(medicine)<>lower(allergy) AND NOT (medicine IN ('Cetirizină','Macrogol','Alginat de sodiu') AND lower(chronic) LIKE '%renal%') AND NOT (medicine='Paracetamol' AND (lower(chronic) LIKE '%hepat%' OR lower(chronic) LIKE '%ciroz%')) AND NOT EXISTS(SELECT FROM allergies a WHERE a.hospital_id=p.hospital_id AND a.patient_id=p.id AND a.status='ACTIVE' AND (lower(a.substance)=lower(medicine) OR lower(a.substance) LIKE '%medicamentoas%')) THEN
    INSERT INTO prescriptions(hospital_id,encounter_id,medicine,dosage,instructions,prescribed_by,created_at,patient_visible)
     VALUES(p.hospital_id,e,medicine,'Doză neprecizată în scenariul sintetic','Plan demonstrativ MFHIST-v1; urmărirea toleranței și a evoluției simptomelor. Nu reprezintă tratamentul curent confirmat.',clinician,stamp,true);
   END IF;
  END LOOP;
 END LOOP;
END
$history$;
'''
(ROOT/'backend/src/main/resources/db/migration/V9__synthetic_longitudinal_records.sql').write_text(sql)
(ROOT/'db/seed/medflow_100plus/longitudinal.sql').write_text('BEGIN;\n'+sql+'COMMIT;\n')
print(f'Prepared {len(rows)} outpatient source scenarios; 2–4 prior visits per synthetic patient.')
