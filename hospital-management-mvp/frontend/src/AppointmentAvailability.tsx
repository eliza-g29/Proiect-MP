import {useEffect,useRef,useState} from 'react';
import {ArrowLeft,ArrowRight,CalendarDays,Clock3} from 'lucide-react';
import {dayLabel,localDay,shiftDay,remainingSlots} from './availability.mjs';
import useAvailability from './useAvailability';
type Props={token:string;doctorId:number;date:string;value:string;onDay:(day:string)=>void;onSelect:(slot:string)=>void;revision:unknown};
export default function AppointmentAvailability({token,doctorId,date,value,onDay,onSelect,revision}:Props) {
 const today=localDay(),[from,setFrom]=useState(date||today);
 const {rows,loading,error}=useAvailability(token,from,doctorId,revision);
 const callbacks=useRef({onDay,onSelect});callbacks.current={onDay,onSelect};
 const days=rows[0]?.days||[],selected=days.find(d=>d.date===date),slots=remainingSlots(selected?.slots||[]);
 useEffect(()=>{if(!loading&&value&&!slots.includes(value))callbacks.current.onSelect('');},[loading,value,slots.join('|')]);
 useEffect(()=>{if(date&&(date<from||date>shiftDay(from,6)))setFrom(date);},[date,from]);
 function move(offset:number){const next=shiftDay(from,offset);setFrom(next);callbacks.current.onDay(next);}
 return <section className="appointment-availability" aria-busy={loading}><div className="booking-calendar-header"><div><CalendarDays size={18}/><strong>Alege ziua și ora</strong></div><div className="availability-week-nav"><button type="button" className="icon-btn" aria-label="7 zile înapoi" disabled={from<=today} onClick={()=>{const next=shiftDay(from,-7)<today?today:shiftDay(from,-7);setFrom(next);callbacks.current.onDay(next);}}><ArrowLeft size={17}/></button><span>{dayLabel(from,{day:'numeric',month:'short'})} – {dayLabel(shiftDay(from,6),{day:'numeric',month:'short'})}</span><button type="button" className="icon-btn" aria-label="7 zile înainte" disabled={shiftDay(from,7)>shiftDay(today,359)} onClick={()=>move(7)}><ArrowRight size={17}/></button></div></div>
  <div className="availability-day-strip booking-days">{Array.from({length:7},(_,i)=>shiftDay(from,i)).map(day=>{const d=days.find(d=>d.date===day),count=remainingSlots(d?.slots||[]).length;return <button type="button" aria-pressed={date===day} key={day} className={date===day?'is-active':''} onClick={()=>callbacks.current.onDay(day)}><span>{dayLabel(day,{weekday:'short'})}</span><strong>{dayLabel(day,{day:'numeric',month:'short'})}</strong><small>{loading?'…':count?`${count} ore libere`:'Fără locuri'}</small></button>;})}</div>
  {error&&<p className="form-error" role="alert">{error}</p>}
  {!error&&<><div className="booking-working-hours"><Clock3 size={15}/>{loading?'Se verifică programul…':selected?.workingHours.length?`Program ${dayLabel(date,{weekday:'long'})}: ${selected.workingHours.map(w=>`${w.startAt.slice(11,16)}–${w.endAt.slice(11,16)}`).join(' / ')}`:'Medicul nu are program configurat în această zi.'}</div>
   <div className="booking-slot-grid">{!loading&&slots.map(slot=><button type="button" aria-pressed={value===slot} className={value===slot?'is-selected':''} key={slot} onClick={()=>callbacks.current.onSelect(slot)}>{slot.slice(11,16)}</button>)}</div>
   {!loading&&selected?.workingHours.length!==0&&!slots.length&&<p className="muted">Toate orele sunt ocupate sau au trecut. Alege o altă zi.</p>}
   {value&&<p className="booking-selection" role="status">Ai ales {dayLabel(value.slice(0,10),{weekday:'long',day:'numeric',month:'long'})}, ora {value.slice(11,16)}.</p>}
  </>}
 </section>;
}
