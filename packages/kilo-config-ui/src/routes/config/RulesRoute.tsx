import { createEffect, createMemo, createSignal, For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Card } from "@kilocode/kilo-ui/card"
import { Tag } from "@kilocode/kilo-ui/tag"
import { useConfig } from "../../context/config"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"

export function RulesRoute() {
  const ctx = useConfig()
  const [text, setText] = createSignal("")
  const [dirty, setDirty] = createSignal(false)
  const rules = createMemo(() => ctx.data()?.rules)
  const target = createMemo(() => rules()?.files.find((file) => file.name === "AGENTS.md"))

  createEffect(() => {
    if (dirty()) return
    setText(target()?.content ?? "")
  })

  function update(value: string) {
    setDirty(true)
    setText(value)
  }

  function save() {
    ctx.rules(text())
    setDirty(false)
  }

  return (
    <ConfigPage title="Project Rules" actions={<Tag>{rules()?.files.filter((file) => file.exists).length ?? 0}</Tag>}>
      <ConfigToolbar
        title="AGENTS.md"
        description="Project rules are stored in the repository and shared with every agent in this workspace."
        meta={<Tag>{target()?.exists ? "local" : "new file"}</Tag>}
      />

      <Show
        when={ctx.query()?.scope === "project"}
        fallback={<Card class="empty">Rules are available only in project settings.</Card>}
      >
        <section class="rules-editor">
          <label>
            <span>Editable rules file</span>
            <textarea value={text()} spellcheck={false} onInput={(event) => update(event.currentTarget.value)} />
          </label>
          <div class="builder-actions">
            <Button variant="primary" disabled={Boolean(ctx.saving())} onClick={save}>
              Save Rules
            </Button>
          </div>
        </section>

        <div class="mini-list">
          <For each={rules()?.files ?? []}>
            {(file) => (
              <article class="mini-item simple" classList={{ inherited: !file.editable }}>
                <strong>{file.name}</strong>
                <span>{file.path}</span>
                <div class="tags">
                  <Tag>{file.exists ? "present" : "missing"}</Tag>
                  <Tag>{file.editable ? "editable" : "read only"}</Tag>
                </div>
              </article>
            )}
          </For>
        </div>
      </Show>
    </ConfigPage>
  )
}
