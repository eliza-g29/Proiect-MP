export type AiCondition = {code:string;label:string;differentials:string[];managementOptions:string[];investigations:string[];referral:string};
export type MedicationView={name:string;indication:string;status:string;checks:string[];reasons:string[];sources:{title:string;url:string}[]};
export type AiView = {hasMedicationModule:boolean;medications:MedicationView[];withheld:MedicationView[];medicationNote:string;conditions:AiCondition[];suggestions:string[];options:string[];investigations:string[];referral:string;tentative:boolean;questions:string[];basis:string[];priority:string;symptoms:string;observations:string;history:string[];hasGuidance:boolean};
export function parseAiDetails(details:unknown):AiView;
export function aiPriorityLabel(value:string):string;
