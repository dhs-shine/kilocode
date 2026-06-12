---
"@kilocode/cli": patch
---

Restart the daemon when `kilo console` is invoked with an explicit `--port` or `--hostname` that doesn't match the already-running daemon, instead of silently reusing the old connection.
