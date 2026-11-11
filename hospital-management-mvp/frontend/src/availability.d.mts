import type {DoctorAvailability} from './availabilityTypes';
export function localDay(now?:Date):string;
export function shiftDay(day:string,offset:number):string;
export function dayLabel(day:string,options?:Intl.DateTimeFormatOptions):string;
export function remainingSlots(slots:string[],now?:Date):string[];
export function availableDoctors(rows:DoctorAvailability[],day?:string|null,query?:string,specialty?:string,now?:Date):DoctorAvailability[];
