import test from 'node:test';
import assert from 'node:assert/strict';
import { parseAiDetails, aiPriorityLabel } from '../src/aiPresentation.mjs';

test('legacy and malformed analyses remain readable without crashing', () => {
  for (const details of [null, undefined, '', '{}', 'invalid', 'null', '[]']) {
    const result = parseAiDetails(details);
    assert.equal(result.hasGuidance, false);
    assert.deepEqual(result.conditions, []);
  }
});

test('conditions, treatment options and investigations survive presentation parsing', () => {
  const details = JSON.stringify({possibleConditions:[{code:'D01',label:'Profil exemplu',differentials:['Alternativă'],managementOptions:['Conduită din set'],investigations:['Investigație din set'],referral:'Specialitate'}],therapyOptions:['Conduită din set'],investigations:['Investigație din set'],confidenceLevel:'tentative'});
  const result = parseAiDetails(details);
  assert.equal(result.conditions[0].label, 'Profil exemplu');
  assert.deepEqual(result.options, ['Conduită din set']);
  assert.deepEqual(result.investigations, ['Investigație din set']);
  assert.equal(result.tentative, true);
  assert.equal(result.hasGuidance, true);
});

test('unexpected JSON fields cannot become React object children', () => {
  const result = parseAiDetails(JSON.stringify({possibleConditions:[null,{}, {label:{bad:true}}, {label:'Valid',managementOptions:[{},'Text'],referral:{bad:true}}],therapyOptions:[null,{},'Text']}));
  assert.equal(result.conditions.length, 1);
  assert.equal(result.conditions[0].referral, '');
  assert.deepEqual(result.options, ['Text']);
});

test('internal priority codes become readable labels', () => {
  assert.equal(aiPriorityLabel('ATTENTION_FLAG'), 'Evaluare prioritară');
  assert.equal(aiPriorityLabel('DEMO_GENERAL'), 'Evaluare obișnuită');
  assert.equal(aiPriorityLabel('UNCLASSIFIED_DEMO'), 'De clarificat');
});

test('medication cards retain checks and reject malformed or unsafe source links', () => {
  const view=parseAiDetails(JSON.stringify({medicationOptions:[null,{name:'Cetirizină',status:'review',checks:['Alergii',{}],sources:[{title:'NHS',url:'https://www.nhs.uk/medicines/cetirizine/'},{title:'Invalid',url:'javascript:alert(1)'}]}],withheldMedications:[{name:'Omeprazol',reasons:['Clopidogrel']}]}));
  assert.equal(view.medications.length,1);
  assert.deepEqual(view.medications[0].checks,['Alergii']);
  assert.equal(view.medications[0].sources.length,1);
  assert.deepEqual(view.withheld[0].reasons,['Clopidogrel']);
});

test('older medication-less analyses are distinguishable from current empty medication results',()=>{
 assert.equal(parseAiDetails('{}').hasMedicationModule,false);
 const view=parseAiDetails(JSON.stringify({medicationOptions:[],medicationNote:'Ipoteza necesită clarificare.'}));
 assert.equal(view.hasMedicationModule,true);
 assert.equal(view.medicationNote,'Ipoteza necesită clarificare.');
});
