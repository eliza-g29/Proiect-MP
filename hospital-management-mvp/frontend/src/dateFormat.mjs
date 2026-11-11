const dateFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit', month: 'short', year: 'numeric',
});
const dateTimeFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
});

/** Missing or invalid API dates must not interrupt rendering a page. */
export function formatDate(value, withTime = false) {
  let date;
  if (value instanceof Date) {
    date = value;
  } else if (typeof value === 'number') {
    date = new Date(value);
  } else if (typeof value === 'string' && value.trim()) {
    const calendarDate = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (calendarDate) {
      const [, year, month, day] = calendarDate.map(Number);
      // A birth date is a calendar date, rather than midnight in UTC.
      date = new Date(0);
      date.setFullYear(year, month - 1, day);
      date.setHours(0, 0, 0, 0);
      if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return '—';
    } else {
      date = new Date(value);
    }
  } else {
    return '—';
  }
  if (!Number.isFinite(date.getTime())) return '—';
  return (withTime ? dateTimeFormatter : dateFormatter).format(date);
}
