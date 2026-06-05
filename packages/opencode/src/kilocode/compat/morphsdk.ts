// kilocode_change - new file
// Re-exports from @morphllm/morphsdk/tools/warp-grep/client.
//
// WHY THIS INDIRECTION EXISTS
// ----------------------------
// @morphllm/morphsdk ships a pre-split ESM distribution for this path:
//   dist/tools/warp_grep/client.js  (805-byte barrel)
//     └─ imports from ../../chunk-P7G3CJB2.js ... (52 total pre-split chunks)
//
// Bun 1.3.14 bundling with `conditions: ["browser"]` resolves via the "import" condition
// (ESM barrel) even inside createRequire() calls. When its ESM splitter merges those
// external pre-split chunks into the bundle, it generates invalid minified output:
//   SyntaxError: Exported binding 'G9' needs to refer to a top-level declared variable.
//
// FIX: script/build.ts adds a morphsdkCjsPlugin (onResolve) that redirects this module
// specifier to client.cjs — a fully self-contained CJS bundle (~2300 lines, no chunk-*.js
// imports). The plugin runs at bundle time before the ESM splitter is invoked.
//
// HOW TO DETECT THIS FOR FUTURE DEPS
// ------------------------------------
// If a new dependency causes the SyntaxError above in release builds, check whether its
// ESM entry point is a barrel that re-imports from internal `chunk-*.js` files:
//
//   head -5 node_modules/<pkg>/dist/index.js
//     → imports { ... } from "./chunk-XYZ123.js"  ← pre-split ESM
//
// If so, add a matching onResolve redirect to the CJS counterpart in build.ts.

export { WarpGrepClient } from "@morphllm/morphsdk/tools/warp-grep/client"
