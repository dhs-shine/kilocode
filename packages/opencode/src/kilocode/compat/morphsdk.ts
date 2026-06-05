import { createRequire } from "module"

// Force Bun to load @morphllm/morphsdk/tools/warp-grep/client via its self-contained
// CJS bundle instead of the pre-split ESM barrel.
//
// WHY THIS EXISTS
// ---------------
// @morphllm/morphsdk ships a pre-split ESM distribution:
//   dist/tools/warp_grep/client.js  (805 bytes, barrel)
//     └─ imports from ../../chunk-P7G3CJB2.js
//     └─ side-effects ../../chunk-63VHBANJ.js ... (12 more chunks, 52 total)
//
// When Bun bundles the CLI with `splitting: true + minify: true`, it merges
// these external pre-split chunks into its own chunk graph. Bun 1.3.14 intermittently
// generates invalid minified ESM in that process:
//
//   SyntaxError: Exported binding 'G9' needs to refer to a top-level declared variable.
//
// The error is non-deterministic (Bun's parallel bundler uses different orderings
// per run), so the build sometimes succeeds and sometimes fails on Windows x64.
//
// The CJS bundle (client.cjs, ~2300 lines) is fully self-contained with no external
// chunk imports. `createRequire` lets Bun inline the CJS module directly without
// running it through the ESM splitter.
//
// HOW TO DETECT THIS FOR FUTURE DEPS
// -----------------------------------
// If a new dependency causes `SyntaxError: Exported binding '...' needs to refer to
// a top-level declared variable` in release builds, check whether its ESM entry point
// is a barrel that re-imports from internal `chunk-*.js` files:
//
//   head -5 node_modules/<pkg>/dist/index.js
//     → imports { ... } from "./chunk-XYZ123.js"  ← pre-split ESM
//
// If so, add a CJS bridge here and re-export from it instead of importing the package
// directly. Always verify there is a `.cjs` (or CJS `main`) alternative.
const req = createRequire(import.meta.url)

// Type-cast via the package's own .d.ts so callers get full type safety.
const mod = req("@morphllm/morphsdk/tools/warp-grep/client") as typeof import("@morphllm/morphsdk/tools/warp-grep/client")

export const WarpGrepClient = mod.WarpGrepClient
