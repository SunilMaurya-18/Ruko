// On-device OCR (Hindi + English). The image is decoded and read in this browser; only /ocr assets are fetched.
const MAX_SIDE = 2000;

let workerPromise = null;
let progressListener = null;

function getWorker() {
  if (!workerPromise) {
    workerPromise = import('tesseract.js')
      .then(({ createWorker }) => createWorker(['hin', 'eng'], 1, {
        workerPath: '/ocr/worker.min.js',
        corePath: '/ocr/core',
        langPath: '/ocr/lang',
        workerBlobURL: false,
        cacheMethod: 'none',
        gzip: true,
        logger: ({ status, progress }) => progressListener?.({
          stage: status === 'recognizing text' ? 'reading' : 'loading',
          progress,
        }),
      }))
      .catch((error) => {
        workerPromise = null;
        throw error;
      });
  }
  return workerPromise;
}

async function downscale(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return canvas;
}

/** Returns `{ text, confidence }` with confidence in 0..1. */
export async function recognize(file, onProgress) {
  progressListener = onProgress;
  try {
    const [image, worker] = await Promise.all([downscale(file), getWorker()]);
    const { data } = await worker.recognize(image);
    return { text: data.text.trim(), confidence: Math.min(1, Math.max(0, data.confidence / 100)) };
  } finally {
    progressListener = null;
  }
}
