export namespace TestProfile {
  // Broad globs keep platform coverage maintainable as tests are added or renamed.
  // Full Linux and Windows runs remain the backstop for platform-neutral behavior.
  const profiles = {
    darwin: {
      description: "Darwin-native process, terminal, filesystem, worktree, and runtime coverage",
      groups: {
        cli: [
          "cli/acp/lifecycle.test.ts",
          "cli/run/{footer.view,run-process,runtime.stdin,scrollback.surface}.test.{ts,tsx}",
          "cli/serve/*.test.ts",
          "cli/smokes/*.test.ts",
          "cli/tui/{app-lifecycle,dialog-prompt,diff-viewer-file-tree,diff-viewer,inline-tool-wrap-snapshot,keymap,plugin-lifecycle,plugin-loader-entrypoint,slot-replace,thread,use-event}.test.{ts,tsx}",
        ],
        config: [
          "config/{config,tui}.test.ts",
          "control-plane/workspace.test.ts",
        ],
        filesystem: [
          "file/{index,path-traversal,ripgrep,watcher}.test.ts",
          "filesystem/filesystem.test.ts",
          "fixture/fixture.test.ts",
          "git/*.test.ts",
          "image/*.test.ts",
          "plugin/{install-concurrency,loader-shared}.test.ts",
          "reference/*.test.ts",
          "snapshot/*.test.ts",
          "tool/{apply_patch,edit,external-directory,glob,grep,read,recall,registry,repo_clone,repo_overview,shell,skill,truncation,write}.test.ts",
          "util/{filesystem,glob,module,process,which,wildcard}.test.ts",
        ],
        kilo: [
          "kilocode/{background-process,bin-tree-sitter-env,command-timeout,daemon,diff-full,external-directory-boundary,indexing-worker,indexing-worktree,interactive-terminal,lancedb-runtime,logo,mcp-oauth-callback,primary-worktree,project-id,pty-self-command,read-directory,session-diff-restore,snapshot-cache,snapshot-freeze-repro,snapshot-revert-move,snapshot-seed,task-nesting,terminal,terminal-title,test-profile,tui-terminal-title-reactivity,vt-screen}.test.ts",
          "kilocode/anaconda-desktop/domain.test.ts",
          "kilocode/cli/cmd/run/interactive-terminal.test.ts",
          "kilocode/cli/cmd/serve.test.ts",
          "kilocode/cli/cmd/tui/context/tui-config.test.ts",
          "kilocode/cli/install-artifact.test.ts",
          "kilocode/commit-message/git-context.test.ts",
          "kilocode/config/config.test.ts",
          "kilocode/permission/external-directory-allow.test.ts",
          "kilocode/sandbox/*.test.ts",
          "kilocode/server/{config-overlay,listener-runtime,tui-config,worktree-list}.test.ts",
          "kilocode/session-export/{e2e,sequence,worker,workspace-provider}.test.ts",
          "kilocode/session-export/worker/{storage,zstd}.test.ts",
          "kilocode/worktree*.test.ts",
        ],
        process: ["mcp/lifecycle.test.ts", "session/prompt.test.ts", "shell/*.test.ts"],
        project: ["project/*.test.ts"],
        pty: ["pty/pty-*.test.ts", "server/httpapi-pty*.test.ts"],
        server: [
          "server/{experimental-session-list,httpapi-experimental,httpapi-file,httpapi-listen,httpapi-workspace-routing,project-init-git,worktree-endpoint-repro}.test.ts",
        ],
      },
    },
  } as const

  export const names = Object.keys(profiles)

  export function resolve(name: string, all: readonly string[]) {
    const files = all.map((file) => file.replaceAll("\\", "/"))
    const profile = profiles[name as keyof typeof profiles]
    if (!profile) {
      return {
        ok: false as const,
        error: `Unknown test profile "${name}". Available profiles: ${names.join(", ")}`,
      }
    }

    const groups = Object.entries(profile.groups)
    const patterns = groups.flatMap(([, patterns]) => patterns)
    const malformed = patterns.filter(
      (pattern) =>
        pattern.startsWith("/") ||
        pattern.startsWith("test/") ||
        pattern.includes("\\") ||
        pattern.split("/").includes("..") ||
        !/\.test\.(ts|tsx|\{ts,tsx\})$/.test(pattern),
    )
    const seen = new Set<string>()
    const duplicates = patterns.filter((pattern) => {
      if (seen.has(pattern)) return true
      seen.add(pattern)
      return false
    })
    const unsorted = groups
      .filter(([, patterns]) =>
        patterns.some((pattern, index) => index > 0 && patterns[index - 1].localeCompare(pattern) > 0),
      )
      .map(([group]) => group)
    const globs = patterns.map((pattern) => ({ pattern, glob: new Bun.Glob(pattern) }))
    const unmatched = globs.filter((item) => !files.some((file) => item.glob.match(file))).map((item) => item.pattern)
    const errors = [
      malformed.length > 0 ? `Malformed patterns: ${malformed.join(", ")}` : "",
      duplicates.length > 0 ? `Duplicate patterns: ${duplicates.join(", ")}` : "",
      unmatched.length > 0 ? `Unmatched patterns: ${unmatched.join(", ")}` : "",
      unsorted.length > 0 ? `Unsorted groups: ${unsorted.join(", ")}` : "",
      patterns.length === 0 ? "Profile contains no patterns" : "",
    ].filter(Boolean)

    if (errors.length > 0) {
      return {
        ok: false as const,
        error: `Invalid test profile "${name}":\n${errors.map((error) => `- ${error}`).join("\n")}`,
      }
    }

    return {
      ok: true as const,
      description: profile.description,
      files: files.filter((file) => globs.some((item) => item.glob.match(file))),
    }
  }
}
