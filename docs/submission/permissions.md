# Permissions

What the Ruko PWA asks the phone for, when, and why. Checked against the code (`pwa/src`, `pwa/public/manifest.webmanifest`) and the server's `Permissions-Policy` header.

| Capability | Asked when | Why | Prompt |
| --- | --- | --- | --- |
| Share target | When the user installs the app (Add to Home screen) | Lets WhatsApp or Telegram "Share" a message to Ruko (`share_target`, GET `/share`). The service worker answers `/share` from the cached app, so the shared text never goes to the network in the URL. | None; part of installing |
| Clipboard read | Only when the user taps **चिपकाएँ / Paste** | Pastes a copied message into the box | Browser may ask once |
| Clipboard write | Only when the user taps a copy button (result screen, complaint draft) | Copies text the user chose to copy | Usually none |
| Notifications (optional) | Only when the user asks for a pause-journal reminder | One fixed-text reminder when the 24-hour pause ends | Browser asks; Ruko works fully if refused |
| Photo | Only when the user taps **फ़ोटो से पढ़ें / Read from photo** | Reads a screenshot with on-device OCR. The image never leaves the phone. | File picker; no camera permission |
| Microphone | Not requested in this build | Spoken input (ASR) is behind a server flag that is off, and the PWA has no recording code. If it is turned on later, the plan is to ask on tap only, with a consent header on the request. | None |
| Speaker | When the user taps **सुनें / Listen** | Plays the spoken result (Bhashini audio, or the phone's own voice) | None |

**Not used:** camera, location, contacts, background sync, push messages, Bluetooth, USB. The server sends `Permissions-Policy: camera=(), geolocation=(), microphone=(self)`, so the page cannot use the camera or location even by mistake.

**Kept on the phone (`localStorage`):** the chosen language, the pause journal, and a count of on-device checks. Message text is not stored, and the journal is never sent anywhere (`NoJournalRouteTest` and the e2e test `journal-network.test.js` enforce this).

**Sent to the server:** only the masked message text (phone numbers, account numbers, Aadhaar, PAN, and OTPs are hidden first), the language, and the source. It lives for one request and is never written to disk or logs.
