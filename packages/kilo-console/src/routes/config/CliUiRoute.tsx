import { Button } from "@kilocode/kilo-web-ui/button"
import { Tag } from "@kilocode/kilo-web-ui/tag"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"
import { useTuiUiSettings } from "./state/ui"

export function CliUiRoute() {
  const state = useTuiUiSettings()

  return (
    <ConfigPage title="CLI UI" actions={<Tag>{state.current() || "default"}</Tag>}>
      <ConfigToolbar title="Theme" description="Configure terminal UI preferences that belong with CLI settings.">
        <label>
          <span>Theme id</span>
          <input
            value={state.theme()}
            placeholder="Theme id"
            onInput={(event) => state.setTheme(event.currentTarget.value)}
          />
        </label>
        <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.save}>
          Save Theme
        </Button>
      </ConfigToolbar>
    </ConfigPage>
  )
}
