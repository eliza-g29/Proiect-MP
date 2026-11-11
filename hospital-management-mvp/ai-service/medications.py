"""Source-linked medication options with independent exclusion gates; no automatic prescription."""
import re
import json
import unicodedata
from pathlib import Path
from learning import LogisticModel
ROOT=Path(__file__).resolve().parent
CATALOG=json.loads((ROOT/'artifacts/medication_catalog.json').read_text())
MODEL_PATH=ROOT/'artifacts/medication_model.json'
MODEL=LogisticModel.from_dict(json.loads(MODEL_PATH.read_text())['model']) if MODEL_PATH.exists() else None
FLAGS=['allergy','unknown_allergies','minor','unknown_age','pregnancy','unknown_pregnancy','renal','unknown_renal','hepatic','interaction','duplicate','obstruction','skin_infection','fever','unknown_medicines']
def normalize(text):
    return ''.join(c for c in unicodedata.normalize('NFKD',str(text).lower()) if not unicodedata.combining(c))
def assessment(drug,code,flags):
    if code not in drug['profiles']: return 'irrelevant'
    blockers=['allergy','minor','pregnancy']+[r for r in drug['risks'] if r in flags]
    if any(r in flags for r in blockers): return 'blocked'
    unknown=['unknown_allergies','unknown_age','unknown_pregnancy','unknown_medicines']
    if 'renal' in drug['risks']: unknown+=['unknown_renal']
    return 'review' if any(r in flags for r in unknown) else 'consider'
def features(drug,code,flags):
    match=code in drug['profiles']
    risk=any(r in flags for r in ['allergy','minor','pregnancy']+drug['risks'])
    missing=any(r in flags for r in ['unknown_allergies','unknown_age','unknown_pregnancy','unknown_medicines']+(['unknown_renal'] if 'renal' in drug['risks'] else []))
    return [(0,1.0),(1,float(match)),(2,float(risk)),(3,float(missing))]+[(4+i,float(r in flags)) for i,r in enumerate(FLAGS)]
def flags_for(drug,context,symptoms):
    context=context or {}; flags=set()
    age=context.get('ageYears')
    if age is None: flags.add('unknown_age')
    elif age<18: flags.add('minor')
    allergies=context.get('allergies',[])
    if not context.get('allergiesReviewed',False): flags.add('unknown_allergies')
    allergy_text=normalize(' '.join(a for a in allergies if not normalize(a).startswith(('fara ','nicio ','nici o '))))
    if any(term in allergy_text for term in drug['allergyTerms']) or any(term in allergy_text for term in ['alergie medicamentoas','alergii medicamentoas','medicamente neprecizate','substanta necunoscuta']):
        flags.add('allergy')
    pregnancy=context.get('pregnancyStatus','UNKNOWN')
    if pregnancy=='PREGNANT_OR_BREASTFEEDING': flags.add('pregnancy')
    elif pregnancy!='NOT_APPLICABLE_OR_EXCLUDED': flags.add('unknown_pregnancy')
    egfr=context.get('egfr')
    if egfr is None: flags.add('unknown_renal')
    elif egfr<60: flags.add('renal') # conservative review gate; not a drug dosing threshold
    history=normalize(' '.join(context.get('conditions',[]))+' '+symptoms)
    if any(t in history for t in ['renal','rinichi']): flags.add('renal')
    if any(t in history for t in ['hepat','ciroza','ficat']): flags.add('hepatic')
    meds=normalize(' '.join(context.get('currentMedicines',[])))
    if not context.get('medicinesReviewed',False): flags.add('unknown_medicines')
    if any(t in meds for t in [normalize(drug['name']),drug['id']]): flags.add('duplicate')
    if drug['id']=='omeprazole' and 'clopidogrel' in meds: flags.add('interaction')
    text=normalize(symptoms)
    temperatures=re.findall(r'(?:temperatura|\bt)\s*[=:]?\s*(\d{2}(?:[.,]\d+)?)',text)
    if any(float(t.replace(',','.'))>=38 for t in temperatures): flags.add('fever')
    if any(t in text for t in ['febra','febr','flanc','frisoane']): flags.add('fever')
    if any(t in text for t in ['varsaturi','oprirea tranzitului','ocluz','obstruct']): flags.add('obstruction')
    if any(t in text for t in ['puroi','infectie','infectata','supur']): flags.add('skin_infection')
    return flags
REASONS={'allergy':'alergie medicamentoasă declarată sau insuficient precizată','minor':'vârsta pediatrică necesită un circuit separat','pregnancy':'sarcină/alăptare necesită verificare specifică','renal':'funcție renală redusă sau antecedente renale','hepatic':'antecedente hepatice','interaction':'posibilă interacțiune cu clopidogrel','duplicate':'medicament deja declarat în tratament','obstruction':'simptome digestive care necesită clarificare','skin_infection':'posibilă infecție cutanată','fever':'febră, frisoane sau durere de flanc; se exclude infecția urinară înaltă'}
def medication_options(code,context=None,symptoms='',supported=True,urgent=False):
    red_flags=any(t in normalize(symptoms) for t in ['durere toracica','dispnee severa','confuzie','pierderea constientei','varsaturi cu sange','scaun negru','deficit neurologic'])
    if not code:
        return [],[],'Nu există suficiente indicii pentru selectarea unei opțiuni medicamentoase.'
    if urgent or red_flags:
        return [],[],'Evaluarea prioritară a pacientului precedă selectarea tratamentului medicamentos.'
    if not supported:
        return [],[],'Ipoteza necesită clarificare: completează durata, intensitatea și simptomele asociate înainte de alegerea medicamentului.'
    if not any(code in drug['profiles'] for drug in CATALOG['medications']):
        return [],[],'Catalogul actual nu conține o opțiune medicamentoasă pentru această ipoteză. Conduita și investigațiile sunt afișate mai jos.'
    options=[]; withheld=[]
    for drug in CATALOG['medications']:
        flags=flags_for(drug,context,symptoms); status=assessment(drug,code,flags)
        if status=='irrelevant': continue
        sources=[CATALOG['sources'][i] for i in drug['sourceIds']]
        score=MODEL.probabilities(features(drug,code,flags))[MODEL.classes.index('consider')] if MODEL else 0
        reasons=[REASONS[r] for r in ['allergy','minor','pregnancy']+drug['risks'] if r in flags and r in REASONS]
        item={'name':drug['name'],'indication':drug['indication'],'status':status,'checks':drug['checks'],'sources':sources,'reasons':reasons,'rankingScore':round(score,4)}
        if status=='blocked': withheld.append(item)
        else: options.append(item)
    options.sort(key=lambda item:item['rankingScore'],reverse=True)
    return options,withheld,'Opțiuni de discutat la revizuire; doza și schema se stabilesc separat.'
