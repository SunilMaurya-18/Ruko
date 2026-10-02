/** Builds the transcript from Web Share Target params (WhatsApp sends `text`; some apps put links in `url`). */
export function sharedTextFrom(params) {
  const text = params.get('text')?.trim() ?? '';
  const url = params.get('url')?.trim() ?? '';
  const title = params.get('title')?.trim() ?? '';
  const parts = [text || title];
  if (url && !parts[0].includes(url)) {
    parts.push(url);
  }
  return parts.filter(Boolean).join('\n');
}
