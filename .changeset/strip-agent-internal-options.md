---
"@kilocode/cli": patch
---

Fix 400 errors on non-default agents (Ask, Plan, org modes, marketplace agents) where internal agent metadata (`displayName`, `id`, `source`) leaked into the model request body and was rejected by strict providers.
