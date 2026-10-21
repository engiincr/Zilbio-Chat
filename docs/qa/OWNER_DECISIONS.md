# Zilbio v3.0.4 — Owner Decisions Register
### Tracked ambiguous decisions and owner resolutions per Addendum B.4

| ID | Feature / Component | Ambiguity Description | Antigravity Recommendation | Owner Status | Resolution / Answer |
|---|---|---|---|---|---|
| OD-01 | Call Signaling (Part 7.1) | Behavior when caller terminates before receiver answers (ring duration < 5s) | Cancel incoming call overlay immediately on receiver device and log as "Canceled Call" | PENDING | Awaiting owner input. Currently defaults to instant dismissal with missed call logging. |
| OD-02 | Disappearing Messages (Part 9.4) | Retention of media attachments on device disk cache after message expiry | Automatically purge cached audio/image files from local cache directory upon message expiration | PENDING | Awaiting owner input. Currently local cache retains until Storage Manager cleanup. |
| OD-03 | Quota Warning Banner (Part 4.2) | Behavior when Gemini daily limit (1400) is reached | Fallback silently to ML Kit on-device model without interrupting messaging flow, display non-blocking snackbar | PENDING | Awaiting owner input. Implemented as non-blocking ML Kit fallback. |
