import { useState, type FormEvent } from 'react';
import { request, post, type Profile } from './api';
type Session = {token:string;user:Profile};
export default function AccountSettings({token,profile,onSession,onProfile,onLogout}: {token:string;profile:Profile;onSession:(s:Session)=>void;onProfile:(p:Profile)=>void;onLogout:()=>void}) {
 const [name,setName]=useState(profile.name),[email,setEmail]=useState(profile.email);
 const [current,setCurrent]=useState(''),[password,setPassword]=useState(''),[confirm,setConfirm]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function save(e:FormEvent,changePassword:boolean) {
  e.preventDefault();setBusy(true);setError('');setNotice('');
  try {
   if(changePassword) {
    if(password!==confirm)throw new Error('Parolele noi nu coincid.');
    const session=await post<Session>('/auth/password',token,{currentPassword:current,newPassword:password});
    setCurrent('');setPassword('');setConfirm('');onSession(session);setNotice('Parola a fost schimbată. Celelalte sesiuni au fost revocate.');
   } else {onProfile(await request<Profile>('/auth/profile',token,{method:'PATCH',body:JSON.stringify({name,email})}));setNotice('Datele contului au fost actualizate.');}
  } catch(e) {setError((e as Error).message);}finally {setBusy(false);}
 }
 return <section className="account-settings"><div className="page-intro"><h2>Contul meu</h2><p>{profile.mustChangePassword?'Înlocuiește parola temporară pentru a continua.':'Date personale și securitatea accesului.'}</p></div>
 {error&&<div className="notice error" role="alert">{error}</div>}{notice&&<div className="notice success" role="status">{notice}</div>}
 {!profile.mustChangePassword&&<form className="panel modal-form" onSubmit={e=>void save(e,false)}><h3>Informații personale</h3><label>Nume<input required maxLength={160} value={name} onChange={e=>setName(e.target.value)} autoComplete="name"/></label><label>Email<input required type="email" maxLength={180} value={email} onChange={e=>setEmail(e.target.value)} autoComplete="email"/></label><button className="btn primary-btn" disabled={busy}>Salvează datele</button></form>}
 <form className="panel modal-form" onSubmit={e=>void save(e,true)}><h3>Schimbă parola</h3><label>Parola curentă / temporară<input required type="password" autoComplete="current-password" value={current} onChange={e=>setCurrent(e.target.value)}/></label><label>Parolă nouă (minimum 12 caractere)<input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)}/></label><label>Confirmă parola nouă<input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)}/></label><button className="btn primary-btn" disabled={busy}>{busy?'Se salvează…':'Schimbă parola'}</button></form>
 <button type="button" className="btn outline-btn" onClick={onLogout}>Deconectare</button></section>;
}
