import { Component, createSignal, onCleanup } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Switch } from "@kilocode/kilo-ui/switch"
import { Select } from "@kilocode/kilo-ui/select"
import { Card } from "@kilocode/kilo-ui/card"
import { useVSCode } from "../../context/vscode"
import { useLanguage } from "../../context/language"
import type { ExtensionMessage } from "../../types/messages"
import SettingsRow from "./SettingsRow"

interface SoundOption {
  value: string
  labelKey: string
}

const groups = [
  { value: "alert", key: "alert", count: 10 },
  { value: "bip-bop", key: "bipbop", count: 10 },
  { value: "staplebops", key: "staplebops", count: 7 },
  { value: "nope", key: "nope", count: 12 },
  { value: "yup", key: "yup", count: 6 },
]

const SOUND_OPTIONS: SoundOption[] = [
  { value: "system", labelKey: "settings.notifications.sound.system" },
  { value: "default", labelKey: "settings.notifications.sound.default" },
  ...groups.flatMap((group) =>
    Array.from({ length: group.count }, (_, index) => {
      const suffix = String(index + 1).padStart(2, "0")
      return {
        value: `${group.value}-${suffix}`,
        labelKey: `sound.option.${group.key}${suffix}`,
      }
    }),
  ),
]

const NotificationsTab: Component = () => {
  const vscode = useVSCode()
  const language = useLanguage()

  const [agentSoundEnabled, setAgentSoundEnabled] = createSignal(false)
  const [permSoundEnabled, setPermSoundEnabled] = createSignal(false)
  const [errorSoundEnabled, setErrorSoundEnabled] = createSignal(false)
  const [agentSound, setAgentSound] = createSignal("system")
  const [permSound, setPermSound] = createSignal("system")
  const [errorSound, setErrorSound] = createSignal("system")

  const unsubscribe = vscode.onMessage((message: ExtensionMessage) => {
    if (message.type !== "notificationSettingsLoaded") return
    const settings = message.settings
    setAgentSoundEnabled(settings.soundAgentEnabled)
    setPermSoundEnabled(settings.soundPermissionsEnabled)
    setErrorSoundEnabled(settings.soundErrorsEnabled)
    setAgentSound(SOUND_OPTIONS.some((option) => option.value === settings.soundAgent) ? settings.soundAgent : "system")
    setPermSound(
      SOUND_OPTIONS.some((option) => option.value === settings.soundPermissions) ? settings.soundPermissions : "system",
    )
    setErrorSound(
      SOUND_OPTIONS.some((option) => option.value === settings.soundErrors) ? settings.soundErrors : "system",
    )
  })

  onCleanup(unsubscribe)
  vscode.postMessage({ type: "requestNotificationSettings" })

  const save = (key: string, value: unknown) => {
    vscode.postMessage({ type: "updateSetting", key, value })
  }

  const test = (settingType: "agent" | "permissions" | "errors", sound: string) => {
    vscode.postMessage({ type: "testNotification", settingType, sound })
  }

  const picker = (
    enabled: () => boolean,
    setEnabled: (value: boolean) => void,
    value: () => string,
    set: (value: string) => void,
    key: string,
    kind: "agent" | "permissions" | "errors",
    label: string,
  ) => (
    <div style={{ display: "flex", gap: "8px", "align-items": "center", "flex-wrap": "wrap" }}>
      <Switch
        checked={enabled()}
        onChange={(checked) => {
          setEnabled(checked)
          save(`sounds.${key}Enabled`, checked)
          if (checked) save(`sounds.${key}`, value())
        }}
        hideLabel
      >
        {label}
      </Switch>
      <Select
        options={SOUND_OPTIONS}
        current={SOUND_OPTIONS.find((option) => option.value === value())}
        value={(option) => option.value}
        label={(option) => language.t(option.labelKey)}
        onSelect={(option) => {
          if (!option) return
          set(option.value)
          save(`sounds.${key}`, option.value)
        }}
        disabled={!enabled()}
        variant="secondary"
        size="small"
        triggerVariant="settings"
      />
      <Button variant="ghost" size="small" disabled={!enabled()} onClick={() => test(kind, value())}>
        {language.t("settings.notifications.testSound")}
      </Button>
    </div>
  )

  return (
    <div>
      <Card>
        <SettingsRow
          title={language.t("settings.notifications.agentSound.title")}
          description={language.t("settings.notifications.agentSound.description")}
        >
          {picker(
            agentSoundEnabled,
            setAgentSoundEnabled,
            agentSound,
            setAgentSound,
            "agent",
            "agent",
            language.t("settings.notifications.agentSound.title"),
          )}
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.permSound.title")}
          description={language.t("settings.notifications.permSound.description")}
        >
          {picker(
            permSoundEnabled,
            setPermSoundEnabled,
            permSound,
            setPermSound,
            "permissions",
            "permissions",
            language.t("settings.notifications.permSound.title"),
          )}
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.errorSound.title")}
          description={language.t("settings.notifications.errorSound.description")}
        >
          {picker(
            errorSoundEnabled,
            setErrorSoundEnabled,
            errorSound,
            setErrorSound,
            "errors",
            "errors",
            language.t("settings.notifications.errorSound.title"),
          )}
        </SettingsRow>
      </Card>
    </div>
  )
}

export default NotificationsTab
