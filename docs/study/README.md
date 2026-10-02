# Behaviour study: protocol

Plan Phase 6: 8–10 consenting adults, 10 messages, with and without Ruko. Two measures:

- **Correct identification:** the share of messages a participant calls correctly (a scam as "risky", a genuine message as "fine"), with Ruko against without. Target: at least 30 percentage points better with Ruko.
- **Restating the reason:** after using Ruko, the share of messages where the participant can say the main reason in their own words. Target: at least 80%.

Report the actual number of participants (n), even if it is below 8. Do not round it up or add sessions that did not happen.

## What is kept

Only `results.csv`: a participant code (P01, P02, …), the group, the message id, and coded answers. No names, phone numbers, ages, photos, recordings, or free-text notes. Consent is given verbally and ticked on the sheet; there is no signed form to keep. Do not run Ruko on the participant's own phone or messages: use the facilitator's phone with the messages below.

## Messages

Ten messages from `shared/fixtures/fixtures.v0.json`, chosen before any session and not changed afterwards. Each set has three scams and two genuine messages. `sc-033` is a known gap (Ruko misses one of its flags) and stays in on purpose.

| Set A | Kind | Set B | Kind |
| --- | --- | --- | --- |
| sc-001 | scam | sc-002 | scam |
| sc-005 | scam | sc-017 | scam |
| sc-020 | scam | sc-033 | scam |
| ed-005 | genuine | ed-008 | genuine |
| ed-012 | genuine | ed-003 | genuine |

- **Group A** sees set A without Ruko, then set B with Ruko.
- **Group B** sees set B without Ruko, then set A with Ruko.

Alternate groups by arrival (P01 A, P02 B, P03 A, …) so each set is seen both ways. Within a set, use the order in the table.

## Session (about 15 minutes)

1. Read the consent text below in the participant's language. Continue only on a clear yes. They may stop at any time; then delete their rows.
2. **Without Ruko:** show each message of the first set on the facilitator's phone as a forwarded WhatsApp message. Ask: "If a friend sent you this, would you send money or act on it?" Record `risky` (would not act, thinks it is a trap), `fine` (would trust it), or `unsure`.
3. **With Ruko:** for each message of the second set, share it to Ruko, let the participant read or listen to the result, then ask the same question and record the answer. Then ask: "In your own words, what is the main reason?" Record `restated = yes` if their answer matches any flag or reason Ruko showed (for a genuine message, that it reads like general education), otherwise `no`. Write nothing else down.
4. Thank them. Do not explain the "right" answers until the end of the session.

Facilitator: do not hint, read the result aloud for them, or explain the flags during the with-Ruko part. Ruko must do the explaining.

## Consent text

**हिंदी:** "हम एक ऐप की जाँच कर रहे हैं जो निवेश वाले मैसेज के खतरे समझाता है। मैं आपको मेरे फ़ोन पर 10 मैसेज दिखाऊँगा और हर एक के बारे में एक-दो सवाल पूछूँगा। इसमें लगभग 15 मिनट लगेंगे। हम आपका नाम, नंबर या कोई निजी जानकारी नहीं लिखेंगे, सिर्फ़ आपके जवाब एक कोड के साथ लिखे जाएँगे। आप कभी भी रुक सकते हैं, और तब आपके जवाब मिटा दिए जाएँगे। यह कोई परीक्षा नहीं है। क्या आप हिस्सा लेना चाहेंगे?"

**English:** "We are testing an app that explains the warning signs in investment messages. I will show you 10 messages on my phone and ask one or two questions about each. It takes about 15 minutes. We will not write down your name, number, or anything personal; only your answers, under a code. You can stop at any time, and then your answers are deleted. This is not a test of you. Would you like to take part?"

## Recording and scoring

Copy `results-template.csv` to `results.csv` and add one row per message: ten rows per participant.

```
participant,group,message_id,condition,answer,restated
P01,A,sc-001,without,risky,
P01,A,sc-002,with,risky,yes
```

`restated` is empty for `without` rows. Then:

```sh
node scripts/score-study.mjs docs/study/results.csv --write
```

This checks the sheet (it refuses names, free text, a message shown in the wrong condition, or duplicates), counts only participants with all ten answers, and writes `docs/study/results.md` with n and both measures. "Unsure" counts as not correctly identified.
