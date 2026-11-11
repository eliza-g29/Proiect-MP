import {useEffect,useState} from 'react';
import {Bell} from 'lucide-react';
import {request} from './api';
export default function NotificationBell({token,onOpen,revision}:{token:string;onOpen:()=>void;revision:unknown}) {
 const [unread,setUnread]=useState(0);
 useEffect(()=>{let active=true;const refresh=()=>void request<{read_at:string|null}[]>('/notifications',token).then(rows=>{if(active)setUnread(rows.filter(r=>!r.read_at).length);}).catch(()=>{if(active)setUnread(0);});refresh();const timer=setInterval(refresh,30000);window.addEventListener('focus',refresh);window.addEventListener('medflow-notifications-changed',refresh);return()=>{active=false;clearInterval(timer);window.removeEventListener('focus',refresh);window.removeEventListener('medflow-notifications-changed',refresh);};},[token,revision]);
 return <button className="icon-btn notification-bell" aria-label={`Notificări${unread?`, ${unread>99?'peste 99':unread} necitite`:''}`} title="Notificări" onClick={onOpen}><Bell size={20}/>{unread>0&&<span>{unread>99?'99+':unread}</span>}</button>;
}
