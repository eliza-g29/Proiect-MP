export function clinicalText(value) {
 return String(value??'').replace(/^MFHIST-v1:\d+:\d+\s*\|\s*/,'').replace(/\s*Sursa scenariului: MF-\d+ TRAIN\.?/g,'').replace(/\s*Scenariu sintetic longitudinal MFHIST-v1\.?/g,'').replace('Doză neprecizată în scenariul sintetic','Doză neprecizată în dosar').replace('Plan demonstrativ MFHIST-v1; ','').replace(/^Eticheta didactica sintetică, neconfirmată clinic:\s*/,'').replace(/\s*Nu reprezinta diagnostic medical\.?$/,'').replace(/^\[DATE SINTETICE\]\s*/,'').replace(/^SIMULARE MEDFLOW:\s*/,'').replace(/\s*— etichetă sintetică de scenariu$/,'');
}
export function patientRecords(rows,patientId,encounters) {
 const ids=new Set(encounters.filter(e=>e.patientId===patientId).map(e=>e.id));
 return rows.filter(row=>Number(row.patient_id)===patientId||ids.has(Number(row.encounter_id)));
}
export function resultCsv(rows) {
 const field=value=>'"'+String(value??'').replace(/^[\t\r\n ]*[=+@-]/,"'$&").replaceAll('"','""')+'"';
 return '\ufeff'+[['Analiză','Rezultat','Unitate','Data'],...rows.map(r=>[r.test_code,r.result,r.unit,r.created_at])].map(row=>row.map(field).join(',')).join('\r\n');
}
