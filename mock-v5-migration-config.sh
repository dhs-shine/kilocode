#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: ./mock-v5-migration-config.sh seed|clean

Creates or removes local legacy migration data for JetBrains plugin migration testing.
This script does not touch VS Code storage.

Environment overrides:
  KILO_CONFIG_DIR  Config directory to manage. Default: ./.kilo-dev/config/kilo
USAGE
}

cmd="${1:-}"
if [[ -z "$cmd" ]]; then
  usage
  exit 2
fi
shift || true

if [[ $# -gt 0 ]]; then
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
fi

repo="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
dir="${KILO_CONFIG_DIR:-$repo/.kilo-dev/config/kilo}"
config="$dir/kilo.json"
legacy="$dir/legacy-settings.json"

seed() {
  mkdir -p "$dir"

  cat > "$legacy" <<'JSON'
{
  "providerProfiles": "{\"currentApiConfigName\":\"mock-openai\",\"apiConfigs\":{\"mock-openai\":{\"apiProvider\":\"openai-native\",\"openAiNativeApiKey\":\"sk-mock-v5-key\",\"openAiNativeBaseUrl\":\"https://mock.local/v1\",\"apiModelId\":\"gpt-4o-mini\"}}}",
  "mcpSettings": "{\"mcpServers\":{\"mock-filesystem\":{\"command\":\"node\",\"args\":[\"mock-mcp-server.js\"],\"env\":{\"MOCK\":\"1\"},\"disabled\":false}}}",
  "customModes": "{\"customModes\":[{\"slug\":\"mock-v5-agent\",\"name\":\"Mock V5 Agent\",\"roleDefinition\":\"You are a mock legacy v5 agent used for migration testing.\",\"groups\":[\"read\",\"edit\",\"browser\",\"command\",\"mcp\"]}]}",
  "globalState": {
    "kilo-code.autoApprovalEnabled": true,
    "kilo-code.allowedCommands": ["npm test", "bun test"],
    "kilo-code.deniedCommands": ["rm -rf *"],
    "alwaysAllowReadOnly": true,
    "alwaysAllowWrite": false,
    "alwaysAllowExecute": false,
    "alwaysAllowMcp": true,
    "alwaysAllowModeSwitch": true,
    "alwaysAllowSubtasks": true,
    "kilo-code.language": "en",
    "ghostServiceSettings": {
      "enableAutoTrigger": true,
      "enableSmartInlineTaskKeybinding": true,
      "enableChatAutocomplete": true
    }
  },
  "taskHistory": "[{\"id\":\"mock-task-1\",\"task\":\"Mock migrated task\",\"workspace\":\"/tmp/mock-v5-workspace\",\"ts\":1700000000000}]",
  "conversations": {
    "mock-task-1": "[{\"role\":\"user\",\"content\":\"Create a mock migration task\",\"ts\":1700000000000},{\"role\":\"assistant\",\"content\":\"Mock task response from legacy config.\",\"ts\":1700000001000}]"
  }
}
JSON

  rm -f "$config"
  rm -f "$dir/opencode.json"
  echo "Seeded mock legacy migration file: $legacy"
  echo "JetBrains dev isolation should pick this up via XDG_CONFIG_HOME=$repo/.kilo-dev/config"
}

clean() {
  rm -rf "$dir"
  echo "Cleaned mock config directory: $dir"
}

case "$cmd" in
  seed) seed ;;
  clean) clean ;;
  -h|--help)
    usage
    ;;
  *)
    echo "unknown command: $cmd" >&2
    usage
    exit 2
    ;;
esac
