// Screen wording around the catalogue text. Band, class, footer, card, and analogy text come from the shared
// catalogue (src/i18n/catalog.js); this file only holds headings and buttons. No percentages, no verdicts.

import { linkUrl } from './recovery/content.js';

export const SEBI_CHECK_URL = linkUrl('sebi_check');

const UI = {
  hi: {
    result: 'नतीजा',
    empty: 'अभी कोई मैसेज जाँचा नहीं गया है।',
    check: 'मैसेज जाँचें',
    flags: 'ख़तरे के निशान',
    unverified: 'जो रुको जाँच नहीं सका',
    reassuring: 'भरोसे की बातें',
    reassuringHint: 'इनसे ख़तरे के निशान कम नहीं होते।',
    analogy: 'आसान भाषा में',
    evidence: 'मैसेज में लिखा है: ',
    regNumber: 'रजिस्ट्रेशन नंबर',
    sebiCheck: 'SEBI Check पर देखें',
    copy: 'नंबर कॉपी करें',
    copyNumber: (number) => `नंबर ${number} कॉपी करें`,
    copied: 'कॉपी हो गया',
    opensSite: 'SEBI की वेबसाइट नई विंडो में खुलेगी',
    listen: 'सुनें',
    stop: 'सुनाना रोकें',
    listenHint: 'नतीजा बोलकर सुनाया जाएगा',
    phoneVoice: 'फ़ोन की आवाज़ में सुना रहे हैं',
    noVoice: 'इस फ़ोन पर बोलकर सुनाने की सुविधा नहीं है। नतीजा ऊपर लिखा है।',
    how: 'रुको कैसे तय करता है',
    recovery: 'पैसे भेज चुके हैं? मदद लें',
    again: 'दूसरा मैसेज जाँचें',
    details: 'मिली जानकारी और भेजा गया टेक्स्ट',
    nothingFound: 'कोई नंबर, लिंक या UPI आईडी नहीं मिली।',
    hiddenPhones: 'फ़ोन नंबर (छिपे हुए)',
    maskedNote: 'निजी नंबर छिपाकर भेजे गए। असली मैसेज सिर्फ इसी फ़ोन पर है।',
    entities: {
      upi_ids: 'UPI आईडी', reg_numbers: 'SEBI रजिस्ट्रेशन नंबर', ifsc: 'IFSC कोड', urls: 'लिंक',
      return_claims: 'मुनाफ़े के दावे', app_names: 'रिमोट कंट्रोल ऐप', qr_phrases: 'QR / स्कैन',
    },
    counts: (c) => `${c.red_flags} ख़तरे के निशान · ${c.couldnt_verify} बातें जाँची नहीं जा सकीं · ${c.reassuring} भरोसे की बातें`,
    onDevice: 'इंटरनेट से जाँच नहीं हो पाई, इसलिए यह जाँच इसी फ़ोन पर हुई। नियम वही हैं।',
    pause: 'पैसे भेजने से पहले रुकें',
    pauseHint: 'तीन छोटे सवालों के जवाब लिखें। जवाब सिर्फ इसी फ़ोन पर रहते हैं।',
    pauseLink: 'रुकें और सोचें',
  },
  en: {
    result: 'Result',
    empty: 'No message has been checked yet.',
    check: 'Check a message',
    flags: 'Warning signs',
    unverified: 'What Ruko could not check',
    reassuring: 'Reassuring signs',
    reassuringHint: 'These do not cancel any warning sign.',
    analogy: 'In simple words',
    evidence: 'The message says: ',
    regNumber: 'Registration number',
    sebiCheck: 'Look it up on SEBI Check',
    copy: 'Copy number',
    copyNumber: (number) => `Copy number ${number}`,
    copied: 'Copied',
    opensSite: 'opens the SEBI website in a new window',
    listen: 'Listen',
    stop: 'Stop',
    listenHint: 'Reads the result aloud',
    phoneVoice: "Using this phone's voice",
    noVoice: 'This phone cannot read aloud. The result is written above.',
    how: 'How Ruko decides',
    recovery: 'Already sent money? Get help',
    again: 'Check another message',
    details: 'What was found, and the text that was sent',
    nothingFound: 'No numbers, links, or UPI IDs were found.',
    hiddenPhones: 'Phone numbers (hidden)',
    maskedNote: 'Personal numbers were hidden before sending. The original message stays on this phone.',
    entities: {
      upi_ids: 'UPI IDs', reg_numbers: 'SEBI registration numbers', ifsc: 'IFSC codes', urls: 'Links',
      return_claims: 'Return claims', app_names: 'Remote-control apps', qr_phrases: 'QR / scan',
    },
    counts: (c) => `${c.red_flags} warning signs · ${c.couldnt_verify} could not be checked · ${c.reassuring} reassuring`,
    onDevice: 'The server could not be reached, so this check ran on this phone. The rules are the same.',
    pause: 'Pause before you send money',
    pauseHint: 'Answer three short questions. Your answers stay on this phone only.',
    pauseLink: 'Pause and think',
  },
};

export function ui(lang) {
  return UI[lang] ?? UI.hi;
}
