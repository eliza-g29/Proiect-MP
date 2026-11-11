import type {Encounter} from './api';
export function clinicalText(value:unknown):string;
export function patientRecords<T extends Record<string,unknown>>(rows:T[],patientId:number,encounters:Encounter[]):T[];
export function resultCsv(rows:Record<string,unknown>[]):string;
