// Plain-Hindi wording for the result screen. No percentages, no true/false, no "safe" verdicts.

export const BAND_LABELS = {
  high_concern: 'बहुत चिंता की बात है',
  some_concern: 'कुछ चिंता की बात है',
  few_flags_still_verify: 'कम निशान मिले, फिर भी जाँचें',
  not_enough_to_judge: 'जाँचने के लिए काफ़ी जानकारी नहीं',
};

export const BAND_HINTS = {
  high_concern: 'पैसा भेजने से पहले रुकें।',
  some_concern: 'पैसा भेजने से पहले अच्छी तरह जाँचें।',
  few_flags_still_verify: 'निशान कम हैं, पर इसका मतलब यह नहीं कि सब ठीक है।',
  not_enough_to_judge: 'मैसेज बहुत छोटा है या साफ़ पढ़ा नहीं गया। पूरा मैसेज डालकर फिर जाँचें।',
};

export const CLASS_LABELS = {
  promotion: 'यह मैसेज कुछ बेच रहा है या पैसे माँग रहा है।',
  education: 'यह मैसेज निवेश की जानकारी देता है।',
  mixed: 'इसमें जानकारी भी है और पैसे की माँग भी।',
  unknown: 'यह मैसेज किस तरह का है, यह साफ़ नहीं है।',
};

export const SNAPSHOT_LABELS = {
  listed: 'रुको के पास की SEBI सूची में यह नंबर है। फिर भी SEBI Check पर देखें।',
  not_listed: 'रुको के पास की SEBI सूची में यह नंबर नहीं है।',
};

export const FOOTER_LABELS = {
  no_flags_not_safe: 'निशान न मिलने का मतलब यह नहीं कि पैसा भेजना ठीक है। पैसा भेजने से पहले हमेशा जाँचें।',
};

export const SEBI_CHECK_URL = 'https://siportal.sebi.gov.in/intermediary/sebi-check';

export function countsLine(counts) {
  return [
    `${counts.red_flags} ख़तरे के निशान`,
    `${counts.couldnt_verify} बातें जाँची नहीं जा सकीं`,
    `${counts.reassuring} भरोसे की बातें`,
  ].join(' · ');
}
