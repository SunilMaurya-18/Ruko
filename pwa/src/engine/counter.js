// How many results this phone produced on its own, reported later as ruko_on_device_total. A count only: no text,
// no result, no timestamp.

export const KEY = 'ruko.on_device_total';

export function onDeviceTotal(storage = globalThis.localStorage) {
  try {
    return Math.max(0, Number.parseInt(storage.getItem(KEY) ?? '0', 10) || 0);
  } catch {
    return 0;
  }
}

export function countOnDevice(storage = globalThis.localStorage) {
  const next = onDeviceTotal(storage) + 1;
  try {
    storage.setItem(KEY, String(next));
  } catch {
    // Storage unavailable: the result still shows; only the count is lost.
  }
  return next;
}
