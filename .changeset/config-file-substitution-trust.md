---
"@kilocode/cli": patch
---

Prevent project config from reading local files or environment variables via `{file:...}` / `{env:...}` references. These references now resolve only in trusted user-owned config (global config, `KILO_CONFIG`, well-known org config), closing a path where a malicious project could exfiltrate local files through a provider API key.
