import { FileDiff, type FileDiffOptions } from "@pierre/diffs"
import type { WorkerPoolManager } from "@pierre/diffs/worker"
import { createEffect, createMemo, onCleanup, splitProps, type ComponentProps, type JSX } from "solid-js"
import { File as BaseFile } from "@opencode-ai/ui/file"
import { createDefaultOptions, styleVariables } from "@kilocode/kilo-ui/pierre"
import { getWorkerPool } from "../pierre/worker"

const VirtualFile = BaseFile as unknown as (props: Record<string, unknown>) => JSX.Element

type File = {
  name: string
  contents: unknown
}

export type DiffProps<T = {}> = Omit<FileDiffOptions<T>, "diffStyle"> & {
  fileDiff?: unknown
  before?: File
  after?: File
  patch?: string
  diffStyle?: FileDiffOptions<T>["diffStyle"]
  annotations?: unknown[]
  onRendered?: () => void
  virtualized?: boolean
  class?: string
  classList?: ComponentProps<"div">["classList"]
}

function value(file: File | undefined) {
  if (typeof file?.contents === "string") return file.contents
  return ""
}

function scheme(container: HTMLDivElement) {
  const host = container.querySelector("diffs-container")
  if (!(host instanceof HTMLElement)) return

  const color = document.documentElement.dataset.colorScheme
  if (color === "dark" || color === "light") {
    host.dataset.colorScheme = color
    return
  }

  host.removeAttribute("data-color-scheme")
}

function EagerDiff<T>(props: DiffProps<T>) {
  let container!: HTMLDivElement
  let instance: FileDiff<T> | undefined

  const [local, rest] = splitProps(props, [
    "fileDiff",
    "before",
    "after",
    "patch",
    "diffStyle",
    "class",
    "classList",
    "annotations",
    "onRendered",
    "virtualized",
  ])

  const options = createMemo(
    () =>
      ({
        ...createDefaultOptions<T>(local.diffStyle),
        ...rest,
      }) as unknown as FileDiffOptions<T>,
  )

  createEffect(() => {
    const annotations = local.annotations ?? []
    instance?.cleanUp()
    const worker = getWorkerPool(local.diffStyle) as unknown as WorkerPoolManager | undefined
    instance = new FileDiff<T>(options(), worker)
    container.innerHTML = ""

    if (local.fileDiff) {
      instance.render({
        fileDiff: local.fileDiff,
        lineAnnotations: annotations,
        containerWrapper: container,
      } as unknown as Parameters<FileDiff<T>["render"]>[0])
    }

    if (!local.fileDiff && local.before && local.after) {
      instance.render({
        oldFile: { ...local.before, contents: value(local.before) },
        newFile: { ...local.after, contents: value(local.after) },
        lineAnnotations: annotations,
        containerWrapper: container,
      } as unknown as Parameters<FileDiff<T>["render"]>[0])
    }

    scheme(container)
    local.onRendered?.()
  })

  createEffect(() => {
    if (!instance) return
    instance.setLineAnnotations((local.annotations ?? []) as Parameters<FileDiff<T>["setLineAnnotations"]>[0])
    instance.rerender()
  })

  onCleanup(() => {
    instance?.cleanUp()
    instance = undefined
  })

  return (
    <div
      data-component="file"
      data-mode="diff"
      style={styleVariables}
      class={local.class}
      classList={local.classList}
      ref={container}
    />
  )
}

export function Diff<T>(props: DiffProps<T>) {
  if (props.virtualized !== false) {
    const next = props as unknown as Record<string, unknown>
    return <VirtualFile {...next} mode="diff" />
  }

  return <EagerDiff {...props} />
}
