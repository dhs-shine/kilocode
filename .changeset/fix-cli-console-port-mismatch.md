---
"@kilocode/cli": patch
---

Restart the daemon when `kilo console` or `kilo daemon start` receives explicit network options that don't match the running daemon, instead of silently ignoring the requested settings.
