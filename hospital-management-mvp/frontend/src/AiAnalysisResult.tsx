import { parseAiDetails } from './aiPresentation.mjs';
import type { AiAnalysis } from './api';

type Props={analysis:AiAnalysis;onRegenerate?:()=>void;busy:boolean};
export default function AiAnalysisResult({analysis,onRegenerate,busy}:Props) {
  const view=parseAiDetails(analysis.details);
  const options=view.options.length?view.options:view.conditions[0]?.managementOptions||[];
  const investigations=view.investigations.length?view.investigations:view.conditions[0]?.investigations||[];
  const referral=view.referral||view.conditions[0]?.referral;
  return <div className="ai-result">
    {view.hasGuidance?<>
      <section className="ai-result-section"><h3>Ce pot indica simptomele</h3>
        {view.conditions.length?<ol className="ai-hypotheses">{view.conditions.map((condition,index)=><li key={condition.code||index}>
          <strong>{condition.label}</strong>{index===0&&<span className="ai-primary-label">{view.tentative?'Ipoteză de explorat':'Ipoteza principală'}</span>}
          {condition.differentials.length>0&&<p>Alte explicații de explorat: {condition.differentials.join('; ')}.</p>}
          {index>0&&condition.managementOptions.length>0&&<details><summary>Conduită pentru această ipoteză</summary><ul>{condition.managementOptions.map((text,i)=><li key={i}>{text}</li>)}</ul></details>}
        </li>)}</ol>:<p>{analysis.summary}</p>}
        {view.tentative&&view.conditions.length>0&&<p className="ai-match-note">Durata și simptomele asociate pot ajuta la diferențiere.</p>}
      </section>
      {options.length>0&&<section className="ai-result-section"><h3>Tratament și conduită orientativă</h3><p className="ai-section-caption">Pentru ipoteza principală</p><ul>{options.map((text,i)=><li key={i}>{text}</li>)}</ul></section>}
      <section className="ai-result-section medication-section"><h3>Opțiuni medicamentoase</h3>
        {!view.hasMedicationModule?<p className="medication-empty">Acest rezultat a fost generat de versiunea anterioară a serviciului AI. Regenerează analiza pentru a verifica opțiunile medicamentoase.</p>:<>
          {view.medications.length>0?<><p className="ai-section-caption">{view.medicationNote}</p><div className="medication-options">{view.medications.map(m=><article className="medication-option" key={m.name}><div className="medication-heading"><strong>{m.name}</strong><span>{m.status==='review'?'Necesită verificări':'Opțiune de evaluat'}</span></div><p>{m.indication}</p><details><summary>Verificări și surse</summary><ul>{m.checks.map((check,i)=><li key={i}>{check}</li>)}</ul>{m.sources.map(source=><a key={source.url} href={source.url} target="_blank" rel="noopener noreferrer">{source.title}</a>)}</details></article>)}</div></>:<p className="medication-empty">{view.withheld.length?'Opțiunile disponibile au fost excluse pe baza contextului de mai jos.':view.medicationNote||'Nu există o opțiune medicamentoasă eligibilă pentru datele introduse.'}</p>}
          {view.withheld.length>0&&<div className="medication-exclusions"><h4>Opțiuni excluse</h4>{view.withheld.map(m=><p key={m.name}><strong>{m.name}:</strong> {m.reasons.join('; ')}</p>)}</div>}
        </>}
      </section>
      {investigations.length>0&&<section className="ai-result-section"><h3>Investigații posibile</h3><ul>{investigations.map((text,i)=><li key={i}>{text}</li>)}</ul></section>}
      {referral&&<p className="ai-referral"><strong>Specialitate / direcționare:</strong> {referral}</p>}
      {view.questions.length>0&&<details className="ai-technical"><summary>Ce informații ar mai ajuta?</summary><ul>{view.questions.map((text,i)=><li key={i}>{text}</li>)}</ul></details>}
    </>:<div className="ai-legacy"><p>Rezultat din versiunea anterioară. Generează o analiză nouă pentru ipoteze, conduită și investigații.</p><details><summary>Vezi rezultatul salvat</summary><p>{analysis.summary}</p>{view.suggestions.map((text,i)=><p key={i}>{text}</p>)}</details></div>}
    <details className="ai-technical"><summary>Simptome analizate și detalii model</summary>
      {view.symptoms&&<p><strong>Simptome:</strong> {view.symptoms}</p>}{view.observations&&<p><strong>Observații:</strong> {view.observations}</p>}
      {view.history.length>0&&<ul>{view.history.map((text,i)=><li key={i}>{text}</li>)}</ul>}
      <p>{analysis.evidence}</p><ul>{view.basis.map((text,i)=><li key={i}>{text}</li>)}</ul>
      <span className="analysis-version">{analysis.modelVersion} · consultația #{analysis.encounterId}</span>
    </details>
    {onRegenerate&&<button type="button" className="btn outline-btn small ai-regenerate" disabled={busy} onClick={onRegenerate}>{busy?'Se analizează…':'Regenerează analiza'}</button>}
  </div>;
}
