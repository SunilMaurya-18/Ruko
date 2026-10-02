import shippedSnapshot from '../../../shared/snapshot/sebi-intermediaries.json' with { type: 'json' };
import { t } from '../i18n/catalog.js';
import { analyzeOnDevice } from './analyze.js';
import { countOnDevice } from './counter.js';
import { snapshotIndex } from './signals.js';

export { OnDeviceRejected } from './analyze.js';

// Loaded on demand (and precached by the service worker) for when /analyze cannot be reached.
const SNAPSHOT = snapshotIndex(shippedSnapshot);

/** Runs the shared rules on this phone over already-masked text; same response shape, engine "on_device". */
export function analyzeOffline(request) {
  const result = analyzeOnDevice(request, { i18n: t, snapshot: SNAPSHOT });
  countOnDevice();
  return result;
}
