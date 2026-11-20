# Daily SIM Caller v1.3.8 — UI shortcut order

Updated from v1.3.7 (no daily scheduling). Moves the **Open App Info if Accessibility is restricted** button to sit immediately under **Open Default Home app Settings** in the same group as **Open Default Phone app Settings**.

This is a pure relocation of the button's existing onClick handler and label. No permission handling, scheduling, manual Call Now action or reboot calling logic was changed. Version code increased to 12 so an APK rebuilt from this source can be installed over v1.3.7 with the original signing key.

Verification: structural/source checks passed, but Android build/signing and device runtime are not verified. The earlier automatic calling after reboot issue remains open.
