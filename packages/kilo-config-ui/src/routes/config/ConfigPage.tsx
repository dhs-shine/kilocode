import type { JSX } from "solid-js"
import { Show } from "solid-js"
import { Tag } from "@kilocode/kilo-ui/tag"

export function ConfigPage(props: { title: string; actions?: JSX.Element; children?: JSX.Element }) {
  return (
    <main class="config-page">
      <section class="config-page-header">
        <h1>{props.title}</h1>
        <Show when={props.actions}>{(actions) => <div class="config-page-actions">{actions()}</div>}</Show>
      </section>
      {props.children}
    </main>
  )
}

export function ConfigToolbar(props: {
  title?: string
  description?: string
  meta?: JSX.Element
  children?: JSX.Element
}) {
  return (
    <Show when={props.children || props.meta}>
      <section class="config-toolbar">
        <Show when={props.children}>{(children) => <div class="config-toolbar-controls">{children()}</div>}</Show>
        <Show when={props.meta}>{(meta) => <div class="config-toolbar-meta">{meta()}</div>}</Show>
      </section>
    </Show>
  )
}

export function SourceBadge(props: { source?: string; inherited?: boolean; overridden?: boolean }) {
  const label = () => {
    if (props.overridden) return "local override"
    if (props.inherited) return "inherited"
    return props.source ?? "default"
  }

  return <Tag>{label()}</Tag>
}
