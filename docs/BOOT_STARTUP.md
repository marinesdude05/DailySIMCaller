# Background after-reboot behavior (v1.3.3)

After `BOOT_COMPLETED`, `BootReceiver` clears legacy startup calling flags, schedules a network-constrained persistent JobScheduler job when enabled, and restores the optional 10 AM reminder. It never launches an activity, places calls, or starts a phone-call foreground service. The job reads the existing HTTPS CSV into the local app cache and persists a status string.

This is not a continuously running background process, and Android may delay or skip the job due to Force Stop, job quotas, battery policy or a missing network. The user can open the app to check the last status and refresh numbers manually. `Prepare number list quietly after restart` is ON initially and can be disabled.

The default Android Home screen is not changed. In-app guided setup opens the appropriate settings pages but cannot approve access on the user's behalf. See `RESTART_SETUP_v1.3.3.md`.
