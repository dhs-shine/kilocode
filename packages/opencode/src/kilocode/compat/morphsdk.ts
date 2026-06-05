// kilocode_change - new file
// Re-exports from @morphllm/morphsdk/tools/warp-grep/client.
//
// @morphllm/morphsdk ships a pre-split ESM distribution for this path (client.js, an 805-byte
// barrel that re-imports from ~52 chunk-*.js files). To keep the bundle simple, script/build.ts
// adds a morphsdkCjsPlugin (onResolve) that redirects this specifier to client.cjs — a fully
// self-contained CJS bundle with no chunk-*.js side-imports.
//
// NOTE: this redirect is not what fixes the release build. The startup crash
//   SyntaxError: Exported binding 'G9' needs to refer to a top-level declared variable
// is a Bun 1.3.14 code-splitting bug (oven-sh/bun#25621); the fix is splitting:false in
// script/build.ts, not this re-export.

export { WarpGrepClient } from "@morphllm/morphsdk/tools/warp-grep/client"
