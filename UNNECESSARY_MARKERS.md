# Unnecessary `kilocode_change` Markers — PR #10507 (v1.14.41)

Last merged upstream: **v1.14.41** (`8ba2a917`)
Mode: dry-run, no writes performed.

## Findings

One file carries `kilocode_change` markers but is otherwise identical to *transformed* upstream — the marker is redundant because the rewrite is handled automatically by `script/upstream/transforms/transform-extensions.ts` (which converts `anomalyco/opencode` → `Kilo-Org/kilocode`).

| Bucket | File | Notes |
|---|---|---|
| markers-only | `packages/opencode/src/cli/cmd/tui/component/error-component.tsx` | Line 34 marks the bug-report URL rewrite, but the URL is auto-transformed during upstream merges, so the marker has no real diff behind it. Touched in this PR by commit `3185e8d58` (rebrand upstream attribution and bug-report URLs). |

`reset-to-upstream.ts --dry-run` confirms it would safely reset:

```
[INFO] [DRY-RUN] Would reset packages/opencode/src/cli/cmd/tui/component/error-component.tsx to transformed upstream v1.14.41
```

## `find-reset-candidates.ts --dry-run` — Summary

```
- Last merged upstream: v1.14.41 (8ba2a917)
- Scope: (all shared paths)
- Review limit: 5 non-marker diff line(s)
- Mode: dry-run (no writes)
- Total candidates: 692
- Non-code assets skipped: 324
- Config-protected files skipped: 1523

| Bucket            | Count | Action         |
|-------------------|-------|----------------|
| markers-only      | 1     | would reset    |
| cosmetic-only     | 1     | would reset    |
| small-diff        | 141   | would reset    |
| large-diff        | 269   | skipped        |
| identical         | 118   | nothing to do  |
| upstream-missing  | 159   | skipped        |
| local-missing     | 3     | skipped        |
| non-code-asset    | 324   | skipped        |
| config-protected  | 1523  | skipped        |
```

`cosmetic-only`: `packages/opencode/src/session/prompt/anthropic.txt` — whitespace-only drift, not marker-related.

`small-diff` (141 files) contains real diffs (markers + ≤5 non-marker lines); not strictly "marker-only" candidates and out of scope for this audit.

## Recommendation

Drop the `// kilocode_change` marker on `error-component.tsx:34` (or run `reset-to-upstream.ts` against the file) — the transform pipeline already rewrites the URL on every merge, so the marker is misleading.
