"""Small, source-linked academic formulary. Summaries are paraphrases, not copied guidelines."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
SOURCES={
 'paracetamol':{'title':'NHS — Paracetamol for adults','url':'https://www.nhs.uk/medicines/paracetamol-for-adults/'},
 'allergy':{'title':'NHS — Cetirizine','url':'https://www.nhs.uk/medicines/cetirizine/'},
 'reflux':{'title':'NHS — Heartburn and acid reflux','url':'https://www.nhs.uk/conditions/heartburn-and-acid-reflux/'},
 'interaction':{'title':'NHS SPS — Clopidogrel and PPIs','url':'https://www.sps.nhs.uk/articles/using-clopidogrel-with-proton-pump-inhibitors-ppis/'},
 'constipation':{'title':'NHS — Macrogol','url':'https://www.nhs.uk/medicines/macrogol/'},
 'skin':{'title':'Hydrocortisone 1% — SmPC','url':'https://www.medicines.org.uk/emc/product/13104/smpc'},
 'uti':{'title':'NICE NG109 — Lower UTI antimicrobial prescribing','url':'https://www.nice.org.uk/guidance/ng109/chapter/Recommendations'},
}
entries=[
 ('paracetamol','Paracetamol',['D03','D04','D06','D14'],'Opțiune simptomatică pentru durere sau febră; nu tratează cauza.', ['paracetamol'],['hepatic','duplicate'],['paracetamol']),
 ('cetirizine','Cetirizină',['D02'],'Antihistaminic pentru manifestări de rinită alergică.', ['cetirizin','cetirizine','hidroxizin','hydroxyzine'],['renal','duplicate'],['allergy']),
 ('alginate','Alginat de sodiu',['D05'],'Opțiune pentru ameliorarea arsurilor și refluxului.', ['alginat','alginate'],['renal','duplicate'],['reflux']),
 ('omeprazole','Omeprazol',['D05'],'Inhibitor de pompă de protoni, de discutat dacă simptomatologia de reflux justifică tratament.', ['omeprazol','omeprazole'],['interaction','duplicate'],['reflux','interaction']),
 ('macrogol','Macrogol',['D07'],'Laxativ osmotic pentru constipație după excluderea semnelor de obstrucție.', ['macrogol','polietilenglicol','polyethylene'],['obstruction','renal','duplicate'],['constipation']),
 ('hydrocortisone','Hidrocortizon topic 1%',['D08'],'Opțiune locală pentru dermatită inflamatorie; se exclude o infecție cutanată și se verifică zona de aplicare.', ['hidrocortizon','hydrocortisone'],['skin_infection','duplicate'],['skin']),
 ('nitrofurantoin','Nitrofurantoină',['D10'],'De evaluat exclusiv pentru infecție urinară joasă necomplicată, după verificarea funcției renale, culturii și contextului clinic.', ['nitrofurantoin'],['renal','fever','duplicate'],['uti']),
]
checks=['Vârstă și sarcină/alăptare','Alergii și tratamente concomitente','Indicație, contraindicații și doză din prospectul produsului disponibil local']
items=[dict(id=i,name=n,profiles=p,indication=d,allergyTerms=a,risks=r,sourceIds=s,checks=checks+(['eGFR, urocultură, absența febrei/durerii de flanc; nu pentru pielonefrită'] if i=='nitrofurantoin' else [])) for i,n,p,d,a,r,s in entries]
(ROOT/'artifacts/medication_catalog.json').write_text(json.dumps({'version':'academic-formulary-v1','reviewedOn':'2026-11-12','clinicalValidation':False,'sources':SOURCES,'medications':items},ensure_ascii=False,indent=2)+'\n')
