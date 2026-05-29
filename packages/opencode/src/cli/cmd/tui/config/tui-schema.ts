import z from "zod"
import { ConfigPlugin } from "@/config/plugin"
import { ConfigKeybinds } from "@/config/keybinds"

const KeybindOverride = z
  .object(
    Object.fromEntries(Object.keys(ConfigKeybinds.Keybinds.shape).map((key) => [key, z.string().optional()])) as Record<
      string,
      z.ZodOptional<z.ZodString>
    >,
  )
  .strict()

export const TuiOptions = z.object({
  scroll_speed: z.number().min(0.001).optional().describe("TUI scroll speed"),
  scroll_acceleration: z
    .object({
      enabled: z.boolean().describe("Enable scroll acceleration"),
    })
    .optional()
    .describe("Scroll acceleration settings"),
  diff_style: z
    .enum(["auto", "stacked"])
    .optional()
    .describe("Control diff rendering style: 'auto' adapts to terminal width, 'stacked' always shows single column"),
  mouse: z.boolean().optional().describe("Enable or disable mouse capture (default: true)"),
  // kilocode_change start - support documented TUI attention config in Console
  attention: z
    .object({
      enabled: z.boolean().optional().describe("Enable TUI attention alerts"),
      notifications: z.boolean().optional().describe("Enable desktop notifications for TUI attention alerts"),
      sound: z.boolean().optional().describe("Enable sound for TUI attention alerts"),
      volume: z.number().min(0).max(1).optional().describe("TUI attention sound volume"),
    })
    .optional()
    .describe("TUI attention settings"),
  // kilocode_change end
})

export const TuiInfo = z
  .object({
    $schema: z.string().optional(),
    theme: z.string().optional(),
    keybinds: KeybindOverride.optional(),
    plugin: ConfigPlugin.Spec.zod.array().optional(),
    plugin_enabled: z.record(z.string(), z.boolean()).optional(),
  })
  .extend(TuiOptions.shape)
  .strict()
