import {useCallback,useEffect,useRef,useState} from 'react';
import {request} from './api';
import type {DoctorAvailability} from './availabilityTypes';
export default function useAvailability(token:string,from:string,doctorId?:number,revision?:unknown) {
 const [rows,setRows]=useState<DoctorAvailability[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState('');
 const sequence=useRef(0);
 const refresh=useCallback(async()=>{
  const attempt=++sequence.current;setLoading(true);setError('');
  try{const data=await request<DoctorAvailability[]>(`/availability/calendar?from=${from}&days=7${doctorId?`&doctorId=${doctorId}`:''}`,token);if(attempt===sequence.current)setRows(data);}
  catch(e){if(attempt===sequence.current){setRows([]);setError((e as Error).message);}}
  finally{if(attempt===sequence.current)setLoading(false);}
 },[token,from,doctorId]);
 useEffect(()=>{setRows([]);void refresh();const onFocus=()=>void refresh();const timer=window.setInterval(onFocus,30000);window.addEventListener('focus',onFocus);return()=>{++sequence.current;clearInterval(timer);window.removeEventListener('focus',onFocus);};},[refresh,revision]);
 return {rows,loading,error,refresh};
}
