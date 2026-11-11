"""Train a symptom-only classifier on the existing case-disjoint synthetic splits."""
import hashlib,json,gzip,time
from pathlib import Path
from dataset import load_split,DATA
from learning import Tfidf,fit_logistic,classification_metrics,features
ROOT=Path(__file__).resolve().parent
start=time.perf_counter()
train,validation=load_split('train'),load_split('validation')
text=lambda r:r['symptoms_text']
vectorizer=Tfidf.fit([text(r) for r in train],min_df=2)
model,training=fit_logistic([vectorizer.transform(text(r)) for r in train],[r['target_syndrome_code'] for r in train],[vectorizer.transform(text(r)) for r in validation],[r['target_syndrome_code'] for r in validation],len(vectorizer.vocabulary),epochs=45)
test=load_split('test')
expected=[r['target_syndrome_code'] for r in test]
predicted=[model.predict(vectorizer.transform(text(r)))[0] for r in test]
seen={tuple(sorted(features(text(r)).items())) for r in train}
novel=[r for r in test if tuple(sorted(features(text(r)).items())) not in seen]
report={'version':'short-symptoms-v1','clinicalValidation':False,'features':['symptoms_text'],'counts':{'train':len(train),'validation':len(validation),'test':len(test)},'sourceSha256':{s:hashlib.sha256((DATA/f'{s}.csv').read_bytes()).hexdigest() for s in ['train','validation','test']},'test':classification_metrics(expected,predicted,model.classes),'testNovelSymptomTextCount':len(novel),'novelSymptomTest':classification_metrics([r['target_syndrome_code'] for r in novel],[model.predict(vectorizer.transform(text(r)))[0] for r in novel],model.classes) if novel else None,'limitation':'Case identities are disjoint; synthetic symptom templates can repeat across splits. Test metrics are not clinical accuracy.','training':training,'elapsedSeconds':time.perf_counter()-start}
p=ROOT/'artifacts/short_symptoms.json.gz'
with gzip.open(p,'wt',encoding='utf-8') as f: json.dump({'version':report['version'],'vectorizer':vectorizer.to_dict(),'model':model.to_dict(),'clinicalValidation':False},f)
report['modelSha256']=hashlib.sha256(p.read_bytes()).hexdigest()
(ROOT/'artifacts/short_symptoms_evaluation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'accuracy':report['test']['accuracy'],'novelExamples':len(novel),'seconds':report['elapsedSeconds']}))
