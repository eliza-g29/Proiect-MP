import test from 'node:test';
import assert from 'node:assert/strict';
import {clinicalText,patientRecords,resultCsv} from '../src/recordPresentation.mjs';
test('clinical display removes fixture wrappers without altering the clinical note',()=>{
 assert.equal(clinicalText('Eticheta didactica sintetică, neconfirmată clinic: Rinită posibilă. Nu reprezinta diagnostic medical.'),'Rinită posibilă.');
 assert.equal(clinicalText('MFHIST-v1:23:1 | Antecedente: astm. Sursa scenariului: MF-1000 TRAIN.'),'Antecedente: astm.');
 assert.equal(clinicalText('Durere în gât de 3 zile'),'Durere în gât de 3 zile');
});
test('patient records include only the chosen patient and their encounters',()=>{
 const encounters=[{id:5,patientId:21},{id:6,patientId:22}];
 assert.deepEqual(patientRecords([{id:1,patient_id:21},{id:2,encounter_id:5},{id:3,patient_id:22},{id:4,encounter_id:6}],21,encounters).map(r=>r.id),[1,2]);
});
test('result export preserves quoting and neutralizes spreadsheet formulas',()=>{
 const csv=resultCsv([{test_code:'A,"B"',result:'=HYPERLINK("x")',unit:'mg/l',created_at:'2026-11-12'}]);
 assert.ok(csv.includes('"A,""B"""'));
 assert.ok(csv.includes('"\'=HYPERLINK'));
 assert.ok(csv.startsWith('\ufeff'));
});
