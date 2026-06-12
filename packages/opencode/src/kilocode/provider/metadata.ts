import { iconNames, type IconName } from "@opencode-ai/ui/icons/provider"

export type ProviderMetadata = {
  noteKey?: string
  note?: string
  icon?: string
}

const notes: Record<string, { key: string; note: string }> = {
  kilo: { key: "settings.providers.note.kilo", note: "Access 500+ AI models" },
  opencode: {
    key: "settings.providers.note.opencode",
    note: "Curated models including Claude, GPT, Gemini and more",
  },
  anthropic: {
    key: "settings.providers.note.anthropic",
    note: "Direct access to Claude models, including Pro and Max",
  },
  deepseek: {
    key: "settings.providers.note.deepseek",
    note: "DeepSeek models for reasoning and coding tasks",
  },
  "github-copilot": { key: "settings.providers.note.copilot", note: "Claude models for coding assistance" },
  openai: {
    key: "settings.providers.note.openai",
    note: "GPT and Codex models with API key or ChatGPT login",
  },
  google: { key: "settings.providers.note.google", note: "Gemini models for fast, structured responses" },
  openrouter: { key: "settings.providers.note.openrouter", note: "Access all supported models from one provider" },
  vercel: { key: "settings.providers.note.vercel", note: "Unified access to AI models with smart routing" },
}

const icons = new Set<string>(iconNames)

function key(id: string) {
  if (id.startsWith("github-copilot")) return "github-copilot"
  return id
}

export function providerMetadata(id: string): ProviderMetadata {
  const name = key(id)
  const note = notes[name]
  return {
    noteKey: note?.key,
    note: note?.note,
    icon: icons.has(name as IconName) ? name : "synthetic",
  }
}
