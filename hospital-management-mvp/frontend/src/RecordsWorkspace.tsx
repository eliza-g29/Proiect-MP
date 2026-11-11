import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Download, Plus, X } from 'lucide-react';
import { request, post, patch, uploadDocument, downloadDocument, nameOfPatient, type Profile, type Patient, type Encounter, type AiAnalysis } from './api';

type Row = Record<string, string | number | null>;
type Field = { key: string; label: string; type?: string; optional?: boolean; max?: number; options?: [string, string][] };
type Category = { key: string; label: string; scope: 'patient' | 'encounter' | 'analysis' | 'admin'; fields: Field[] };
const patient: Field = { key: 'patient_id', label: 'Pacient', type: 'patient' };
const encounter: Field = { key: 'encounter_id', label: 'Consultație', type: 'encounter' };
const coding: Field[] = [{key:'coding_system', label:'Sistem de codificare', max:120}, {key:'coding_version',label:'Versiunea codificării',max:80}];
const categories: Category[] = [
  {key:'allergies',label:'Alergii',scope:'patient',fields:[patient,{key:'substance',label:'Substanță',max:200},{key:'reaction',label:'Reacție',type:'textarea'}, {key:'status',label:'Stare',options:[['ACTIVE','Activă'],['INACTIVE','Inactivă'],['RESOLVED','Rezolvată']]}]},
  {key:'diagnoses',label:'Diagnostice',scope:'encounter',fields:[encounter,{key:'code',label:'Cod diagnostic',max:80},...coding,{key:'description',label:'Diagnostic confirmat de personalul medical',type:'textarea'}]},
  {key:'observations',label:'Observații',scope:'encounter',fields:[encounter,{key:'type',label:'Tip / cod parametru',max:120},...coding,{key:'value',label:'Valoare',max:300},{key:'unit',label:'Unitate (sau „fără unitate”)',max:80},{key:'measured_at',label:'Momentul măsurării',type:'datetime-local'}]},
  {key:'lab-results',label:'Analize de laborator',scope:'patient',fields:[patient,{...encounter,optional:true},{key:'test_code',label:'Cod analiză',max:120},...coding,{key:'result',label:'Rezultat',type:'textarea'},{key:'unit',label:'Unitate (sau „fără unitate”)',max:80}]},
  {key:'prescriptions',label:'Rețete',scope:'encounter',fields:[encounter,{key:'medicine',label:'Medicament',max:200},{key:'dosage',label:'Dozaj introdus de personalul medical',max:300},{key:'instructions',label:'Instrucțiuni',type:'textarea'}]},
  {key:'medical-documents',label:'Documente',scope:'patient',fields:[patient,{key:'document_type',label:'Tip document',max:120}]},
  {key:'ai-reviews',label:'Revizuiri AI',scope:'analysis',fields:[{key:'analysis_id',label:'Analiză AI',type:'analysis'},{key:'decision',label:'Decizie umană',options:[['NEEDS_REVIEW','Necesită revizuire'],['ACCEPTED','Acceptată'],['MODIFIED','Modificată de medic'],['REJECTED','Respinsă']]},{key:'notes',label:'Motiv / observații',type:'textarea'},{key:'revised_recommendation',label:'Recomandarea revizuită (obligatorie pentru Modificată)',type:'textarea',optional:true}]},
  {key:'departments',label:'Departamente',scope:'admin',fields:[{key:'name',label:'Denumire',max:160}]},
  {key:'staff-departments',label:'Personal în departamente',scope:'admin',fields:[{key:'staff_id',label:'Membru al personalului',type:'staff'},{key:'department_id',label:'Departament',type:'department'},{key:'valid_from',label:'De la',type:'date'},{key:'valid_to',label:'Până la',type:'date',optional:true}]}
];
for(const category of categories)if(category.scope==='patient'||category.scope==='encounter')category.fields.push({key:'patient_visible',label:'Vizibil pacientului (implicit Da)',optional:true,options:[['true','Da'],['false','Nu · informație internă']]});
const extraLabels: Record<string,string> = {id:'ID',created_at:'Înregistrat la',confirmed_by:'Confirmat de',prescribed_by:'Prescris de',reviewer_id:'Revizuit de',reviewed_at:'Revizuit la',original_name:'Fișier',size_bytes:'Dimensiune (octeți)',uploaded_by:'Încărcat de'};

export default function RecordsWorkspace({token,profile,patients,encounters,analyses,initialKind='allergies',initialAnalysisId,initialPatientId}: {token:string;profile:Profile;patients:Patient[];encounters:Encounter[];analyses:AiAnalysis[];initialKind?:string;initialAnalysisId?:number;initialPatientId?:number}) {
  const [kind,setKind]=useState(initialKind);
  const [rows,setRows]=useState<Row[]>([]);
  const [departments,setDepartments]=useState<Row[]>([]);
  const [staff,setStaff]=useState<Row[]>([]);
  const [fields,setFields]=useState<Record<string,string>>(initialAnalysisId&&initialKind==='ai-reviews'?{analysis_id:String(initialAnalysisId),decision:'NEEDS_REVIEW'}:{});
  const [file,setFile]=useState<File|null>(null);
  const [editing,setEditing]=useState(Boolean(initialAnalysisId&&initialKind==='ai-reviews'));
  const [editId,setEditId]=useState<number|null>(null);
  const [loading,setLoading]=useState(false);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const allowed=categories.filter(c=>(profile.role!=='PATIENT' || (c.scope!=='admin' && c.scope!=='analysis')) && (profile.role!=='STAFF' || c.scope!=='analysis'));
  const category=allowed.find(c=>c.key===kind) || allowed[0];
  const canWrite=profile.role!=='PATIENT' && (category.scope!=='admin' || profile.role==='ADMIN') && (profile.role!=='STAFF' || !['diagnoses','prescriptions','ai-reviews'].includes(kind));
  const load=useCallback(async () => {
    setLoading(true);setError('');
    try { setRows(await request<Row[]>(`/records/${kind}${initialPatientId?`?patientId=${initialPatientId}`:''}`,token)); }
    catch(e) { setError((e as Error).message); }
    finally { setLoading(false); }
  },[kind,token,initialPatientId]);
  useEffect(()=>{let active=true;setRows([]);setLoading(true);setError('');
    request<Row[]>(`/records/${kind}${initialPatientId?`?patientId=${initialPatientId}`:''}`,token).then(data=>{if(active)setRows(data);}).catch(e=>{if(active)setError((e as Error).message);}).finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[kind,token,initialPatientId]);
  useEffect(()=>{
    if(profile.role!=='ADMIN')return;
    let active=true;
    Promise.all([request<Row[]>('/records/departments',token),request<Row[]>('/staff',token)])
      .then(([d,s])=>{if(active){setDepartments(d);setStaff(s);}}).catch(e=>{if(active)setError((e as Error).message);});
    return ()=>{active=false;};
  },[token,profile.role,kind]);
  const options=(f:Field): [string,string][] => {
    if(f.options)return f.options;
    if(f.type==='patient')return patients.filter(p=>!initialPatientId||p.id===initialPatientId).map(p=>[String(p.id),nameOfPatient(p)]);
    if(f.type==='encounter')return encounters.filter(e=>(!initialPatientId||e.patientId===initialPatientId)&&(!fields.patient_id || String(e.patientId)===fields.patient_id)).map(e=>[String(e.id),`#${e.id} · ${nameOfPatient(patients.find(p=>p.id===e.patientId) || {firstName:'Pacient',lastName:String(e.patientId)} as Patient)} · ${e.symptoms.slice(0,60)}`]);
    if(f.type==='analysis')return analyses.map(a=>[String(a.id),`#${a.id} · consultația #${a.encounterId} · ${a.category}`]);
    if(f.type==='department')return departments.map(d=>[String(d.id),String(d.name)]);
    if(f.type==='staff')return staff.map(s=>[String(s.id),`${s.name} (${s.role})`]);
    return [];
  };
  const update=(key:string,value:string)=>setFields(old=>({...old,[key]:value,...(key==='patient_id'?{encounter_id:''}:{})}));
  async function submit(e:FormEvent) {
    e.preventDefault();setSaving(true);setError('');setNotice('');
    try {
      if(kind==='medical-documents') {
        if(!file)throw new Error('Selectează un fișier.');
        if(file.size>10*1024*1024)throw new Error('Fișierul depășește 10 MB.');
        await uploadDocument(token,fields.patient_id,fields.document_type,file,fields.patient_visible!=='false');
      } else {
        const payload:Record<string,string|null>={};category.fields.forEach(f=>{payload[f.key]=fields[f.key] || null;});
        if(editId)await patch(`/records/${kind}/${editId}`,token,payload);else await post(`/records/${kind}`,token,payload);
      }
      setEditing(false);setFields({});setFile(null);setNotice('Înregistrarea a fost salvată.');await load();
    } catch(e) {setError((e as Error).message);} finally {setSaving(false);}
  }
  const columns=[...category.fields.map(f=>f.key),...Object.keys(extraLabels).filter(k=>k!=='id' && rows.some(r=>r[k]!=null))];
  if(kind==='medical-documents')columns.push('download');
  const display=(row:Row,key:string) => {
    const value=row[key];if(value==null)return '—';
    if(key==='patient_id')return nameOfPatient(patients.find(p=>p.id===Number(value))) || `#${value}`;
    if(key==='department_id')return String(departments.find(d=>d.id===Number(value))?.name || `#${value}`);
    const field=category.fields.find(f=>f.key===key);
    return field?.options?.find(([v])=>v===String(value))?.[1] || String(value);
  };
  return <>
    <div className="page-intro"><div className="section-heading"><div><h2>Dosar medical extins</h2><p>Înregistrări clinice și organizarea unității, conform drepturilor contului.</p></div>{canWrite && <button className="btn primary-btn" disabled={saving || loading} onClick={()=>{setEditing(true);setEditId(null);setFields(initialAnalysisId && kind==='ai-reviews'?{analysis_id:String(initialAnalysisId)}:initialPatientId?{patient_id:String(initialPatientId)}:{});setFile(null);setError('');setNotice('');}}><Plus size={17}/>Adaugă înregistrare</button>}</div></div>
    <nav className="record-tabs" aria-label="Categorii dosar">{allowed.map(c=><button type="button" className={`btn ${kind===c.key?'primary-btn':'outline-btn'}`} key={c.key} aria-pressed={kind===c.key} disabled={saving} onClick={()=>{setKind(c.key);setEditing(false);setEditId(null);setFields({});setFile(null);setNotice('');}}>{c.label}</button>)}</nav>
    {error && <div className="notice error" role="alert">{error}</div>}{notice && <div className="notice success" role="status">{notice}</div>}
    {kind==='ai-reviews' && <p className="muted">Revizuirea se salvează separat de diagnosticul și rețeta consultației.</p>}
    {editing && canWrite && <section className="panel"><form className="modal-form record-form" onSubmit={submit}><div className="section-heading"><h3>{category.label} · înregistrare nouă</h3><button type="button" className="icon-btn" aria-label="Închide formularul" onClick={()=>setEditing(false)}><X size={18}/></button></div><div className="record-fields">
      {category.fields.map(f=><label key={f.key}>{f.label}{f.optional?' (opțional)':' *'}{f.options || ['patient','encounter','analysis','department','staff'].includes(f.type || '') ? <select required={!f.optional} disabled={!!editId && ['patient_id','encounter_id','analysis_id','staff_id','department_id'].includes(f.key)} value={fields[f.key] || ''} onChange={e=>update(f.key,e.target.value)}><option value="">Selectează</option>{options(f).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select> : f.type==='textarea' ? <textarea required={!f.optional} rows={3} maxLength={4000} value={fields[f.key] || ''} onChange={e=>update(f.key,e.target.value)}/> : <input required={!f.optional} type={f.type || 'text'} maxLength={f.max} value={fields[f.key] || ''} onChange={e=>update(f.key,e.target.value)}/>}</label>)}
      {kind==='medical-documents' && <label>Fișier * (maxim 10 MB)<input type="file" required onChange={e=>setFile(e.target.files?.[0] || null)}/></label>}
    </div><div className="modal-actions"><button type="button" className="btn outline-btn" onClick={()=>setEditing(false)}>Renunță</button><button className="btn primary-btn" disabled={saving || loading}>{saving?'Se salvează…':'Salvează'}</button></div></form></section>}
    <section className="panel table-panel record-results"><div className="table-toolbar"><strong>{category.label}</strong><span className="result-count">{loading?'Se încarcă…':`${rows.length} înregistrări`}</span></div>{rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>ID</th>{canWrite&&!['medical-documents','ai-reviews'].includes(kind)&&<th>Acțiuni</th>}{columns.map(k=><th key={k}>{category.fields.find(f=>f.key===k)?.label || extraLabels[k] || 'Descarcă'}</th>)}</tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>#{row.id}</td>{canWrite&&!['medical-documents','ai-reviews'].includes(kind)&&<td><button className="btn outline-btn small" disabled={saving} onClick={()=>{const values:Record<string,string>={};category.fields.forEach(f=>{values[f.key]=row[f.key]==null?'':String(row[f.key]);if(f.type==='datetime-local')values[f.key]=values[f.key].slice(0,16);});setFields(values);setEditId(Number(row.id));setEditing(true);setError('');}}>Actualizează</button></td>}{columns.map(k=><td key={k}>{k==='download'?<button type="button" className="btn outline-btn small" onClick={()=>{setError('');void downloadDocument(token,Number(row.id),String(row.original_name)).catch(e=>setError((e as Error).message));}}><Download size={15}/>Descarcă</button>:display(row,k)}</td>)}</tr>)}</tbody></table></div>:!loading && <div className="empty"><h3>Nu există înregistrări accesibile</h3><p>{canWrite?'Folosește „Adaugă înregistrare” pentru a începe.':'Înregistrările introduse de personalul medical vor apărea aici.'}</p></div>}</section>
  </>;
}
