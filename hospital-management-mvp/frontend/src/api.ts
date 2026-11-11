import { formatDate } from './dateFormat.mjs';

export type Role = 'ADMIN' | 'DOCTOR' | 'STAFF' | 'PATIENT';
export type Tab = 'dashboard' | 'patients' | 'appointments' | 'encounters' | 'ai' | 'doctors' | 'audit' | 'records' | 'accounts' | 'schedule' | 'notifications' | 'reports' | 'sharing' | 'settings';
export type Profile = { id: number; name: string; email: string; role: Role; hospitalId: number; doctorId: number | null; patientId: number | null; mustChangePassword: boolean };
export type Patient = { id: number; hospitalId: number; firstName: string; lastName: string; birthDate: string; phone: string | null; email: string | null; createdAt: string };
export type Doctor = { id: number; hospitalId: number; name: string; specialty: string; email: string | null };
export type Appointment = { id: number; hospitalId: number; patientId: number; doctorId: number; startAt: string; reason: string; status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'; version:number };
export type Encounter = { id: number; hospitalId: number; patientId: number; doctorId: number; appointmentId: number | null; symptoms: string; observations: string | null; assessment: string | null; patientVisible: boolean; createdAt: string };
export type AiAnalysis = { id: number; hospitalId: number; encounterId: number; category: string; summary: string; evidence: string; modelVersion: string; createdAt: string; details: string };
export type Audit = { id: number; actorId: number; action: string; resource: string; createdAt: string };
export type Dashboard = { patients: number; doctors: number; appointments: number; todayAppointments: number; aiAnalyses: number; hospitalName: string };
export type Snapshot = {
  dashboard: Dashboard;
  patients: Patient[];
  doctors: Doctor[];
  appointments: Appointment[];
  encounters: Encounter[];
  analyses: AiAnalysis[];
  audit: Audit[];
};
const base = import.meta.env.VITE_API_URL || '/api';
export class ApiError extends Error {
  constructor(public readonly status:number,message:string) {super(message);this.name='ApiError';}
}
export async function request<T>(path: string, token: string | null, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) },
  });
  let data: any;
  try { data = await response.json(); } catch { data = {}; }
  if (!response.ok) throw new ApiError(response.status,data.error || (response.status === 401 ? 'Sesiune expirată sau date incorecte' : `Eroare HTTP ${response.status}`));
  return data as T;
}
export function post<T>(path: string, token: string, payload: unknown): Promise<T> {
  return request<T>(path, token, { method: 'POST', body: JSON.stringify(payload) });
}
export function patch<T>(path: string, token: string, payload: unknown): Promise<T> {
  return request<T>(path, token, { method: 'PATCH', body: JSON.stringify(payload) });
}
export const nameOfPatient = (p?: Patient) => p ? `${p.firstName} ${p.lastName}` : 'Pacient necunoscut';
export const fmtDate = (d: unknown) => formatDate(d);
export const fmtDateTime = (d: unknown) => formatDate(d, true);

export async function uploadDocument(token: string, patientId: string, documentType: string, file: File, patientVisible = true): Promise<void> {
  const form = new FormData();
  form.append('patientId', patientId); form.append('documentType', documentType); form.append('file', file);form.append('patientVisible',String(patientVisible));
  const response = await fetch(`${base}/medical-documents`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || `Încărcare eșuată (${response.status})`);
  }
}
export async function downloadDocument(token: string, id: number, name: string): Promise<void> {
  const response = await fetch(`${base}/medical-documents/${id}/download`, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`Document indisponibil (${response.status})`);
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = url; link.download = name; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function downloadReport(token:string,from:string,to:string):Promise<void> {
 const response=await fetch(`${base}/reports.csv?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,{headers:{Authorization:`Bearer ${token}`}});
 if(!response.ok)throw new Error(`Export indisponibil (${response.status})`);
 const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download=`medflow-${from}-${to}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
