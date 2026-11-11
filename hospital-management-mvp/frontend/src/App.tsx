import NotificationBell from './NotificationBell';
import PatientHome from './PatientHome';
import PatientRecord, {type RecordSection} from './PatientRecord';
import {clinicalText} from './recordPresentation.mjs';
import AvailabilityDirectory from './AvailabilityDirectory';
import AppointmentAvailability from './AppointmentAvailability';
import {localDay} from './availability.mjs';
import AiAnalysisResult from './AiAnalysisResult';
import { aiPriorityLabel } from './aiPresentation.mjs';
import PageErrorBoundary from './PageErrorBoundary';
import AccountSettings from './AccountSettings';
import OperationsWorkspace from './OperationsWorkspace';
import RecordsWorkspace from './RecordsWorkspace';
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
  Activity, ArrowDownRight, ArrowRight, Bell, BrainCircuit, CalendarDays, Check,
  ChevronDown, ChevronRight, ClipboardList, Clock3, FileHeart, HeartPulse,
  Hospital, LayoutDashboard, LockKeyhole, LogOut, Menu, Plus, Search, ShieldCheck,
  ShieldAlert, Stethoscope, UsersRound, X, type LucideIcon
} from 'lucide-react';
import {
  ApiError,
  type Appointment,
  type Dashboard,
  type Doctor,
  type Encounter,
  type AiAnalysis,
  type Patient,
  type Profile,
  type Snapshot,
  type Tab,
  fmtDate,
  fmtDateTime,
  nameOfPatient,
  patch,
  post,
  request
} from './api';

const nav: { tab: Tab; label: string; icon: LucideIcon; restricted?: 'CLINICAL' | 'ADMIN' }[] = [
  { tab: 'dashboard', label: 'Prezentare generală', icon: LayoutDashboard },
  { tab: 'patients', label: 'Pacienți', icon: UsersRound },
  { tab: 'appointments', label: 'Programări', icon: CalendarDays },
  { tab: 'encounters', label: 'Consultații', icon: FileHeart },
  { tab: 'ai', label: 'Asistent AI', icon: BrainCircuit, restricted: 'CLINICAL' },
  { tab: 'doctors', label: 'Echipă medicală', icon: Stethoscope },
  { tab: 'records', label: 'Dosar medical extins', icon: FileHeart },
  { tab: 'schedule', label: 'Calendar și disponibilitate', icon: CalendarDays },
  { tab: 'notifications', label: 'Notificări', icon: Bell },
  { tab: 'accounts', label: 'Conturi și drepturi', icon: UsersRound, restricted: 'ADMIN' },
  { tab: 'sharing', label: 'Colaborare și acces', icon: ShieldCheck, restricted: 'ADMIN' },
  { tab: 'reports', label: 'Rapoarte', icon: Activity, restricted: 'ADMIN' },
  { tab: 'settings', label: 'Contul meu', icon: LockKeyhole },
  { tab: 'audit', label: 'Jurnal acces', icon: ShieldCheck, restricted: 'ADMIN' }
];
const tabTitle: Record<Tab, string> = {
  dashboard: 'Prezentare generală', patients: 'Managementul pacienților',
  appointments: 'Programări medicale', encounters: 'Consultații și dosare',
  ai: 'Asistent clinic', doctors: 'Echipa medicală', audit: 'Jurnal de audit', records: 'Dosar medical extins', accounts: 'Conturi și drepturi', schedule: 'Calendar și disponibilitate', notifications: 'Notificări', reports: 'Rapoarte administrative', sharing: 'Colaborare și acces', settings: 'Contul meu'
};
const blankDashboard: Dashboard = { patients: 0, doctors: 0, appointments: 0, todayAppointments: 0, aiAnalyses: 0, hospitalName: '' };
function initials(name: string) { return name.split(' ').slice(0, 2).map(w => w[0]).join('').toUpperCase(); }
function appointmentLabel(s: Appointment['status']) {
  return s === 'SCHEDULED' ? 'Programată' : s === 'COMPLETED' ? 'Finalizată' : 'Anulată';
}
function roleLabel(role: Profile['role']) { return role === 'ADMIN' ? 'Administrator' : role === 'DOCTOR' ? 'Medic' : role === 'STAFF' ? 'Personal medical' : 'Pacient'; }
function Empty({ icon: Icon, title, text }: { icon: LucideIcon; title: string; text: string }) {
  return <div className="empty"><div className="empty-icon"><Icon size={24} /></div><h3>{title}</h3><p>{text}</p></div>;
}
function Avatar({ name, variant = 0 }: { name: string; variant?: number }) {
  return <span className={`avatar color-${variant % 5}`}>{initials(name)}</span>;
}
function Status({ value }: { value: Appointment['status'] }) {
  return <span className={`status status-${value.toLowerCase()}`}><span className="status-dot" />{appointmentLabel(value)}</span>;
}
function Login({ onLogin }: { onLogin: (token: string, user: Profile) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      const result = await request<{ token: string; user: Profile }>('/auth/login', null,
        { method: 'POST', body: JSON.stringify({ email, password }) });
      onLogin(result.token, result.user);
    } catch (err) { setError((err as Error).message + '. Verifică dacă serviciile sunt pornite.'); }
    finally { setBusy(false); }
  }
  return <main className="login-page">
    <div className="login-visual">
      <div className="brand brand-login"><span className="brand-icon"><HeartPulse size={24} /></span><span>medflow<span className="brand-period">.</span></span></div>
      <div className="visual-content"><div className="eyebrow light"><span className="live-dot" /> PLATFORMĂ INTEGRATĂ</div>
        <h1>Mai mult timp<br />pentru ceea ce<br /><em>contează.</em></h1>
        <p>Un singur spațiu pentru pacienți, programări și consultații, cu acces rapid la dosarul medical și suport pentru deciziile clinice.</p>
        <div className="visual-features"><span><Check size={15} /> Fluxuri organizate</span><span><Check size={15} /> Acces pe roluri</span><span><Check size={15} /> Dosar medical integrat</span></div>
      </div>
      <div className="visual-footer">MEDFLOW · ÎNGRIJIRE CONECTATĂ</div>
      <div className="visual-orb orb-one"/><div className="visual-orb orb-two"/>
    </div>
    <div className="login-form-area"><div className="login-card">
      <div className="small-graphic"><LockKeyhole size={22}/></div>
      <div className="eyebrow primary">BINE AI VENIT</div>
      <h2>Autentificare în MedFlow</h2><p className="muted">Intră în spațiul tău de lucru pentru a gestiona activitățile spitalului.</p>
      <form onSubmit={submit} className="form-stack">
        <label>Adresă de email<input type="email" required autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} placeholder="nume@spital.ro" /></label>
        <label>Parolă<input type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        {error && <div className="form-error">{error}</div>}
        <button className="btn primary-btn login-submit" disabled={busy} type="submit">{busy ? 'Se autentifică...' : 'Intră în aplicație'}<ArrowRight size={18}/></button>
      </form>
      <p className="muted">Ai uitat parola? Solicită administratorului spitalului recuperarea accesului după verificarea identității.</p>

    </div></div>
  </main>;
}

type ModalName = 'patient' | 'appointment' | 'encounter' | 'doctor';

export default function App() {
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem('medflow-token'));
  const tokenRef = useRef(token);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [data, setData] = useState<Snapshot | null>(null);
  const [active, setActive] = useState<Tab>('dashboard');
  const [recordStart,setRecordStart]=useState<{kind:string;analysisId?:number;patientId?:number;view?:'overview'|'manage';section?:RecordSection}>({kind:'history'});
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [appointmentDay,setAppointmentDay]=useState(''),[appointmentDoctor,setAppointmentDoctor]=useState('');
  const [appointmentView,setAppointmentView]=useState<'upcoming'|'past'|'cancelled'|'all'>('upcoming');
  const [scheduleAdminOpen,setScheduleAdminOpen]=useState(false);
  const [expandedEncounter,setExpandedEncounter]=useState<number|null>(null);
  const [selectedPatient, setSelectedPatient] = useState<number | null>(null);
  const [cancelAppointment,setCancelAppointment]=useState<Appointment|null>(null);
  const [modal, setModal] = useState<ModalName | null>(null);
  const [fields, setFields] = useState<Record<string,string>>({});
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const logout = useCallback(() => {
    const oldToken=tokenRef.current;if(oldToken)void post('/auth/logout',oldToken,{}).catch(()=>{});
    tokenRef.current = null;
    sessionStorage.removeItem('medflow-token'); setToken(null); setProfile(null); setData(null); setActive('dashboard');
    setModal(null); setSelectedPatient(null); setSidebarOpen(false); setQuery(''); setNotice(''); setError('');
  }, []);
  const refresh = useCallback(async (bearer: string) => {
    setLoading(true);
    try {
      const me = await request<Profile>('/auth/me', bearer);
      if(tokenRef.current !== bearer)return;
      setProfile(me);
      if(me.mustChangePassword)return;
      const staff = me.role === 'ADMIN' || me.role === 'DOCTOR';
      const [dashboard, patients, doctors, appointments, encounters, analyses, audit] = await Promise.all([
        request<Dashboard>('/dashboard', bearer),
        request<Patient[]>('/patients', bearer),
        request<Doctor[]>('/doctors', bearer),
        request<Appointment[]>('/appointments', bearer),
        request<Encounter[]>('/encounters', bearer),
        staff ? request<AiAnalysis[]>('/ai-analyses', bearer) : Promise.resolve([]),
        me.role === 'ADMIN' ? request<Snapshot['audit']>('/audit', bearer) : Promise.resolve([])
      ]);
      if (tokenRef.current !== bearer) return;
      setProfile(me); setData({ dashboard, patients, doctors, appointments, encounters, analyses, audit });
    } catch (err) {
      if (tokenRef.current !== bearer) return;
      if ((err instanceof ApiError && err.status===401) || (err as Error).message.includes('401') || (err as Error).message.includes('expirată')) logout();
      setError((err as Error).message);
    } finally { if (tokenRef.current === bearer) setLoading(false); }
  }, [logout]);
  useEffect(() => { if (token) { void refresh(token); } }, [token, refresh]);
  const isStaff = profile?.role !== 'PATIENT';
  const isClinical = profile?.role === 'ADMIN' || profile?.role === 'DOCTOR';
  const patientMap = useMemo(() => new Map(data?.patients.map(p => [p.id,p]) || []), [data]);
  const doctorMap = useMemo(() => new Map(data?.doctors.map(d => [d.id,d]) || []), [data]);
  const getPatient = (id: number) => nameOfPatient(patientMap.get(id));
  const getDoctor = (id: number) => doctorMap.get(id)?.name || 'Medic necunoscut';
  const choose = (tab: Tab) => {setActive(tab);setQuery('');setSelectedPatient(null);setSidebarOpen(false);};
  const show = (name: ModalName) => {
    setFields({ patientId: String(profile?.patientId || data?.patients[0]?.id || ''),
      doctorId: String(profile?.doctorId || data?.doctors[0]?.id || ''),
      startAt: '',appointmentDate:localDay() });
    setModal(name);setError('');
  };
  const update = (name: string, value: string) => setFields(p => ({ ...p, [name]: value }));
  async function execute(action: () => Promise<unknown>, message: string) {
    if (!token) return;
    setBusy(true);setError('');setNotice('');
    try { await action(); await refresh(token); setNotice(message);setModal(null); }
    catch (err) { setError((err as Error).message); }
    finally { setBusy(false); }
  }
  function submitModal(e: FormEvent) {
    e.preventDefault();if (!token || !modal) return;
    if(modal === 'doctor')void execute(()=>patch(`/doctors/${fields.editDoctorId}`,token,{name:fields.name,specialty:fields.specialty,email:fields.email||null}),'Profilul medicului a fost actualizat.');
    if(modal === 'patient') {
      const payload={firstName:fields.firstName,lastName:fields.lastName,birthDate:fields.birthDate,phone:fields.phone||null,email:fields.email||null};
      void execute(()=>fields.editPatientId?patch(`/patients/${fields.editPatientId}`,token,payload):post('/patients',token,payload),'Datele pacientului au fost salvate.');
    }
    if(modal === 'appointment') {
      if(!fields.startAt){setError('Alege o oră disponibilă pentru programare.');return;}
      const payload={patientId:Number(fields.patientId),doctorId:Number(fields.doctorId),startAt:fields.startAt,reason:fields.reason||'',version:fields.editAppointmentId?Number(fields.appointmentVersion):undefined};
      void execute(()=>fields.editAppointmentId?patch(`/appointments/${fields.editAppointmentId}`,token,payload):post('/appointments',token,payload),'Programarea a fost salvată.');
    }
    if(modal === 'encounter') {
      const payload={patientId:Number(fields.patientId),doctorId:Number(fields.doctorId),appointmentId:fields.appointmentId?Number(fields.appointmentId):null,symptoms:fields.symptoms,observations:fields.observations||'',assessment:fields.assessment||'',patientVisible:fields.patientVisible!=='false'};
      void execute(()=>fields.editEncounterId?patch(`/encounters/${fields.editEncounterId}`,token,payload):post('/encounters',token,payload),'Consultația a fost salvată.');
    }
  }

  function changeStatus(id: number, status: Appointment['status']) {
    if(token) void execute(() => patch(`/appointments/${id}/status`, token, {status,version:data?.appointments.find(a=>a.id===id)?.version}), 'Statusul programării a fost actualizat.');
  }
  function analyze(encounterId: number) {
    setExpandedEncounter(encounterId);
    if(token) void execute(() => post(`/ai/analyze/${encounterId}`, token, {}), 'Analiza este afișată în consultație.');
  }

  if(!token) return <Login onLogin={(value, me) => {tokenRef.current = value;sessionStorage.setItem('medflow-token',value);setProfile(me);setToken(value);setError('');}}/>;
  const acceptSession=(session:{token:string;user:Profile})=>{tokenRef.current=session.token;sessionStorage.setItem('medflow-token',session.token);setProfile(session.user);setToken(session.token);setData(null);};
  if(profile?.mustChangePassword)return <main className="content"><AccountSettings token={token} profile={profile} onSession={acceptSession} onProfile={setProfile} onLogout={logout}/></main>;
  if(!data || !profile) return <div className="loading-screen"><HeartPulse size={37}/><h2>MedFlow</h2><p>{loading ? 'Se încarcă spațiul de lucru...' : error || 'Se conectează...'}</p><button className="btn outline-btn" onClick={logout}>Revenire la autentificare</button></div>;

  const appointmentsSorted = [...data.appointments].sort((a,b) => a.startAt.localeCompare(b.startAt));
  const stats = data.dashboard || blankDashboard;
  const pending = appointmentsSorted.filter(a => a.status === 'SCHEDULED');
  const completed = appointmentsSorted.filter(a => a.status === 'COMPLETED').length;
  const cancelled = appointmentsSorted.filter(a => a.status === 'CANCELLED').length;
  const totalA = data.appointments.length || 1;
  const grouped = [
    { name:'Programate', count: pending.length, color:'#36ad9c' },
    { name:'Finalizate', count: completed, color:'#6b9bd7' },
    { name:'Anulate', count: cancelled, color:'#d99d7c' }
  ];
  const filteredPatients = data.patients.filter(p => `${p.firstName} ${p.lastName} ${p.email || ''}`.toLowerCase().includes(query.toLowerCase()));
  const filteredAppointments = appointmentsSorted.filter(a=>(!appointmentDay||a.startAt.startsWith(appointmentDay))&&(!appointmentDoctor||String(a.doctorId)===appointmentDoctor)).filter(a=>appointmentView==='all'||(appointmentView==='cancelled'?a.status==='CANCELLED':appointmentView==='upcoming'?a.status==='SCHEDULED'&&new Date(a.startAt).getTime()>Date.now():a.status!=='CANCELLED'&&(a.status==='COMPLETED'||new Date(a.startAt).getTime()<=Date.now()))).filter(a => `${getPatient(a.patientId)} ${getDoctor(a.doctorId)} ${a.reason || ''}`.toLowerCase().includes(query.toLowerCase()));
  const filteredEncounters = data.encounters.filter(e => `${getPatient(e.patientId)} ${e.symptoms}`.toLowerCase().includes(query.toLowerCase()));
  const chosen = selectedPatient != null ? patientMap.get(selectedPatient) : null;
  const showNav = nav.filter(item => !item.restricted || (item.restricted === 'CLINICAL' && isClinical) || (item.restricted === 'ADMIN' && profile.role === 'ADMIN'));

  const appointmentTable = (items: Appointment[], action = true) => <div className="table-scroll"><table className="data-table"><thead><tr>
    <th>Pacient</th><th>Medic</th><th>Data și ora</th><th>Motiv</th><th>Status</th>{action && <th className="right">Acțiuni</th>}
    </tr></thead><tbody>{items.map(a => <tr key={a.id}>
    <td><div className="person-cell"><Avatar name={getPatient(a.patientId)} variant={a.patientId}/><span className="cell-name">{getPatient(a.patientId)}</span></div></td>
    <td>{getDoctor(a.doctorId)}</td><td className="nowrap">{fmtDateTime(a.startAt)}</td><td className="muted-cell">{a.reason || '—'}</td><td><Status value={a.status}/></td>
    {action && <td className="right">{a.status==='SCHEDULED' && (isStaff || new Date(a.startAt).getTime()>Date.now()) ? <div className="row-actions">
      {isStaff && <button className="icon-btn success-action" title="Marchează finalizată" aria-label="Marchează finalizată" disabled={busy} onClick={()=>changeStatus(a.id,'COMPLETED')}><Check size={17}/></button>}
      {new Date(a.startAt).getTime()>Date.now() && <button className="btn outline-btn small" disabled={busy} onClick={()=>{setFields({editAppointmentId:String(a.id),appointmentVersion:String(a.version),patientId:String(a.patientId),doctorId:String(a.doctorId),appointmentDate:a.startAt.slice(0,10),startAt:'',reason:a.reason||''});setModal('appointment');setError('');}}>Reprogramează</button>}
      <button className="icon-btn danger-action" title="Anulează programarea" aria-label="Anulează programarea" disabled={busy} onClick={()=>setCancelAppointment(a)}><X size={17}/></button>
    </div>:<span className="muted-cell">—</span>}</td>}

  </tr>)}</tbody></table></div>;
  const sectionHeading = (title: string, subtitle: string, right?: ReactNode) =>
    <div className="section-heading"><div><h2>{title}</h2><p>{subtitle}</p></div>{right}</div>;
  const filterInput = <div className="search-box"><Search size={18}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Caută după nume sau detalii..." /></div>;

  return <div className="app-shell">
    <aside className={`sidebar ${sidebarOpen ? 'opened' : ''}`}>
      <div className="brand"><span className="brand-icon"><HeartPulse size={23}/></span><span>medflow<span className="brand-period">.</span></span></div>
      <div className="hospital-switch"><span className="hospital-glyph"><Hospital size={20}/></span><span className="hospital-words"><strong>{stats.hospitalName.replace('Spitalul Demo ','Centrul Medical ')}</strong><small>Unitate medicală</small></span><ChevronDown size={16}/></div>
      <div className="nav-caption">SPAȚIU DE LUCRU</div>
      <nav className="nav-list">{showNav.map(item => <button key={item.tab} className={`nav-link ${active === item.tab ? 'active' : ''}`} onClick={()=>{if(item.tab==='records')setRecordStart({kind:'history'});choose(item.tab);}}>
        <item.icon size={18} strokeWidth={1.9}/><span>{item.label}</span>{active===item.tab && <span className="nav-active-tick"/>}
      </button>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-note"><span className="note-icon"><ShieldCheck size={19}/></span><strong>Îngrijire conectată</strong><p>Pacienți, programări și consultații într-un singur loc.</p></div>
      <button className="profile-side" onClick={logout} title="Deconectare"><Avatar name={profile.name}/><span><strong>{profile.name}</strong><small>{roleLabel(profile.role)} · Deconectare</small></span><LogOut size={17}/></button></div>
    </aside>
    {sidebarOpen && <button className="mobile-backdrop" onClick={()=>setSidebarOpen(false)} aria-label="Închide meniul"/>}
    <div className="main-area">
      <header className="topbar"><div className="topbar-start"><button className="mobile-menu icon-btn" onClick={()=>setSidebarOpen(true)}><Menu size={23}/></button><div className="breadcrumbs">MedFlow <ChevronRight size={15}/><span>{tabTitle[active]}</span></div></div>
        <div className="topbar-right"><span className="top-date"><CalendarDays size={16}/>{new Intl.DateTimeFormat('ro-RO',{day:'numeric',month:'long',year:'numeric'}).format(new Date())}</span><span className="topbar-divider"/><span className="role-pill"><span className="online-dot"/>{roleLabel(profile.role)}</span><NotificationBell token={token} revision={notice} onOpen={()=>choose('notifications')}/><Avatar name={profile.name}/><button type="button" className="btn outline-btn logout-btn" onClick={logout}><LogOut size={17}/>Deconectare</button></div>
      </header>
      <main className="content">
        {notice && <div className="notice success"><Check size={18}/>{notice}<button onClick={()=>setNotice('')}><X size={15}/></button></div>}
        {error && <div className="notice error"><ShieldAlert size={18}/>{error}<button onClick={()=>setError('')}><X size={15}/></button></div>}
        <PageErrorBoundary key={active}>
        {active==='dashboard'&&profile.role==='PATIENT'&&<PatientHome token={token} profile={profile} appointments={data.appointments} doctors={data.doctors} onBook={()=>choose('schedule')} onAppointments={()=>choose('appointments')} onRecords={section=>{setRecordStart({kind:section,section,patientId:profile.patientId||undefined});choose('records');}}/>}
        {active === 'dashboard' && profile.role!=='PATIENT' && <>
          <div className="welcome-row"><div><div className="eyebrow primary"><span className="live-dot"/> PANOU DE CONTROL</div>
            <h1>Bun venit, {profile.name.split(' ')[0]} <span className="greeting-star">✳</span></h1>
            <p>O privire de ansamblu asupra activității medicale din unitatea ta.</p></div>
            {isStaff && <button className="btn primary-btn" onClick={()=>show('appointment')}><Plus size={17}/>Programare nouă</button>}
          </div>
          <div className="metric-grid">
            {[
              {label:'Pacienți înregistrați',val:stats.patients,icon:UsersRound,cls:'mint',desc:'În baza de date',tab:'patients' as Tab},
              {label:'Programări active',val:stats.appointments,icon:CalendarDays,cls:'blue',desc:'În așteptare',tab:'appointments' as Tab},
              {label:'Medici în echipă',val:stats.doctors,icon:Stethoscope,cls:'violet',desc:'În unitatea medicală',tab:'doctors' as Tab},
              {label:'Analize asistate AI',val:stats.aiAnalyses,icon:BrainCircuit,cls:'sand',desc:'Suport pentru consultații',tab:'ai' as Tab},
            ].filter(item => item.tab !== 'ai' || isClinical).map(item => <button type="button" className="metric-card" key={item.label} onClick={()=>{if(item.tab==='records')setRecordStart({kind:'history'});choose(item.tab);}} aria-label={`${item.label} : deschide ${tabTitle[item.tab]}`}><span className={`metric-icon ${item.cls}`}><item.icon size={22}/></span><span className="metric-label">{item.label}</span><span className="metric-value">{item.val.toLocaleString('ro-RO')}<ArrowDownRight className="metric-arrow" size={18}/></span><span className="metric-desc">{item.desc}</span></button>)}
          </div>
          <div className="dashboard-grid"><section className="panel appointments-panel">
            {sectionHeading('Programări viitoare', 'Cele mai apropiate vizite medicale',<button className="text-link" onClick={()=>choose('appointments')}>Vezi toate <ArrowRight size={16}/></button>)}
            {pending.length ? appointmentTable(pending.slice(0,5),false) : <Empty icon={CalendarDays} title="Nicio programare activă" text="Adaugă o programare pentru a începe."/>}
          </section><section className="panel stats-panel">
            {sectionHeading('Status programări','Distribuția înregistrărilor')}
            <div className="donut-wrap"><div className="donut" style={{background:`conic-gradient(#36ad9c 0 ${pending.length/totalA*100}%, #6b9bd7 ${pending.length/totalA*100}% ${(pending.length+completed)/totalA*100}%, #d99d7c ${(pending.length+completed)/totalA*100}% 100%)`}}><div className="donut-hole"><strong>{data.appointments.length}</strong><small>Total</small></div></div></div>
            <div className="chart-legend">{grouped.map(g=><div key={g.name}><span><i style={{background:g.color}}/>{g.name}</span><strong>{g.count}</strong></div>)}</div>
          </section></div>
          <div className="dashboard-bottom"><div className="callout-ai"><div className="ai-callout-icon"><BrainCircuit size={27}/></div><div><span className="eyebrow">SUPORT PENTRU CONSULTAȚII</span><h3>Asistent pentru documentarea clinică</h3><p>Analizează simptomele, consultă ipotezele propuse și înregistrează decizia medicului.</p></div>{isClinical && <button className="btn ghost-light" onClick={()=>choose('ai')}>Explorează <ArrowRight size={17}/></button>}</div>
            <div className="mini-help"><ShieldCheck size={20}/><div><h3>Acces securizat pe roluri</h3><p>Fiecare cont vede secțiunile permise rolului său.</p></div></div></div>
        </>}
        {active === 'patients' && <>
          <div className="page-intro">{sectionHeading('Pacienți', `${data.patients.length} înregistrări în unitatea curentă`, isStaff ? <button className="btn primary-btn" onClick={()=>show('patient')}><Plus size={17}/>Pacient nou</button> : undefined)}</div>
          <section className="panel table-panel"><div className="table-toolbar">{filterInput}<span className="result-count">{filteredPatients.length} rezultate</span></div>
            {filteredPatients.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Pacient</th><th>Data nașterii</th><th>Telefon</th><th>Adresă email</th><th></th></tr></thead><tbody>
            {filteredPatients.map(p => <tr key={p.id} className="clickable-row" onClick={()=>setSelectedPatient(p.id)}><td><div className="person-cell"><Avatar name={nameOfPatient(p)} variant={p.id}/><span className="cell-name">{nameOfPatient(p)}<small>ID #{p.id}</small></span></div></td><td>{fmtDate(p.birthDate)}</td><td>{p.phone || '—'}</td><td>{p.email || '—'}</td><td><ChevronRight size={18}/></td></tr>)}
            </tbody></table></div> : <Empty icon={UsersRound} title="Niciun pacient găsit" text="Modifică termenul de căutare sau adaugă un pacient."/>}
          </section>
          {chosen && <div className="detail-overlay" onClick={()=>setSelectedPatient(null)}><section className="detail-panel" onClick={e=>e.stopPropagation()}><button className="icon-btn detail-close" onClick={()=>setSelectedPatient(null)}><X size={20}/></button>
              <Avatar name={nameOfPatient(chosen)} variant={chosen.id}/><h2>{nameOfPatient(chosen)}</h2><p className="muted">Dosar pacient · ID #{chosen.id}</p>
              <div className="detail-info"><div><span>Data nașterii</span><strong>{fmtDate(chosen.birthDate)}</strong></div><div><span>Telefon</span><strong>{chosen.phone || 'Necompletat'}</strong></div><div><span>Email</span><strong>{chosen.email || 'Necompletat'}</strong></div></div>
              {isStaff && <button className="btn outline-btn" onClick={()=>{setSelectedPatient(null);setFields({editPatientId:String(chosen.id),firstName:chosen.firstName,lastName:chosen.lastName,birthDate:chosen.birthDate,phone:chosen.phone||'',email:chosen.email||''});setModal('patient');setError('');}}>Editează informațiile pacientului</button>}
              <button className="btn primary-btn full" onClick={()=>{setRecordStart({kind:'history',patientId:chosen.id});choose('records');}}><FileHeart size={17}/>Deschide dosarul medical</button>
              <h3>Istoric consultații</h3>{data.encounters.filter(e=>e.patientId===chosen.id).map(e=><div className="detail-encounter" key={e.id}><span>{fmtDate(e.createdAt)}</span><strong>{clinicalText(e.symptoms)}</strong><p>{clinicalText(e.assessment) || 'Fără evaluare introdusă'}</p></div>)}
              {!data.encounters.some(e=>e.patientId===chosen.id) && <p className="muted">Nu există consultații pentru acest pacient.</p>}
              {isClinical && <button className="btn primary-btn full" onClick={()=>{setSelectedPatient(null);show('encounter');setFields(old=>({...old,patientId:String(chosen.id)}));}}><Plus size={17}/>Consultație nouă</button>}
            </section></div>}
        </>}
        {active === 'appointments' && <><div className="page-intro">{sectionHeading('Programări', 'Planificarea consultațiilor și gestionarea vizitelor',<button className="btn primary-btn" onClick={()=>show('appointment')}><Plus size={17}/>Programare nouă</button>)}</div>
          <nav className="record-view-tabs" aria-label="Filtre programări">{([['upcoming','Viitoare'],['past','Istoric'],['cancelled','Anulate'],['all','Toate']] as const).map(([value,label])=><button type="button" key={value} className={appointmentView===value?'is-active':''} aria-pressed={appointmentView===value} onClick={()=>setAppointmentView(value)}>{label}</button>)}</nav>
          <section className="panel table-panel"><div className="table-toolbar appointment-filters">{filterInput}<label>Medic<select value={appointmentDoctor} onChange={e=>setAppointmentDoctor(e.target.value)}><option value="">Toți medicii</option>{data.doctors.map(d=><option value={d.id} key={d.id}>{d.name}</option>)}</select></label><label>Data<input type="date" value={appointmentDay} onChange={e=>setAppointmentDay(e.target.value)}/></label>{(appointmentDay||appointmentDoctor)&&<button type="button" className="text-link" onClick={()=>{setAppointmentDay('');setAppointmentDoctor('');}}>Resetează filtrele</button>}<span className="result-count">{filteredAppointments.length} rezultate</span></div>
            {filteredAppointments.length ? appointmentTable(filteredAppointments) : <Empty icon={CalendarDays} title="Nicio programare" text="Înregistrează prima programare."/>}</section>
        </>}
        {active === 'encounters' && <><div className="page-intro">{sectionHeading('Consultații medicale', 'Istoricul consultațiilor și observațiilor clinice',isClinical ? <button className="btn primary-btn" onClick={()=>show('encounter')}><Plus size={17}/>Consultație nouă</button> : undefined)}</div>
          <div className="section-filters">{filterInput}</div>
          <div className="encounter-grid">{filteredEncounters.map(e => {
            const encounterAnalyses=data.analyses.filter(a=>a.encounterId===e.id).sort((a,b)=>b.createdAt.localeCompare(a.createdAt)||b.id-a.id);
            const latestAnalysis=encounterAnalyses[0];
            const canAnalyze=isClinical&&(profile.role==='ADMIN'||e.doctorId===profile.doctorId);
            const expanded=expandedEncounter===e.id;
            return <article className={`panel encounter-card consultation-card${expanded?' is-expanded':''}`} key={e.id}>
              <div className="encounter-top"><div className="person-cell"><Avatar name={getPatient(e.patientId)} variant={e.patientId}/><span className="cell-name">{getPatient(e.patientId)}<small>Consultația #{e.id} · {getDoctor(e.doctorId)}</small></span></div><span className="date-chip"><Clock3 size={14}/>{fmtDate(e.createdAt)}</span></div>
              <div className={`consultation-content${isClinical?' has-ai':''}`} id={`consultation-content-${e.id}`}>
                <section className="consultation-notes"><div className="eyebrow">SIMPTOME RAPORTATE</div><p className={expanded?'':'consultation-preview'}>{clinicalText(e.symptoms)}</p>
                  {expanded&&<><div className="eyebrow">OBSERVAȚII</div><p>{clinicalText(e.observations)||'Nespecificate'}</p>{e.assessment&&<><div className="eyebrow">EVALUARE MEDIC</div><p>{clinicalText(e.assessment)}</p></>}</>}
                  {expanded&&isClinical&&<button className="btn outline-btn small" onClick={()=>{setFields({editEncounterId:String(e.id),patientId:String(e.patientId),doctorId:String(e.doctorId),appointmentId:e.appointmentId?String(e.appointmentId):'',symptoms:e.symptoms,observations:e.observations||'',assessment:e.assessment||'',patientVisible:String(e.patientVisible)});setModal('encounter');setError('');}}>Actualizează consultația</button>}
                </section>
                {expanded&&isClinical&&<section className="encounter-ai"><div className="analysis-top"><h3><BrainCircuit size={18}/> Analiza consultației</h3><span className="clinical-support-label">Suport consultativ</span></div>
                  {latestAnalysis?<><p className="consultation-analysis-meta">{aiPriorityLabel(latestAnalysis.category)} · {fmtDateTime(latestAnalysis.createdAt)}</p><AiAnalysisResult analysis={latestAnalysis} busy={busy} onRegenerate={canAnalyze?()=>analyze(e.id):undefined}/><button className="btn outline-btn small" onClick={()=>{setRecordStart({kind:'ai-reviews',analysisId:latestAnalysis.id});choose('records');}}>Revizuiește propunerile</button>
                    {encounterAnalyses.length>1&&<details className="ai-technical"><summary>Analize anterioare ({encounterAnalyses.length-1})</summary>{encounterAnalyses.slice(1).map(a=><div className="analysis-item" key={a.id}><p className="muted small-text">{fmtDateTime(a.createdAt)}</p><AiAnalysisResult analysis={a} busy={busy}/></div>)}</details>}</>:<div className="consultation-ai-empty"><BrainCircuit size={26}/><h4>O perspectivă asupra simptomelor</h4><p>Generează ipoteze, opțiuni de conduită și investigații pentru această consultație.</p>{canAnalyze&&<button className="btn primary-btn small" disabled={busy} onClick={()=>analyze(e.id)}>{busy?'Se analizează…':'Analizează simptomele'}</button>}</div>}
                </section>}
              </div>
              <div className="encounter-footer"><span>{latestAnalysis?<><span className="consultation-status-dot"/>Analiză disponibilă</>:<><Clock3 size={14}/>Fără analiză AI</>}</span><button className="btn outline-btn small" aria-expanded={expanded} aria-controls={`consultation-content-${e.id}`} onClick={()=>setExpandedEncounter(expanded?null:e.id)}>{expanded?'Restrânge consultația':'Deschide consultația'}<ChevronDown size={15} style={{transform:expanded?'rotate(180deg)':undefined}}/></button></div>
            </article>;
          })}
          </div>{!filteredEncounters.length && <Empty icon={ClipboardList} title="Nicio consultație" text="Consultațiile vor apărea aici după înregistrare."/>}
        </>}
        {active === 'ai' && <><div className="page-intro">{sectionHeading('Asistent AI','Ipoteze, conduită și investigații propuse pe baza simptomelor')}</div>
          <p className="clinical-support-label">Recomandări consultative · decizia aparține medicului</p>
          <div className="ai-two-col"><section className="panel ai-guide"><div className="ai-guide-icon"><BrainCircuit size={28}/></div><h2>Cum funcționează?</h2><p>Modelul analizează simptomele și istoricul, propune explicații posibile și afișează opțiunile de conduită din setul de referință.</p>
            <div className="ai-steps"><div><span>01</span> Completează simptomele consultației</div><div><span>02</span> Selectează „Analizează simptomele”</div><div><span>03</span> Consultă rezultatul direct în consultație</div></div>
            <button className="btn primary-btn full" onClick={()=>choose('encounters')}>Mergi la consultații <ArrowRight size={17}/></button></section>
            <section className="panel ai-history">{sectionHeading('Istoric analize',`${data.analyses.length} rezultate salvate`)}
              {data.analyses.length ? data.analyses.map(a => <div className="analysis-item" key={a.id}><div className="analysis-top"><span className="ai-status"><Activity size={15}/>{aiPriorityLabel(a.category)}</span><span className="muted small-text">{fmtDateTime(a.createdAt)}</span></div><AiAnalysisResult analysis={a} busy={busy} onRegenerate={profile.role==='ADMIN'||data.encounters.some(e=>e.id===a.encounterId&&e.doctorId===profile.doctorId)?()=>analyze(a.encounterId):undefined}/><button className="btn outline-btn small" onClick={()=>{setRecordStart({kind:'ai-reviews',analysisId:a.id});choose('records');}}>Acceptă / modifică / respinge</button></div>) : <Empty icon={BrainCircuit} title="Nu există analize" text="Generează o analiză din pagina consultației."/>}
            </section></div>
        </>}
        {active === 'doctors' && <><div className="page-intro">{sectionHeading('Echipă medicală',`${data.doctors.length} profesioniști înregistrați`)}</div>
          <div className="doctor-grid">{data.doctors.map((d,i) => <article className="panel doctor-card" key={d.id}><div className="doctor-top"><Avatar name={d.name} variant={i}/><span className="doctor-chip">Specialist</span></div><h3>{d.name}</h3><p>{d.specialty}</p><div className="doctor-divider"/><div className="doctor-meta"><Stethoscope size={16}/>Personal medical</div><div className="doctor-meta"><Bell size={16}/>{d.email || 'Fără email'}</div>{profile.role==='ADMIN'&&<button className="btn outline-btn full" onClick={()=>{setFields({editDoctorId:String(d.id),name:d.name,specialty:d.specialty,email:d.email||''});setModal('doctor');setError('');}}>Editează profilul și specializarea</button>}{(profile.role!=='DOCTOR' || profile.doctorId===d.id) && <button className="btn outline-btn full" onClick={()=>{show('appointment');setFields(old=>({...old,doctorId:String(d.id)}));}}>Programează o vizită <ArrowRight size={16}/></button>}</article>)}</div>
        </>}
        {active==='schedule'&&<><AvailabilityDirectory token={token} doctors={data.doctors} appointments={data.appointments} onBook={(doctorId,date)=>{show('appointment');setFields(old=>({...old,doctorId:String(doctorId),appointmentDate:date,startAt:''}));}}/>{(profile.role==='ADMIN'||profile.role==='STAFF')&&<details className="availability-admin" onToggle={e=>setScheduleAdminOpen(e.currentTarget.open)}><summary>Administrare program și personal medical</summary>{scheduleAdminOpen&&<OperationsWorkspace mode="schedule" token={token} profile={profile} doctors={data.doctors} patients={data.patients} appointments={data.appointments} onUpdated={()=>void refresh(token)}/>}</details>}</>}
        {(['accounts','notifications','reports','sharing'] as Tab[]).includes(active) && <OperationsWorkspace key={active} mode={active as 'accounts'|'notifications'|'reports'|'sharing'} token={token} profile={profile} doctors={data.doctors} patients={data.patients} appointments={data.appointments} onUpdated={()=>void refresh(token)}/>}
        {active==='settings'&&<AccountSettings token={token} profile={profile} onSession={acceptSession} onProfile={setProfile} onLogout={logout}/>}
        {active==='records'&&<>{(recordStart.view==='manage'||recordStart.kind==='ai-reviews')?<><button className="btn outline-btn small record-back" onClick={()=>setRecordStart({kind:'history',patientId:recordStart.patientId})}>Înapoi la dosar</button><RecordsWorkspace key={`${recordStart.kind}-${recordStart.patientId||''}-${recordStart.analysisId||''}`} token={token} profile={profile} patients={data.patients} encounters={data.encounters} analyses={data.analyses} initialKind={recordStart.kind} initialAnalysisId={recordStart.analysisId} initialPatientId={recordStart.patientId}/></>:<PatientRecord key={`${recordStart.patientId||''}-${recordStart.section||''}`} token={token} profile={profile} patients={data.patients} encounters={data.encounters} initialPatientId={recordStart.patientId} initialSection={recordStart.section} onManage={(kind,patientId)=>setRecordStart({kind,patientId,view:'manage'})}/>}</>}

        {active === 'audit' && <><div className="page-intro">{sectionHeading('Jurnal de audit','Ultimele operațiuni și accesări ale datelor din aplicație')}</div>
          <section className="panel table-panel"><div className="table-scroll"><table className="data-table"><thead><tr><th>Acțiune</th><th>Resursă</th><th>Utilizator</th><th>Moment</th></tr></thead><tbody>{data.audit.map(a=><tr key={a.id}><td><span className="audit-action">{a.action}</span></td><td>{a.resource}</td><td>#{a.actorId}</td><td>{fmtDateTime(a.createdAt)}</td></tr>)}</tbody></table></div>{!data.audit.length && <Empty icon={ShieldCheck} title="Jurnal gol" text="Evenimentele auditate vor apărea aici."/>}</section>
        </>}
        </PageErrorBoundary>
        <footer className="main-footer"><span>© 2026 MedFlow · Platformă medicală</span><span><span className="online-dot"/> Sesiune activă</span></footer>
      </main>
    </div>
    {cancelAppointment&&<div className="modal-overlay"><section className="modal-dialog" role="dialog" aria-modal="true" aria-labelledby="cancel-visit-title"><div className="modal-header"><div><span className="eyebrow primary">PROGRAMARE MEDICALĂ</span><h2 id="cancel-visit-title">Anulezi această programare?</h2></div><button type="button" className="icon-btn" aria-label="Închide" onClick={()=>setCancelAppointment(null)}><X size={20}/></button></div><div className="modal-form"><h3>{getDoctor(cancelAppointment.doctorId)}</h3><p>{fmtDateTime(cancelAppointment.startAt)}</p><p className="muted">Locul va fi eliberat, iar anularea va apărea în istoricul programărilor.</p><div className="modal-actions"><button type="button" className="btn outline-btn" onClick={()=>setCancelAppointment(null)}>Păstrează programarea</button><button type="button" className="btn primary-btn" disabled={busy} onClick={()=>{changeStatus(cancelAppointment.id,'CANCELLED');setCancelAppointment(null);}}>Confirmă anularea</button></div></div></section></div>}
    {modal && <div className="modal-overlay" onMouseDown={e=>{if(e.target === e.currentTarget)setModal(null);}}><div className={`modal-dialog${modal==='appointment'?' appointment-dialog':''}`} role="dialog" aria-modal="true" aria-label={modal}>
      <div className="modal-header"><div><span className="eyebrow primary">ÎNREGISTRARE</span><h2>{modal === 'doctor' ? 'Profil medic' : modal === 'patient' ? (fields.editPatientId?'Actualizare pacient':'Pacient nou') : modal === 'appointment' ? (fields.editAppointmentId?'Reprogramare':'Programare nouă') : (fields.editEncounterId?'Actualizare consultație':'Consultație nouă')}</h2></div><button className="icon-btn" onClick={()=>setModal(null)}><X size={20}/></button></div>
      <form onSubmit={submitModal} className="modal-form">
        {modal==='doctor'&&<><label>Nume<input required maxLength={160} value={fields.name||''} onChange={e=>update('name',e.target.value)}/></label><label>Specializare<input required maxLength={120} value={fields.specialty||''} onChange={e=>update('specialty',e.target.value)}/></label><label>Email<input type="email" maxLength={180} value={fields.email||''} onChange={e=>update('email',e.target.value)}/></label></>}
        {modal === 'patient' && <><div className="form-row"><label>Prenume *<input required value={fields.firstName || ''} onChange={e=>update('firstName',e.target.value)} /></label><label>Nume *<input required value={fields.lastName || ''} onChange={e=>update('lastName',e.target.value)}/></label></div><label>Data nașterii *<input required type="date" max={new Date().toISOString().slice(0,10)} value={fields.birthDate || ''} onChange={e=>update('birthDate',e.target.value)}/></label><div className="form-row"><label>Telefon<input value={fields.phone || ''} onChange={e=>update('phone',e.target.value)}/></label><label>Email<input type="email" value={fields.email || ''} onChange={e=>update('email',e.target.value)}/></label></div></>}
        {(modal === 'appointment' || modal === 'encounter') && <><label>Pacient *<select required disabled={profile.role==='PATIENT' || !!fields.editAppointmentId || !!fields.editEncounterId} value={fields.patientId || ''} onChange={e=>{update('patientId',e.target.value);update('appointmentId','');}}><option value="">Selectează pacientul</option>{data.patients.map(p=><option key={p.id} value={p.id}>{nameOfPatient(p)}</option>)}</select></label><label>Medic *<select required disabled={profile.role==='DOCTOR' || !!fields.editEncounterId} value={fields.doctorId || ''} onChange={e=>{update('doctorId',e.target.value);update('appointmentId','');update('startAt','');}}><option value="">Selectează medicul</option>{data.doctors.map(d=><option key={d.id} value={d.id}>{d.name} · {d.specialty}</option>)}</select></label></>}
        {modal === 'appointment' && <>{fields.doctorId&&<AppointmentAvailability key={`${fields.editAppointmentId||'new'}-${fields.doctorId}`} token={token} doctorId={Number(fields.doctorId)} date={fields.appointmentDate||localDay()} value={fields.startAt||''} revision={`${busy}-${error}`} onDay={day=>setFields(old=>({...old,appointmentDate:day,startAt:''}))} onSelect={slot=>update('startAt',slot)}/>}<label>Motivul vizitei<textarea rows={3} maxLength={300} value={fields.reason || ''} onChange={e=>update('reason',e.target.value)} placeholder="Descriere scurtă a solicitării"/></label></>}
        {modal === 'encounter' && <><label>Programare asociată (opțional)<select disabled={!!fields.editEncounterId} value={fields.appointmentId || ''} onChange={e=>update('appointmentId',e.target.value)}><option value="">Fără programare · prezentare directă</option>{data.appointments.filter(a=>String(a.patientId)===fields.patientId && String(a.doctorId)===fields.doctorId && a.status!=='CANCELLED').map(a=><option key={a.id} value={a.id}>#{a.id} · {fmtDateTime(a.startAt)} · {a.reason || 'Consultație'}</option>)}</select></label><label>Simptome raportate *<textarea required minLength={3} maxLength={4000} rows={4} value={fields.symptoms || ''} onChange={e=>update('symptoms',e.target.value)} placeholder="Descrie simptomele, debutul, durata și intensitatea"/></label><label>Observații<textarea rows={3} maxLength={4000} value={fields.observations || ''} onChange={e=>update('observations',e.target.value)}/></label><label>Vizibil pacientului<select value={fields.patientVisible||'true'} onChange={e=>update('patientVisible',e.target.value)}><option value="true">Da</option><option value="false">Nu · consultație internă</option></select></label><label>Evaluarea medicului<textarea rows={3} maxLength={8000} value={fields.assessment || ''} onChange={e=>update('assessment',e.target.value)}/></label></>}
        {error && <div className="form-error">{error}</div>}
        <div className="modal-actions"><button type="button" className="btn outline-btn" onClick={()=>setModal(null)}>Renunță</button><button className="btn primary-btn" type="submit" disabled={busy}>{busy ? 'Se salvează...' : 'Salvează'} <Check size={17}/></button></div>
      </form>
    </div></div>}
  </div>;
}
