// Exact integer VND and calendar parsing shared by the two confirmed notification envelopes.
export function parseVnd(value: string): number | null {
  if (!/^[+-]?(?:\d+|\d{1,3}(?:,\d{3})+|\d{1,3}(?:\.\d{3})+)$/.test(value)) return null;
  const amount = Number(value.replace(/[,.]/g, ''));
  return Number.isSafeInteger(amount) ? amount : null;
}

export function bankTimestamp(date: string, time: string): string | null {
  const [day, month, year] = date.split('/').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  if (year < 1900 || year > 9999 || value.getUTCFullYear() !== year || value.getUTCMonth() !== month - 1
    || value.getUTCDate() !== day || hour > 23 || minute > 59) return null;
  return `${date} ${time}`;
}
