import hashlib,json,unittest
from pathlib import Path
from unittest.mock import patch
from medications import medication_options,CATALOG,MODEL,assessment,features
from model import demo_response
ROOT=Path(__file__).resolve().parents[1]
CLEAR={'ageYears':35,'allergies':[],'allergiesReviewed':True,'medicinesReviewed':True,'currentMedicines':[],'pregnancyStatus':'NOT_APPLICABLE_OR_EXCLUDED','egfr':90,'conditions':[]}
class MedicationTests(unittest.TestCase):
 def test_rhinitis_has_named_source_linked_option(self):
  options,_,_=medication_options('D02',CLEAR,'strănut sezonier')
  self.assertEqual(options[0]['name'],'Cetirizină');self.assertEqual(options[0]['status'],'consider')
  self.assertTrue(all(s['url'].startswith('https://') for s in options[0]['sources']))
 def test_allergy_is_excluded_even_when_model_scores_high(self):
  with patch.object(MODEL,'probabilities',return_value=[.25]*4):
   options,blocked,_=medication_options('D02',{**CLEAR,'allergies':['Cetirizină']})
  self.assertFalse(options);self.assertEqual(blocked[0]['name'],'Cetirizină')
 def test_renal_history_excludes_relevant_option(self):
  options,blocked,_=medication_options('D02',{**CLEAR,'conditions':['boală renală cronică']})
  self.assertFalse(options);self.assertTrue(blocked)
 def test_clopidogrel_excludes_omeprazole(self):
  options,blocked,_=medication_options('D05',{**CLEAR,'currentMedicines':['Clopidogrel']})
  self.assertNotIn('Omeprazol',[i['name'] for i in options]);self.assertIn('Omeprazol',[i['name'] for i in blocked])
 def test_fever_excludes_lower_uti_antibiotic(self):
  for text in ['usturime la urinare și febră','Temperatura=39.1 C','durere de flanc']:
   options,blocked,_=medication_options('D10',CLEAR,text)
   self.assertFalse(options);self.assertTrue(blocked)
 def test_unknown_context_is_not_assumed_normal(self):
  options,_,_=medication_options('D05',{},'arsuri după mese')
  self.assertTrue(options);self.assertTrue(all(i['status']=='review' for i in options))
 def test_minor_and_pregnancy_not_given_adult_choices(self):
  for ctx in [{**CLEAR,'ageYears':8},{**CLEAR,'pregnancyStatus':'PREGNANT_OR_BREASTFEEDING'}]:
   options,blocked,_=medication_options('D03',ctx)
   self.assertFalse(options);self.assertTrue(blocked)
 def test_unmatched_tentative_urgent_or_red_flags_do_not_get_drugs(self):
  for args in [('D01',{}),('D03',{'supported':False}),('D03',{'urgent':True}),('D03',{'symptoms':'confuzie nouă'})]:
   self.assertFalse(medication_options(args[0],CLEAR,**args[1])[0])
 def test_routing_dataset_groups_are_disjoint_and_artifacts_frozen(self):
  groups={};pairs={}
  for split in ['train','validation','test']:
   rows=[json.loads(line) for line in (ROOT/'data/medication_v1'/f'{split}.jsonl').read_text().splitlines()]
   groups[split]={r['scenarioGroup'] for r in rows};pairs[split]={tuple(features(next(d for d in CATALOG['medications'] if d['id']==r['medicationId']),r['profileCode'],set(r['flags']))) for r in rows}
  self.assertFalse(groups['train']&groups['test']);self.assertFalse(groups['train']&groups['validation']);self.assertFalse(groups['validation']&groups['test'])
  self.assertFalse(pairs['train']&pairs['test'])
  report=json.loads((ROOT/'artifacts/medication_evaluation.json').read_text())
  self.assertFalse(report['clinicalValidation']);self.assertEqual(report['counts']['test'],3150)
  self.assertEqual(report['modelSha256'],hashlib.sha256((ROOT/'artifacts/medication_model.json').read_bytes()).hexdigest())
 def test_history_does_not_override_current_classification(self):
  a=demo_response('strănut, rinoree apoasă, mâncărime nazală')
  b=demo_response('strănut, rinoree apoasă, mâncărime nazală',medical_history=['durere toracică apăsătoare, iradiere în braț']*10)
  self.assertEqual(a['possibleConditions'],b['possibleConditions'])

class ShortSymptomTests(unittest.TestCase):
 def test_colloquial_symptoms_generate_specific_options(self):
  cases=[('stranut, imi curge nasul si ma mananca nasul','D02','Cetirizină'),('arsuri dupa masa, regurgitatii acide','D05','Omeprazol'),('durere de cap bilaterala si tensiune cervicala','D04','Paracetamol')]
  for text,code,medicine in cases:
   with self.subTest(text=text):
    result=demo_response(text,patient_context=CLEAR)
    self.assertEqual(result['possibleConditions'][0]['code'],code)
    self.assertIn(medicine,[m['name'] for m in result['medicationOptions']])
 def test_missing_medications_have_an_explicit_reason(self):
  for text in ['oboseala','xyzq abcdefgh necunoscut']:
   result=demo_response(text)
   self.assertTrue(result['medicationNote'])
   self.assertFalse(result['medicationOptions'])
 def test_short_classifier_matches_frozen_report_and_original_splits(self):
  report=json.loads((ROOT/'artifacts/short_symptoms_evaluation.json').read_text())
  self.assertEqual(hashlib.sha256((ROOT/'artifacts/short_symptoms.json.gz').read_bytes()).hexdigest(),report['modelSha256'])
  self.assertFalse(report['clinicalValidation']);self.assertEqual(report['counts'],{'train':2250,'validation':375,'test':375})
  for split,value in report['sourceSha256'].items():
   self.assertEqual(hashlib.sha256((ROOT/'data/medflow_3000'/f'{split}.csv').read_bytes()).hexdigest(),value)

if __name__=='__main__':unittest.main()
