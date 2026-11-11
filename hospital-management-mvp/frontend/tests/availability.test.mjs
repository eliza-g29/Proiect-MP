import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {localDay,shiftDay,remainingSlots,availableDoctors} from '../src/availability.mjs';
test('calendar navigation crosses months, years and leap days without UTC drift',()=>{
 assert.equal(shiftDay('2026-12-30',7),'2027-01-06');
 assert.equal(shiftDay('2028-02-28',1),'2028-02-29');
 assert.equal(shiftDay('2026-11-12',-7),'2026-11-05');
 assert.equal(localDay(new Date(2026,10,12,0,15)),'2026-11-12');
});
test('calendar day navigation survives daylight saving transitions',()=>{
 const result=execFileSync(process.execPath,['--input-type=module','-e',"import {shiftDay} from './src/availability.mjs'; process.stdout.write(shiftDay('2026-03-28',1)+'|'+shiftDay('2026-10-24',1))"],{cwd:new URL('..',import.meta.url),env:{...process.env,TZ:'Europe/Bucharest'},encoding:'utf8'});
 assert.equal(result,'2026-03-29|2026-10-25');
});
test('expired or malformed slots disappear before rendering',()=>{
 assert.deepEqual(remainingSlots(['2026-11-12T09:00:00','invalid','2026-11-12T10:00:00','2026-11-12T10:30:00'],new Date('2026-11-12T10:00:00')),['2026-11-12T10:30:00']);
});
const rows=[
 {doctorId:1,name:'Dr. Ana',specialty:'Cardiologie',days:[{date:'2026-11-12',slots:[]},{date:'2026-11-13',slots:['2026-11-13T09:00:00']}]},
 {doctorId:2,name:'Dr. Mihai',specialty:'Medicină internă',days:[{date:'2026-11-12',slots:['2026-11-12T11:00:00']},{date:'2026-11-13',slots:[]}]},
 {doctorId:3,name:'Dr. Elena',specialty:'Cardiologie',days:[{date:'2026-11-12',slots:['2026-11-12T09:00:00']}]},
];
test('today and weekly views show only doctors with free future slots',()=>{
 const now=new Date('2026-11-12T08:00:00');
 assert.deepEqual(availableDoctors(rows,'2026-11-12','','',now).map(d=>d.doctorId),[3,2]);
 assert.deepEqual(availableDoctors(rows,null,'','',now).map(d=>d.doctorId),[3,2,1]);
 assert.deepEqual(availableDoctors(rows,'2026-11-13','ana','Cardiologie',now).map(d=>d.doctorId),[1]);
 assert.deepEqual(availableDoctors(rows,null,'medicina','',now).map(d=>d.doctorId),[2]);
 assert.equal(rows[0].days.length,2);
});
