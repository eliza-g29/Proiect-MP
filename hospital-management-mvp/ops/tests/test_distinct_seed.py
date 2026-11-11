import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class SeedTests(unittest.TestCase):
    def test_clinical_contexts_distinct(self):
        sql=(ROOT/'db/seed/medflow_100plus/seed_medflow.sql').read_text()
        cases=re.findall(r"^\((\d+), '(MF-\d+)', '((?:[^']|'')*)', '((?:[^']|'')*)',",sql,re.M)
        self.assertEqual(len(cases),1080)
        self.assertEqual(len({r[1] for r in cases}),1080)
        self.assertEqual(len({(r[2],r[3]) for r in cases}),1080)
        self.assertTrue(all('Durată:' in r[2] and 'CRP:' in r[3] for r in cases))
    def test_names_distinct(self):
        for total in (100,250):
            self.assertEqual(len({((n-1)%20,((n-1)//20)%20) for n in range(1,total+1)}),total)
    def test_repeat_import_uses_stable_appointment(self):
        sql=(ROOT/'db/seed/medflow_100plus/seed_medflow.sql').read_text()
        self.assertNotIn("AND symptoms LIKE 'SIMULARE MEDFLOW:%'",sql)
        self.assertEqual(sql.count('WHERE hospital_id=v_hospital_id AND appointment_id=v_appointment_id'),2)

class LongitudinalTests(unittest.TestCase):
    def test_history_targets_synthetic_identities_and_preserves_existing_records(self):
        sql=(ROOT/'backend/src/main/resources/db/migration/V9__synthetic_longitudinal_records.sql').read_text()
        self.assertIn("email LIKE '%@medflow.example.test'",sql)
        self.assertIn("email='admin@demo.ro'",sql)
        self.assertIn('visits:=2+n%3',sql)
        self.assertIn("observations LIKE marker||' |%'",sql)
        self.assertNotIn('DELETE FROM',sql)
        self.assertNotIn('UPDATE patients',sql)
        self.assertIn('stamp,true',sql)
    def test_only_training_cases_used_and_no_empty_lab_values_inserted(self):
        import csv
        train={r['case_id'] for r in csv.DictReader((ROOT/'ai-service/data/medflow_3000/train.csv').read_text(encoding='utf-8-sig').splitlines())}
        sql=(ROOT/'db/seed/medflow_100plus/longitudinal.sql').read_text()
        sources=set(re.findall(r"\(\d+,'(MF-\d+)'",sql))
        self.assertEqual(len(sources),1500)
        self.assertTrue(sources<=train)
        self.assertIn("WHERE v<>''",sql)
    def test_import_runs_history_after_identity_refinements(self):
        script=(ROOT/'db/seed/medflow_100plus/import.sh').read_text()
        self.assertLess(script.index('refine_existing.sql'),script.index('longitudinal.sql'))

if __name__=='__main__': unittest.main()
