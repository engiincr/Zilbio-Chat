# Zilbio v3.0.4 — Hardware & Manual Test Queue
### Physical Device Protocols and Unverified Hardware Items per Section 4 & Addendum B.5

All items below require physical hardware, RF environments, or live acoustic setups. In strict compliance with Rule Zero and Addendum B.5, none of these items are claimed as automated passes in CI. Each remains **NOT VERIFIED** until physical multi-device execution.

---

| Item ID | Section / Feature | Description | Required Physical Environment | Status | Verification Protocol (For Next Pack) |
|---|---|---|---|---|---|
| **MAN-01** | Part 8.6 | Two-Way Voice Delivery | 2 physical Android phones running Zilbio with functional microphones, earpieces, and speakers. | **NOT VERIFIED** | Establish WebRTC voice call between Device A and Device B. Verify bidirectional 48kHz Opus audio stream without echo, acoustic howling, or packet dropouts over 60 seconds of continuous speech. |
| **MAN-02** | Part 7.2 | Lock-Screen Heads-Up Notification | Physical Android device with display locked and screen off, receiving FCM call signaling push. | **NOT VERIFIED** | Place inbound call to locked device. Verify that full-screen intent / high-priority notification wakes the display, shows caller identity, and allows 1-tap answering directly from lock screen. |
| **MAN-03** | Part 8.8 | Camera Beautification Visual Quality | Physical Camera2 sensor hardware with real-time GL surface rendering. | **NOT VERIFIED** | Open video call or preview with beautification filter enabled. Verify real-time skin smoothing on front camera at 30fps without GPU thermal throttling or preview stutter. |
| **MAN-04** | Part 0.5 | Real-Network Call Latency | 2 physical devices connected over public heterogeneous cellular networks (4G/5G). | **NOT VERIFIED** | Measure end-to-end audio round-trip time across ExpressTURN relay server over public internet. Confirm latency remains below acceptable threshold (<300ms). |
| **MAN-05** | Part 14.9, B.5 | Background Notification Actions | Physical device running Android 12+ with app backgrounded or process cached. | **NOT VERIFIED** | Receive message and call notifications while Zilbio is completely backgrounded. Verify action buttons ("Yanıtla", "Kabul Et", "Reddet") execute immediately without crashing. |
