"""Adapt the explicitly supplied MedFlow V1 fixture to this project's current schema.

Reads only the input directory given as an argument. Writes only inside the project.
Does not connect to the database or execute SQL.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parent.parent
TARGET = PROJECT / "db" / "seed" / "medflow_100plus"

PREAMBLE = r"""
-- Current-schema adapter: append-only, one transaction, all constraints remain active.
SET LOCAL lock_timeout = '15s';
SET LOCAL statement_timeout = '180s';
SELECT pg_advisory_xact_lock(760011170001::bigint);
DO $preflight$
DECLARE relation_name TEXT;
BEGIN
  FOREACH relation_name IN ARRAY ARRAY['hospitals','doctors','patients','app_users','appointments','encounters','ai_analyses','audit_events','work_periods'] LOOP
    IF to_regclass('public.' || relation_name) IS NULL THEN
      RAISE EXCEPTION 'Lipseste tabela %. Porneste backendul actualizat pentru migrarea schemei.', relation_name;
    END IF;
  END LOOP;
  IF NOT EXISTS (SELECT FROM information_schema.columns
    WHERE table_schema='public' AND table_name='appointments' AND column_name='version') THEN
    RAISE EXCEPTION 'Importul adaptat necesita schema V7. Actualizeaza backendul inainte de import.';
  END IF;
END
$preflight$;

CREATE FUNCTION pg_temp.mf_seed_ensure_work_period(h BIGINT, d BIGINT, appointment_start TIMESTAMP)
RETURNS VOID LANGUAGE plpgsql AS $work_period$
DECLARE
  preferred_start TIMESTAMP := LEAST(date_trunc('day',appointment_start)+interval '8 hours',appointment_start);
  preferred_end TIMESTAMP := GREATEST(date_trunc('day',appointment_start)+interval '18 hours',appointment_start+interval '30 minutes');
BEGIN
  PERFORM id FROM doctors WHERE hospital_id=h AND id=d FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Medicul % nu apartine spitalului %.',d,h; END IF;
  IF EXISTS (SELECT FROM work_periods WHERE hospital_id=h AND doctor_id=d
      AND start_at<=appointment_start AND end_at>=appointment_start+interval '30 minutes') THEN RETURN; END IF;
  IF NOT EXISTS (SELECT FROM work_periods WHERE hospital_id=h AND doctor_id=d
      AND start_at<preferred_end AND end_at>preferred_start) THEN
    INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at) VALUES(h,d,preferred_start,preferred_end);
  ELSIF NOT EXISTS (SELECT FROM work_periods WHERE hospital_id=h AND doctor_id=d
      AND start_at<appointment_start+interval '30 minutes' AND end_at>appointment_start) THEN
    INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at)
      VALUES(h,d,appointment_start,appointment_start+interval '30 minutes');
  ELSE
    RAISE EXCEPTION 'Program existent incompatibil cu programarea importata: spital %, medic %, ora %. Nicio inregistrare existenta nu a fost modificata.',h,d,appointment_start;
  END IF;
END
$work_period$;
"""

POSTFLIGHT = r"""
-- Assertions execute BEFORE COMMIT, so an incomplete import rolls back in full.
DO $verify$
DECLARE n BIGINT;
BEGIN
  IF (SELECT count(*) FROM mf_seed_clinical_cases)<>1080 THEN
    RAISE EXCEPTION 'Setul clinic nu contine cele 1080 de cazuri asteptate.';
  END IF;
  SELECT count(*) INTO n FROM hospitals
    WHERE name IN (SELECT 'Spital fictiv MedFlow '||lpad(i::text,3,'0') FROM generate_series(1,100) AS g(i));
  IF n<>100 THEN RAISE EXCEPTION 'Spitale sintetice: %, asteptat 100.',n; END IF;
  IF (SELECT count(*) FROM doctors WHERE lower(email) LIKE 'demo.doctor%@medflow.example.test'
      OR lower(email) LIKE 'hospital%.doctor%@medflow.example.test')<400 THEN
    RAISE EXCEPTION 'Lipsesc profiluri de medic din setul importat.';
  END IF;
  IF (SELECT count(*) FROM patients WHERE lower(email) LIKE 'demo.patient%@medflow.example.test'
      OR lower(email) LIKE 'hospital%.patient%@medflow.example.test')<1450 THEN
    RAISE EXCEPTION 'Lipsesc pacienti din setul importat.';
  END IF;
  IF (SELECT count(*) FROM app_users WHERE lower(email) LIKE 'demo.%@medflow.example.test'
      OR lower(email) LIKE 'hospital%@medflow.example.test')<420 THEN
    RAISE EXCEPTION 'Lipsesc conturi din setul importat.';
  END IF;
  IF (SELECT count(*) FROM appointments WHERE reason LIKE '[MFSEED:%')<2400 THEN
    RAISE EXCEPTION 'Lipsesc programari din setul importat.';
  END IF;
  IF (SELECT count(*) FROM encounters e JOIN appointments a ON a.id=e.appointment_id
      WHERE a.reason LIKE '[MFSEED:%' )<1080 THEN
    RAISE EXCEPTION 'Lipsesc consultatii din setul importat.';
  END IF;
  IF (SELECT count(*) FROM ai_analyses WHERE model_version='seed-fixture-synthetic-1.0')<720 THEN
    RAISE EXCEPTION 'Lipsesc etichete AI sintetice din setul importat.';
  END IF;
  IF (SELECT count(*) FROM audit_events WHERE action='DEMO_IMPORT' AND resource LIKE 'MFSEED:%')<1040 THEN
    RAISE EXCEPTION 'Lipsesc evenimente de audit din setul importat.';
  END IF;
  IF EXISTS(SELECT FROM appointments a WHERE a.reason LIKE '[MFSEED:%' AND a.status='SCHEDULED'
      AND NOT EXISTS(SELECT FROM work_periods w WHERE w.hospital_id=a.hospital_id AND w.doctor_id=a.doctor_id
        AND w.start_at<=a.start_at AND w.end_at>=a.start_at+interval '30 minutes')) THEN
    RAISE EXCEPTION 'Exista programari importate in afara programului medicului.';
  END IF;
END
$verify$;
"""


def prepare(source_dir: Path):
    sql_path = source_dir / "seed_medflow.sql"
    original = sql_path.read_text(encoding="utf-8")
    manifest = json.loads((source_dir / "manifest.json").read_text(encoding="utf-8"))
    expected = {"hospitals": 100, "doctors": 400, "patients": 1450, "app_users": 420,
                "appointments": 2400, "encounters": 1080, "ai_analyses": 720, "audit_events": 1040}
    if manifest.get("tables_added") != expected:
        raise ValueError("Pachetul nu corespunde manifestului MedFlow 100plus asteptat")
    cases = re.findall(r"^\((\d+), '(MF-\d+)',", original, re.M)
    if len(cases) != 1080 or {int(seq) for seq, _ in cases} != set(range(1, 1081)) or len({case for _, case in cases}) != 1080:
        raise ValueError("Cazurile clinice sunt incomplete sau au identificatori duplicati")
    categories = re.findall(r", '(GENERAL_REVIEW|EXTENDED_REVIEW|ATTENTION_FLAG)',", original)
    if {label: categories.count(label) for label in set(categories)} != {
            "GENERAL_REVIEW": 360, "EXTENDED_REVIEW": 360, "ATTENTION_FLAG": 360}:
        raise ValueError("Distributia categoriilor nu corespunde manifestului")
    # No persistent destructive statements or mechanisms that bypass integrity checks.
    if re.search(r"(?im)^\s*(?:TRUNCATE\b|DELETE\s+FROM\b|UPDATE\s+\w|ALTER\s+TABLE\b|DROP\s+TABLE\b|SET\s+session_replication_role\b)", original):
        raise ValueError("Pachetul contine operatii neasteptate asupra datelor existente")
    if original.count("\nBEGIN;\n") != 1 or original.count("\nCOMMIT;") != 1:
        raise ValueError("Structura tranzactionala a pachetului nu este cea asteptata")
    if original.count(") ON COMMIT DROP;\n") != 1:
        raise ValueError("Tabela temporara a cazurilor nu are structura asteptata")
    adapted = original.replace(") ON COMMIT DROP;\n", ") ON COMMIT DROP;\n" + PREAMBLE, 1)
    pattern = r"(IF v_appointment_id IS NULL THEN\n)(\s*)(INSERT INTO appointments\()"
    adapted, insertions = re.subn(pattern,
        r"\1\2IF v_status='SCHEDULED' THEN PERFORM pg_temp.mf_seed_ensure_work_period(v_hospital_id,v_doctor_id,v_date); END IF;\n\2\3",
        adapted)
    if insertions != 2:
        raise ValueError("Nu au fost identificate ambele fluxuri de programari")
    original_demo = "SELECT id INTO v_hospital_id FROM hospitals WHERE name = 'Spitalul Demo București' ORDER BY id LIMIT 1;"
    if adapted.count(original_demo) != 1:
        raise ValueError("Selectarea spitalului demo a fost modificata in sursa")
    adapted = adapted.replace(original_demo, """SELECT hospital_id INTO v_hospital_id FROM app_users WHERE lower(email)='admin@demo.ro' AND role='ADMIN' LIMIT 1;
  IF v_hospital_id IS NULL THEN
    SELECT id INTO v_hospital_id FROM hospitals WHERE name='Spitalul Demo București' ORDER BY id LIMIT 1;
  END IF;""", 1)
    # Lookup fixtures by tenant and normalized email, without changing existing identities.
    adapted = adapted.replace("FROM doctors WHERE email=v_email", "FROM doctors WHERE hospital_id=v_hospital_id AND lower(email)=lower(v_email)")
    adapted = adapted.replace("FROM patients WHERE email=v_email", "FROM patients WHERE hospital_id=v_hospital_id AND lower(email)=lower(v_email)")
    adapted = adapted.replace("FROM app_users WHERE email=v_email", "FROM app_users WHERE hospital_id=v_hospital_id AND lower(email)=lower(v_email)")
    adapted = adapted.replace("FROM app_users WHERE email='demo.admin0001@medflow.example.test'", "FROM app_users WHERE hospital_id=v_hospital_id AND lower(email)='demo.admin0001@medflow.example.test'")
    adapted = adapted.replace("ON CONFLICT (email) DO NOTHING", "ON CONFLICT DO NOTHING")
    adapted = adapted.replace("\nCOMMIT;", "\n" + POSTFLIGHT + "\nCOMMIT;", 1)
    adapted = adapted.replace("-- MedFlow • demo seed v1", "-- Adapted for MedFlow V7: original clinical data and logical identifiers preserved.\n-- MedFlow • demo seed v1", 1)
    TARGET.mkdir(parents=True, exist_ok=True)
    (TARGET / "seed_medflow.sql").write_text(adapted, encoding="utf-8")
    verification = (source_dir / "verifica_medflow.sql").read_text(encoding="utf-8")
    verification = verification.replace("FROM hospitals h WHERE name='Spitalul Demo București';",
        "FROM hospitals h WHERE h.id=COALESCE((SELECT hospital_id FROM app_users WHERE lower(email)='admin@demo.ro' AND role='ADMIN' LIMIT 1),(SELECT id FROM hospitals WHERE name='Spitalul Demo București' ORDER BY id LIMIT 1));")
    verification += """\n\\echo '=== Programari importate fara interval de lucru: trebuie zero ==='
SELECT count(*) AS erori FROM appointments a WHERE a.reason LIKE '[MFSEED:%' AND a.status='SCHEDULED'
 AND NOT EXISTS(SELECT FROM work_periods w WHERE w.hospital_id=a.hospital_id AND w.doctor_id=a.doctor_id
 AND w.start_at<=a.start_at AND w.end_at>=a.start_at+interval '30 minutes');
"""
    (TARGET / "verifica_medflow.sql").write_text(verification, encoding="utf-8")
    manifest["source_sha256"] = hashlib.sha256(sql_path.read_bytes()).hexdigest()
    manifest["adapted_for_schema"] = "V7"
    manifest["additional_supporting_records"] = "Working periods for new scheduled appointments, without modifying existing periods"
    (TARGET / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Cazuri validate offline: 1080 distincte, cate 360 in fiecare categorie.")
    print("SQL adaptat:", TARGET.relative_to(PROJECT) / "seed_medflow.sql")
    print("Nu au fost executate comenzi pe baza de date.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source_directory", type=Path)
    prepare(parser.parse_args().source_directory)
