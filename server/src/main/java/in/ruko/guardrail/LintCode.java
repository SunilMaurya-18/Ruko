package in.ruko.guardrail;

/** TRD §3 linter checks. */
public enum LintCode {
    /** Recommendation language: buy, sell, hold, in English, Hindi, and Hinglish. */
    L1,
    /** Ticker symbols from the dated NSE/BSE snapshot (SEBI, NSDL, UPI, IFSC and other acronyms allowed). */
    L2,
    /** Verdict words (safe, scam, fraud, ...) anywhere but the fixed footer key. */
    L3,
    /** Card at most 25 words, analogy at most 40, every evidence quote present in the input. */
    L4,
    /** Links only to allowlisted hosts or tel:1930; no markup. */
    L5,
    /** Output language matches the request. */
    L6,
    /** Price targets or return predictions outside a quoted span. */
    L7,
    /** Band, content class, analogy, and signal ids equal the rule result. */
    L8
}
