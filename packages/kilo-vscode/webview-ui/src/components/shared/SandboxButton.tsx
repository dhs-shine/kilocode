/**
 * SandboxButton component
 * The lock toggle used to enable/disable a session's sandbox override.
 *
 * SandboxButtonBase accepts enabled/availability/onToggle props and is consumed
 * by both the chat prompt and Agent Manager so both surfaces render one control.
 */

import { type Component } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Tooltip } from "@kilocode/kilo-ui/tooltip"
import { Icon } from "@kilocode/kilo-ui/icon"
import { useLanguage } from "../../context/language"

export interface SandboxButtonBaseProps {
  /** Whether the sandbox override is currently enabled. */
  enabled: boolean
  /** Sandbox backend availability. Undefined means unknown (e.g. pre-creation). */
  available?: boolean
  /** Reason text shown in the tooltip when unavailable. */
  reason?: string
  /** Extra disabled conditions beyond unavailability. */
  disabled?: boolean
  /** Called when the user clicks the toggle. */
  onToggle: () => void
}

export const SandboxButtonBase: Component<SandboxButtonBaseProps> = (props) => {
  const language = useLanguage()
  const unavailable = () => props.available === false
  const tooltip = () =>
    unavailable()
      ? (props.reason ?? language.t("common.requestFailed"))
      : props.enabled
        ? language.t("prompt.action.sandbox.enabled")
        : language.t("prompt.action.sandbox.disabled")

  return (
    <Tooltip value={tooltip()} placement="top">
      <Button
        variant="ghost"
        size="small"
        onClick={props.onToggle}
        disabled={props.disabled || unavailable()}
        aria-label={
          props.enabled ? language.t("prompt.action.sandbox.disable") : language.t("prompt.action.sandbox.enable")
        }
        aria-pressed={props.enabled}
        class={`prompt-status-button ${props.enabled ? "prompt-status-button--active" : ""}`}
      >
        <Icon name="lock" size="small" />
      </Button>
    </Tooltip>
  )
}
