"""Regression checks for the PostgreSQL migration's record-variable collision."""
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class ScheduleMigrationTests(unittest.TestCase):
 def test_loop_record_does_not_shadow_sql_relation_alias(self):
  sql=(ROOT/'backend/src/main/resources/db/migration/V10__weekly_doctor_availability.sql').read_text()
  self.assertIsNone(re.search(r'FOR\s+(\w+)\s+IN\s+SELECT\s+\1[.]\*',sql,re.I))
  self.assertIn('FOR w IN SELECT wp.* FROM work_periods wp',sql)
 def test_normalization_still_targets_only_synthetic_identities(self):
  sql=(ROOT/'backend/src/main/resources/db/migration/V10__weekly_doctor_availability.sql').read_text()
  normalize=sql[sql.index('CREATE FUNCTION normalize_demo_doctor_hours'):]
  self.assertIn("d.email LIKE '%@medflow.example.test'",normalize)
  self.assertIn('u.email=\'admin@demo.ro\'',normalize)
  self.assertIn("a.status='SCHEDULED'",normalize)
  self.assertIn('doctor_schedule_exceptions',normalize)
if __name__=='__main__': unittest.main()
