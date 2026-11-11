"""Build a scoped migration from the project's existing synthetic fixtures."""
import csv
import re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
seed = ROOT / 'db/seed/medflow_100plus/seed_medflow.sql'
text = seed.read_text()
if "SIMULARE MEDFLOW:" not in text:
    raise SystemExit("Fixtures already refined; existing migration left unchanged.")
rows = {r['case_id']: r for r in csv.DictReader((ROOT/'ai-service/data/medflow_3000/train.csv').open(encoding='utf-8-sig'))}
quote = lambda s: "'" + s.replace("'", "''") + "'"
pattern = re.compile(r"^\((\d+), '(MF-\d+)', '((?:[^']|'')*)', '((?:[^']|'')*)',", re.M)
updates = []
keys = set()
def replace(m):
    seq, case, symptoms, observations = m.groups()
    r = rows[case]
    new = f"{r['symptoms_text']}. Durată: {r['symptom_duration_days']} zile; intensitate raportată: {r['reported_severity_0_10']}/10."
    obs = observations.replace("''", "'")
    extra = f" Glicemie: {r['glucose_mg_dl']} mg/dl; hemoglobină: {r['hemoglobin_g_dl']} g/dl; leucocite: {r['wbc_10e9_l']} ×10⁹/l; CRP: {r['crp_mg_l']} mg/l."
    newobs = obs + extra
    assert (new, newobs) not in keys
    keys.add((new, newobs))
    updates.append('('+','.join(map(quote, [symptoms.replace("''", "'"), obs, new, newobs]))+')')
    return f"({seq}, '{case}', {quote(new)}, {quote(newobs)},"
text = pattern.sub(replace, text)
assert len(updates) == 1080
text = text.replace('(((v_idx-1)*7)%cardinality(v_last_names))', '(((v_idx-1)/cardinality(v_first_names))%cardinality(v_last_names))')
text = text.replace('(((v_idx-1)*9)%cardinality(v_last_names))', '(((v_idx-1)/cardinality(v_first_names))%cardinality(v_last_names))')
seed.write_text(text)
first = "ARRAY['Ana','Mihai','Elena','Andrei','Ioana','Radu','Maria','Victor','Irina','Alexandru','Teodora','Daria','Matei','Cristina','Paul','Diana','Luca','Sofia','Oana','Vlad']"
last = "ARRAY['Popescu','Ionescu','Georgescu','Marinescu','Dumitrescu','Radu','Stan','Petrescu','Munteanu','Stoica','Florea','Sandu','Tudor','Enache','Ilie','Nistor','Dobre','Badea','Preda','Matei']"
names = f"""-- Only generated demo identities; IDs and all clinical relations remain intact.
WITH names AS (SELECT {first} AS first_names, {last} AS last_names), numbered AS (
 SELECT id, substring(email from '^demo[.]doctor([0-9]{{4}})@medflow[.]example[.]test$')::int AS n
 FROM doctors WHERE email ~ '^demo[.]doctor[0-9]{{4}}@medflow[.]example[.]test$'
)
UPDATE doctors d SET name='Dr. '||first_names[1+(n-1)%20]||' '||last_names[1+((n-1)/20)%20]
FROM numbered, names WHERE d.id=numbered.id AND n BETWEEN 1 AND 100;
WITH names AS (SELECT {first} AS first_names, {last} AS last_names), numbered AS (
 SELECT id, substring(email from '^demo[.]patient([0-9]{{4}})@medflow[.]example[.]test$')::int AS n
 FROM patients WHERE email ~ '^demo[.]patient[0-9]{{4}}@medflow[.]example[.]test$'
)
UPDATE patients p SET first_name=first_names[1+(n-1)%20],last_name=last_names[1+((n-1)/20)%20]
FROM numbered, names WHERE p.id=numbered.id AND n BETWEEN 1 AND 250;
UPDATE app_users u SET name=d.name FROM doctors d
 WHERE u.doctor_id=d.id AND u.hospital_id=d.hospital_id AND u.email=d.email AND u.email ~ '^demo[.]doctor[0-9]{{4}}@medflow[.]example[.]test$';
UPDATE app_users u SET name=p.first_name||' '||p.last_name FROM patients p
 WHERE u.patient_id=p.id AND u.hospital_id=p.hospital_id AND u.email=p.email AND u.email ~ '^demo[.]patient[0-9]{{4}}@medflow[.]example[.]test$';
"""
clinical = """-- Update only untouched imported cases, using exact original text and a seed appointment.
WITH cases(old_symptoms,old_observations,symptoms,observations) AS (VALUES
""" + ',\n'.join(updates) + """
)
UPDATE encounters e SET symptoms=c.symptoms,observations=c.observations
FROM cases c, appointments a WHERE e.appointment_id=a.id AND e.hospital_id=a.hospital_id
AND a.reason LIKE '[MFSEED:%' AND e.symptoms=c.old_symptoms AND e.observations=c.old_observations;
"""
(ROOT/'backend/src/main/resources/db/migration/V8__distinct_demo_identities_and_case_context.sql').write_text(names+clinical)
# The same scoped corrections run after a future/repeated import as well.
(ROOT/'db/seed/medflow_100plus/refine_existing.sql').write_text('BEGIN;\n'+names+clinical+'COMMIT;\n')
print(f'Prepared {len(updates)} distinct clinical contexts; 100 distinct doctors, 250 distinct patients.')
