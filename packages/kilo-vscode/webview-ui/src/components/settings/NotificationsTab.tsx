import { Component, createSignal, onCleanup } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Switch } from "@kilocode/kilo-ui/switch"
import { Card } from "@kilocode/kilo-ui/card"
import { useVSCode } from "../../context/vscode"
import { useLanguage } from "../../context/language"
import type { ExtensionMessage } from "../../types/messages"
import SettingsRow from "./SettingsRow"

const NotificationsTab: Component = () => {
  const vscode = useVSCode()
  const language = useLanguage()
  const [enabled, setEnabled] = createSignal(false)

  const unsubscribe = vscode.onMessage((message: ExtensionMessage) => {
    if (message.type !== "notificationSettingsLoaded") return
    setEnabled(message.settings.attentionEnabled)
  })

  onCleanup(unsubscribe)
  vscode.postMessage({ type: "requestNotificationSettings" })

  return (
    <Card>
      <SettingsRow
        title={language.t("settings.notifications.enable.title")}
        description={language.t("settings.notifications.enable.description")}
        last
      >
        <div style={{ display: "flex", gap: "8px", "align-items": "center" }}>
          <Switch
            checked={enabled()}
            onChange={(checked) => {
              setEnabled(checked)
              vscode.postMessage({ type: "updateSetting", key: "attention.enabled", value: checked })
            }}
            hideLabel
          >
            {language.t("settings.notifications.enable.title")}
          </Switch>
          <Button
            variant="ghost"
            size="small"
            disabled={!enabled()}
            onClick={() => vscode.postMessage({ type: "testNotification" })}
          >
            {language.t("settings.notifications.testSound")}
          </Button>
        </div>
      </SettingsRow>
    </Card>
  )
}

export default NotificationsTab
