# Saathi prototype (v3)

A clickable, browser-only mock of the Saathi app for reviewing the new UI before it's built in Compose.
It's one file, `index.html`, with plain HTML, CSS and JavaScript. There's nothing to install or build.

## Open it

- Double-click `index.html`, or drag it into Chrome / Safari / Firefox.
- On a phone, open the same file (or the shared link) and it fills the screen like the app.

On a laptop you see the phone on the left and **demo controls** on the right. On a phone the controls open from the round button at the bottom-left.

## What to try

1. **Seven taps to ready**: grant the steps, or press *Later*.
   In the Brain step, use *Demo API keys → Gemini · rejected* to see the inline error. Then use *Gemini · valid* to see the "Gemini key saved · verified in 1.2 s" toast.
2. **Today**: switch Off / Ask me / Auto, change the auto-answer delay, then tap *Call back* or *Transcript*.
3. **Profile drawer**: tap the avatar (top right) to open Persona, Brain, the greeting copy, phone setup and privacy.
4. **Persona**: change the language, style or voice. Each change auto-saves, shows a tick and offers *Undo*, and the greeting updates live.
5. **Brain**:
   - Replace or remove a key (with confirmation).
   - Pick a model.
   - Turn on *Answer when offline*.
   - Open Gemma for the on-device screen.
6. **On-device (Gemma 4 E2B installed)**:
   - Run the offline test.
   - Choose *When offline / Always / Never* and GPU or CPU.
   - Download Qwen.
   - Tap E4B to see why it's locked.
   - Delete Gemma, then download it again.
7. **Calls**:
   - *Simulate a call* (Mom, unknown courier, loan spam) and watch the auto-answer countdown.
   - Let Saathi answer, then press *Stop AI* and type replies.
   - When the call ends it's saved with a summary.
   - Delete a call (confirm + Undo).
8. Toggle **Network → Offline** and simulate a call to see Gemma answer on the phone.

## Notes

- Everything is simulated: callers, scripts, keys, reply times and downloads. Downloads run sped up and are labelled "demo · sped up".
- The demo keys are fake. A key containing "revoked" is rejected; any other key in the right format is accepted. Real keys are never needed and nothing leaves the page.
- Model names, sizes and the RAM rule come from `app/src/main/java/com/aicallagent/app/agent/ModelDownloads.kt`.
- Some behaviour is a proposal and isn't in the app yet:
  - Gemma as an **offline backup** behind Gemini (the app picks only one brain today).
  - Manual GPU/CPU choice.
  - On-device test calls.
- Hosted copy of this prototype: https://claude.ai/artifact/WmgVRwixPCXAH2Jt4W4ED9 (private until shared from its Share menu).
- Design canvas with all versions: https://claude.ai/artifact/TLjdvQa1iPNGPr1nRqJa9i (private to the owner unless shared).
