const record = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
const strings = value => Array.isArray(value) ? value.filter(item => typeof item === 'string' && item.trim()) : [];

const medicationList = value => Array.isArray(value) ? value.map(record).filter(item=>typeof item.name==='string'&&item.name.trim()).slice(0,10).map(item=>({
  name:item.name,indication:typeof item.indication==='string'?item.indication:'',status:['consider','review','blocked'].includes(item.status)?item.status:'review',
  checks:strings(item.checks),reasons:strings(item.reasons),sources:Array.isArray(item.sources)?item.sources.map(record).filter(source=>typeof source.title==='string'&&typeof source.url==='string'&&/^https:\/\//.test(source.url)).map(source=>({title:source.title,url:source.url})):[],
})) : [];

export function parseAiDetails(details) {
  let parsed = {};
  try { parsed = record(JSON.parse(details || '{}')); } catch { /* Legacy entries can have no structured details. */ }
  const conditions = Array.isArray(parsed.possibleConditions) ? parsed.possibleConditions.map(record)
    .filter(item => typeof item.label === 'string' && item.label.trim())
    .slice(0, 3).map(item => ({
      code: typeof item.code === 'string' ? item.code : '', label: item.label,
      differentials: strings(item.differentials), managementOptions: strings(item.managementOptions),
      investigations: strings(item.investigations), referral: typeof item.referral === 'string' ? item.referral : '',
    })) : [];
  const input = record(parsed.input);
  return {
    hasMedicationModule:Array.isArray(parsed.medicationOptions),
    medications:medicationList(parsed.medicationOptions),withheld:medicationList(parsed.withheldMedications),
    medicationNote:typeof parsed.medicationNote==='string'?parsed.medicationNote:'',
    conditions, suggestions: strings(parsed.diagnosticSuggestions), options: strings(parsed.therapyOptions),
    investigations: strings(parsed.investigations), referral: typeof parsed.referral === 'string' ? parsed.referral : '',
    tentative: parsed.confidenceLevel === 'tentative', questions: strings(parsed.clarificationQuestions),
    basis: strings(parsed.basis), priority: typeof parsed.priority === 'string' ? parsed.priority : '',
    symptoms: typeof input.symptoms === 'string' ? input.symptoms : '',
    observations: typeof input.observations === 'string' ? input.observations : '', history: strings(input.medicalHistory),
    hasGuidance: Array.isArray(parsed.possibleConditions),
  };
}

export function aiPriorityLabel(value) {
  return ({GENERAL_REVIEW:'Evaluare obișnuită',EXTENDED_REVIEW:'Evaluare extinsă',ATTENTION_FLAG:'Evaluare prioritară',
    DEMO_GENERAL:'Evaluare obișnuită',DEMO_EXTENDED:'Evaluare extinsă',DEMO_ATTENTION:'Evaluare prioritară',
    UNCLASSIFIED_DEMO:'De clarificat',NOT_ASSESSED:'De clarificat'})[value] || 'Analiză simptome';
}
