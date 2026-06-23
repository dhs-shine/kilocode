import { createContext, createMemo, Show, useContext } from "solid-js"
import type { Part, Provider, StepFinishPart } from "@kilocode/sdk/v2"
import { useTheme } from "@tui/context/theme"
import * as Model from "@tui/util/model"
import { KiloRoutedModel } from "@/kilocode/session/routed-model"

export namespace RoutedModelMeta {
  type Providers = Provider[] | ReadonlyMap<string, Provider> | undefined

  export type Info = {
    labels: ReadonlyMap<string, string>
    consumed: ReadonlySet<string>
  }

  const empty: Info = {
    labels: new Map(),
    consumed: new Set(),
  }

  export const Context = createContext<() => Info>(() => empty)

  function eligible(part: Part, details: boolean) {
    if (part.type === "reasoning") return true
    if (part.type !== "tool") return false
    return details || part.state.status !== "completed"
  }

  function boundary(part: Part, details: boolean) {
    return part.type === "step-start" || part.type === "step-finish" || eligible(part, details)
  }

  export function label(list: Providers, model: StepFinishPart["model"]) {
    if (!model) return undefined
    const id = KiloRoutedModel.display(model.modelID)
    const name = Model.name(list, model.providerID, model.modelID)
    const text = name === model.modelID && id !== model.modelID ? Model.name(list, model.providerID, id) : name
    return KiloRoutedModel.displayName(text)
  }

  function finish(parts: Part[], index: number, details: boolean) {
    const part = parts.slice(index + 1).find((item) => boundary(item, details))
    if (part?.type !== "step-finish") return undefined
    return part
  }

  export function info(list: Providers, parts: Part[], details: boolean): Info {
    const entries = parts.flatMap((part, index) => {
      if (!eligible(part, details)) return []
      const item = finish(parts, index, details)
      const text = label(list, item?.model)
      if (!item || !text) return []
      return [[part.id, text, item.id] as const]
    })
    return {
      labels: new Map(entries.map((entry) => [entry[0], entry[1]] as const)),
      consumed: new Set(entries.map((entry) => entry[2])),
    }
  }

  export function View(props: { id?: string }) {
    const { theme } = useTheme()
    const info = useContext(Context)
    const text = createMemo(() => (props.id ? info().labels.get(props.id) : undefined))

    return (
      <Show when={text()}>
        <span style={{ fg: theme.textMuted }}> · {text()}</span>
      </Show>
    )
  }
}
