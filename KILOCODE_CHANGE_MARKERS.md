# `kilocode_change` Marker Audit — PR #10507 (OpenCode v1.14.41 merge)

## Methodology

1. Pulled the PR file list and full diff via `gh pr diff 10507 --repo Kilo-Org/kilocode`.
2. Counted **133 changed files** in the PR.
3. Searched the diff for any line beginning with `-` (i.e. removed) that contains the string `kilocode_change` to find every removed marker.
4. For each removal, inspected surrounding context to determine whether:
   - the marker was preserved on a corresponding `+` line (rewording / reformatting),
   - the marker was moved (block boundaries shifted to enclose only the original Kilo content),
   - the marker plus its underlying code was deleted intentionally, or
   - the marker was dropped accidentally (orphaned start/end, lost annotation).
5. Cross-checked any suspicious cases against the current state of the file in the working tree.

The vast majority of `kilocode_change` removals in the diff are benign: they are paired with re-added `+` markers (e.g. enriched comments, single-line collapses of start/end blocks, marker-position shifts to keep new upstream code outside the Kilo block). Below are the only cases that need attention.

## Findings

### 🟥 1. Orphaned `kilocode_change start` in `local.tsx` (likely accidental)

**File:** `packages/opencode/src/cli/cmd/tui/context/local.tsx`

Two markers were removed around the trailing `createEffect(...)` (PR diff lines 4402 and 4416):

- `// kilocode_change - validate configured agent model when agent changes` (single-line outer annotation, line 4402 of the diff)
- `// kilocode_change end` (line 4416 of the diff)

The inner `// kilocode_change start - configured models resolve directly without persistence` at line 468 of the new file **was kept**, but its matching `end` is gone. Verified with:

```
grep -c "kilocode_change start" packages/opencode/src/cli/cmd/tui/context/local.tsx → 8
grep -c "kilocode_change end"   packages/opencode/src/cli/cmd/tui/context/local.tsx → 7
```

The Kilo `createEffect` block (which warns about an invalid configured model and is not present in upstream) is now an unclosed Kilo block. Either:

- restore the trailing `// kilocode_change end` after the closing `})` on line 478, or
- demote the marker to a single-line `// kilocode_change` on the `createEffect(() => {` line.

This is the only marker imbalance introduced by the PR and almost certainly an accidental drop during conflict resolution.

### 🟨 2. Deletion of Kilo-only test file `workspace-restore.test.ts` (verify intent)

**File:** `packages/opencode/test/workspace/workspace-restore.test.ts` (deleted)

The whole file was removed. It was a Kilo-specific test fixture annotated with:

> `// kilocode_change - skip these tests after upstream's Workspace refactor.`

The test was already `describe.skip(...)` and the comment explained that its fixtures relied on `spyOn(globalThis, "fetch")` / `SyncEvent.replayAll` patterns that upstream had refactored away. Removing the file is consistent with that note (it was effectively dead code awaiting a rewrite), but because this is a Kilo-only test rather than upstream code, it is worth a quick human confirmation that the follow-up tracked in the comment is being abandoned (or moved elsewhere) rather than silently dropped.

## Other removals reviewed and cleared

All remaining `-`-side `kilocode_change` lines in the diff are paired with `+`-side replacements that preserve the marker's intent. Notable benign cases:

- `provider-error.tsx`: title/description markers re-added on the new lines (PR lines 2853–2858).
- `config.ts`: `// kilocode_change start` enriched with explanatory tail text (PR line 2531).
- `session/processor.tsx`: paired `start`/`end` collapsed into a single inline `// kilocode_change - !part.ignored…` on the same statement (PR lines 7601–7604).
- `provider-transform.test.ts`: `// kilocode_change end` moved earlier in the file so newly added upstream tests are correctly outside the Kilo block (PR lines 10275 vs 10356).
- `auth.test.ts`: trailing `// kilocode_change` lifted from the `test(...)` line onto its own line (PR lines 10368–10370).
- `agent.test.ts`: `start`/`end` block boundaries reshuffled around the `arrayContaining` block, plus single-line markers on `task: false` and `openTelemetry: true` re-added verbatim (PR lines 11703–11808).

No other shared opencode files lost a `kilocode_change` annotation in this PR.
