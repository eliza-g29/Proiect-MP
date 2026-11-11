"""Train routing on transparent synthetic rules; evaluates software behavior, not clinical efficacy."""
import hashlib,json,random
from pathlib import Path
from learning import fit_logistic,classification_metrics
from medications import CATALOG,FLAGS,assessment,features
ROOT=Path(__file__).resolve().parent
rng=random.Random(20261112)
# Group by complete risk context, keeping all profile/drug pairs in one split.
contexts=[set()]+[{f} for f in FLAGS]
while len(contexts)<100:
    flags={f for f in FLAGS if rng.random()<.16}
    if flags not in contexts: contexts.append(flags)
rng.shuffle(contexts)
splits={'train':contexts[:70],'validation':contexts[70:85],'test':contexts[85:]}
records={}; vectors={}; labels={}
for split,groups in splits.items():
    records[split]=[];vectors[split]=[];labels[split]=[]
    for flags in groups:
        group=hashlib.sha256('|'.join(sorted(flags)).encode()).hexdigest()[:16]
        for i in range(1,31):
            code=f'D{i:02}'
            for drug in CATALOG['medications']:
                label=assessment(drug,code,flags)
                records[split].append({'scenarioGroup':group,'profileCode':code,'medicationId':drug['id'],'flags':sorted(flags),'targetDecision':label,'sourceIds':drug['sourceIds'],'labelOrigin':'SYNTHETIC_CATALOG_RULES'})
                vectors[split].append(features(drug,code,flags));labels[split].append(label)
    target=ROOT/'data/medication_v1'/f'{split}.jsonl';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in records[split]))
model,training=fit_logistic(vectors['train'],labels['train'],vectors['validation'],labels['validation'],4+len(FLAGS),epochs=35,patience=8)
artifact={'format':'medflow-medication-routing-v1','version':'synthetic-medication-routing-v1','model':model.to_dict(),'clinicalValidation':False,'labelOrigin':'source-linked, manually encoded catalog rules; synthetic combinations','training':training}
path=ROOT/'artifacts/medication_model.json';path.write_text(json.dumps(artifact)+'\n')
report={'version':artifact['version'],'clinicalValidation':False,'scope':'Routing/exclusion rule imitation only; no clinical treatment effectiveness or dosing evaluation','splitUnit':'scenarioGroup (complete risk context)','counts':{s:len(v) for s,v in records.items()},'catalogSha256':hashlib.sha256((ROOT/'artifacts/medication_catalog.json').read_bytes()).hexdigest(),'modelSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'dataSha256':{s:hashlib.sha256((ROOT/'data/medication_v1'/f'{s}.jsonl').read_bytes()).hexdigest() for s in records},'test':classification_metrics(labels['test'],[model.predict(v)[0] for v in vectors['test']],model.classes)}
(ROOT/'artifacts/medication_evaluation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'counts':report['counts'],'testAccuracy':report['test']['accuracy'],'clinicalValidation':False}))
