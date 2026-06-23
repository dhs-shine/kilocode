---
"kilo-code": patch
---

Fix AWS Bedrock credential provider crash (`is not a function. is a Symbol`) caused by esbuild identifier minification renaming an AWS SDK function and an internal Symbol to the same short identifier in the CJS bundle. Disabling identifier minification for the extension host bundle resolves the collision while preserving syntax and whitespace minification.
