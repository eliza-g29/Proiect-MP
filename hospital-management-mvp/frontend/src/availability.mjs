export function localDay(now=new Date()) {
  return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
}
export function shiftDay(day,offset) {
  const date=new Date(`${day}T12:00:00`);date.setDate(date.getDate()+offset);return localDay(date);
}
export function dayLabel(day,options={weekday:'short',day:'numeric',month:'short'}) {
  return new Intl.DateTimeFormat('ro-RO',options).format(new Date(`${day}T12:00:00`));
}
export function remainingSlots(slots,now=new Date()) {
  return slots.filter(slot=>typeof slot==='string'&&new Date(slot).getTime()>now.getTime()).sort();
}
export function availableDoctors(rows,day=null,query='',specialty='',now=new Date()) {
  const normalize=value=>value.normalize('NFD').replace(/\p{Diacritic}/gu,'').toLocaleLowerCase('ro');
  const normalized=normalize(query);
  return rows.map(row=>({...row,days:row.days.filter(d=>!day||d.date===day).map(d=>({...d,slots:remainingSlots(d.slots,now)}))}))
    .filter(row=>(!specialty||row.specialty===specialty)&&normalize(`${row.name} ${row.specialty}`).includes(normalized)&&row.days.some(d=>d.slots.length))
    .sort((a,b)=>a.days.flatMap(d=>d.slots).sort()[0].localeCompare(b.days.flatMap(d=>d.slots).sort()[0])||a.name.localeCompare(b.name,'ro'));
}
