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
  { value: "none", labelKey: "settings.notifications.sound.none" },
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

  const [agentNotify, setAgentNotify] = createSignal(true)
  const [permNotify, setPermNotify] = createSignal(true)
  const [errorNotify, setErrorNotify] = createSignal(true)
  const [playWhenFocused, setPlayWhenFocused] = createSignal(false)
  const [agentSound, setAgentSound] = createSignal("system")
  const [permSound, setPermSound] = createSignal("system")
  const [errorSound, setErrorSound] = createSignal("system")

  const unsubscribe = vscode.onMessage((message: ExtensionMessage) => {
    if (message.type !== "notificationSettingsLoaded") return
    const settings = message.settings
    setAgentNotify(settings.notifyAgent)
    setPermNotify(settings.notifyPermissions)
    setErrorNotify(settings.notifyErrors)
    setPlayWhenFocused(settings.playWhenFocused)
    setAgentSound(settings.soundAgent)
    setPermSound(settings.soundPermissions)
    setErrorSound(settings.soundErrors)
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
    value: () => string,
    set: (value: string) => void,
    key: string,
    kind: "agent" | "permissions" | "errors",
  ) => (
    <div style={{ display: "flex", gap: "8px", "align-items": "center", "flex-wrap": "wrap" }}>
      <Select
        options={SOUND_OPTIONS}
        current={SOUND_OPTIONS.find((option) => option.value === value())}
        value={(option) => option.value}
        label={(option) => language.t(option.labelKey)}
        onSelect={(option) => {
          if (!option) return
          set(option.value)
          save(key, option.value)
        }}
        variant="secondary"
        size="small"
        triggerVariant="settings"
      />
      <Button variant="ghost" size="small" onClick={() => test(kind, value())}>
        {language.t("settings.notifications.testSound")}
      </Button>
    </div>
  )

  return (
    <div>
      <Card>
        <SettingsRow
          title={language.t("settings.notifications.agent.title")}
          description={language.t("settings.notifications.agent.description")}
        >
          <Switch
            checked={agentNotify()}
            onChange={(checked) => {
              setAgentNotify(checked)
              save("notifications.agent", checked)
            }}
            hideLabel
          >
            {language.t("settings.notifications.agent.title")}
          </Switch>
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.permissions.title")}
          description={language.t("settings.notifications.permissions.description")}
        >
          <Switch
            checked={permNotify()}
            onChange={(checked) => {
              setPermNotify(checked)
              save("notifications.permissions", checked)
            }}
            hideLabel
          >
            {language.t("settings.notifications.permissions.title")}
          </Switch>
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.errors.title")}
          description={language.t("settings.notifications.errors.description")}
          last
        >
          <Switch
            checked={errorNotify()}
            onChange={(checked) => {
              setErrorNotify(checked)
              save("notifications.errors", checked)
            }}
            hideLabel
          >
            {language.t("settings.notifications.errors.title")}
          </Switch>
        </SettingsRow>
      </Card>

      <h4 style={{ "margin-top": "16px", "margin-bottom": "8px" }}>{language.t("settings.notifications.sounds")}</h4>
      <Card>
        <SettingsRow
          title={language.t("settings.notifications.playWhenFocused.title")}
          description={language.t("settings.notifications.playWhenFocused.description")}
        >
          <Switch
            checked={playWhenFocused()}
            onChange={(checked) => {
              setPlayWhenFocused(checked)
              save("sounds.playWhenFocused", checked)
            }}
            hideLabel
          >
            {language.t("settings.notifications.playWhenFocused.title")}
          </Switch>
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.agentSound.title")}
          description={language.t("settings.notifications.agentSound.description")}
        >
          {picker(agentSound, setAgentSound, "sounds.agent", "agent")}
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.permSound.title")}
          description={language.t("settings.notifications.permSound.description")}
        >
          {picker(permSound, setPermSound, "sounds.permissions", "permissions")}
        </SettingsRow>
        <SettingsRow
          title={language.t("settings.notifications.errorSound.title")}
          description={language.t("settings.notifications.errorSound.description")}
          last
        >
          {picker(errorSound, setErrorSound, "sounds.errors", "errors")}
        </SettingsRow>
      </Card>
    </div>
  )
}

export default NotificationsTab
