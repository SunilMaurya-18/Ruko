# Persona and demo journey

Ruko has one user and one journey (TRD §0). Any feature that needs a second persona or a second demo is out of scope.

## Ramesh

- 46, runs a hardware shop in a tier-3 town in Uttar Pradesh. Hindi first; reads Roman-script Hinglish on WhatsApp; little English.
- Low-end Android phone, patchy 3G/4G, prepaid data. Uses WhatsApp and Telegram daily and UPI for the shop.
- Has a demat account opened through his bank two years ago. Has bought a few shares and one mutual fund.
- Is in several "stock tips" WhatsApp and Telegram groups that relatives and customers added him to.
- Trusts voice more than text. Would not read a long screen; will listen to a short spoken answer.
- Has heard of SEBI but has never used SEBI Check or SCORES. Does not know about 1930.

**What he needs from Ruko:** a fast, spoken, plain-Hindi answer to "should I stop before I pay?", with the reason he can repeat to his son, and the right next step: verify, wait, or get help.

**What Ruko must never do for him:** tell him a tip is safe, tell him what to buy or sell, or ask him to type long text.

## The demo journey

1. **The message arrives.** In a Telegram group: "गारंटी के साथ हर महीने 20% मुनाफा … VIP ग्रुप फीस ₹4999, इस UPI पर भेजें … SEBI registered INH000012345 … सिर्फ आज".
2. **Share to Ruko.** He long-presses, taps Share, picks Ruko. The text opens in Ruko as an editable transcript; personal numbers are masked on the phone before anything is sent.
3. **Spoken result.** Ruko speaks in Hindi: the band (high concern), the class (promotion), the top flags with the exact words that triggered them (guaranteed return, personal UPI for a fee, paid VIP group, urgency), one analogy for "guaranteed return", and the footer that no flags does not mean safe.
4. **SEBI Check handoff.** The registration number is shown as "couldn't verify". One tap opens SEBI Check with the number ready to paste.
5. **Pause.** Ruko offers the three-line journal (why send money, time horizon, can you afford to lose it) and "wait 24 hours". The answers stay on the phone.
6. **Already paid.** Variant: he says he already sent ₹4999. Ruko opens recovery: call 1930 now, then the bank, then cybercrime.gov.in, plus an editable complaint draft of at most 200 words.
7. **Broker problem.** Variant: a registered broker is not releasing funds. Ruko shows plain-Hindi SCORES steps and the SCORES link.
8. **Close.** Study numbers and guardrails (linter, log audit, offline parity).

Steps 2–5 also work with the API down (on-device rules and browser speech).
