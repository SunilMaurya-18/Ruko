# Accessibility: TalkBack walk-through of the result screen

Plan Phase 6. `pwa/e2e/result-a11y.test.js` (part of `npm run test:e2e` in CI) already checks, at a 360 px phone size:
- focus moves to the screen title;
- the band is in a live region;
- every control has an accessible name;
- the result carries its language;
- headings don't skip levels;
- touch targets are at least 48 px;
- there is no sideways scrolling;
- all of the above also hold in dark mode, and the page and band follow the phone's colour scheme.

`pwa/src/styles.test.js` (part of `npm test`) checks WCAG AA contrast for every colour pair the light and dark themes use: at least 4.5:1 for text, and 3:1 for field borders, severity stripes, and the focus ring.

What it cannot check is how TalkBack actually reads the screen. That needs a phone.

## Set-up

- Android phone with TalkBack on (Settings › Accessibility › TalkBack), speech output set to Hindi (Google TTS, `hi-IN`) and English (`en-IN`) installed.
- Chrome, Ruko installed from the deployed URL ("Add to Home screen").
- Messages: `sc-002` (Hindi, high concern), `sc-001` (English, high concern), `ed-002` (Hindi, education).

## Walk-through

For each message, share it to Ruko (or paste it and tap "जाँचें"), then on the result screen, swipe right through every element from the top and fill in the table.

| # | Check | sc-002 | sc-001 | ed-002 |
| --- | --- | --- | --- | --- |
| 1 | On arrival, TalkBack reads the screen title first (not the page URL or "web view") | | | |
| 2 | The band (for example "बहुत चिंता की बात है") is announced without having to swipe to it | | | |
| 3 | Hindi text is spoken by the Hindi voice and English text by the English voice (no letter-by-letter reading) | | | |
| 4 | Counts ("2 ख़तरे के निशान · …") are read as words; the "·" separators are not read out as symbols | | | |
| 5 | Each red flag is read as one item, followed by its quoted evidence, in order | | | |
| 6 | "सुनें" / "Listen" is announced as a button; double-tap plays; TalkBack and the spoken result don't talk over each other in a way that loses words | | | |
| 7 | "रुकें और सोचें" (pause) and the recovery and SEBI Check links are announced as links with their full labels | | | |
| 8 | The footer ("निशान न मिलने का मतलब यह नहीं कि पैसा भेजना सुरक्षित है …") is reachable and read | | | |
| 9 | With font size at the largest setting, nothing is cut off and no sideways scrolling is needed | | | |
| 10 | Back gesture returns to the message screen with the text still there | | | |
| 11 | With the phone in dark mode, every card, band, and button is readable, and the focus ring is visible | | | |

Mark each cell ✓, ✗, or a short note. Fix every ✗ before the demo; note anything left open here.

## Result

| Date | Phone and Android version | TalkBack version | Done by | Open issues |
| --- | --- | --- | --- | --- |
| | | | | |
