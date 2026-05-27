#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <version> [--skip-verification]" >&2
  echo "Example: $0 7.0.1-rc.1" >&2
  echo "Example: $0 v7.0.1-rc.1 --skip-verification" >&2
  echo "Builds/signs the current checkout without creating or validating a git tag." >&2
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

raw=""
skip_verification=0

for arg in "$@"; do
  case "$arg" in
    --skip-verification)
      skip_verification=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -n "$raw" ]]; then
        echo "Unexpected argument: $arg" >&2
        usage
        exit 1
      fi
      raw="$arg"
      ;;
  esac
done

if [[ -z "$raw" ]]; then
  usage
  exit 1
fi

version="${raw#v}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-rc\.[0-9]+$ ]]; then
  echo "Unsupported version '$raw'. Expected x.y.z-rc.n, for example 7.0.1-rc.1." >&2
  exit 1
fi

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(git -C "$script" rev-parse --show-toplevel)"
plugin="$(cd "${script}/.." && pwd)"
secrets="${root}/.secrets"
chain="${secrets}/chain.crt"
key="${secrets}/private.pem"
encrypted_key="${secrets}/private_encrypted.pem"
pass="${secrets}/JETBRAINS_PRIVATE_KEY_PASSWORD"

if [[ ! -d "$plugin" ]]; then
  echo "Expected JetBrains plugin package at $plugin" >&2
  exit 1
fi

for file in "$chain" "$key" "$pass"; do
  if [[ ! -s "$file" ]]; then
    echo "Missing required secret file: $file" >&2
    exit 1
  fi
  chmod go-rwx "$file" 2>/dev/null || true
done

if [[ -f "$encrypted_key" ]]; then
  chmod go-rwx "$encrypted_key" 2>/dev/null || true
fi

export JETBRAINS_CERTIFICATE_CHAIN_FILE="$chain"
export JETBRAINS_PRIVATE_KEY_FILE="$key"
export JETBRAINS_PRIVATE_KEY_PASSWORD="$(<"$pass")"

cd "$plugin"

./gradlew clean
KILO_VERSION="$version" KILO_CHANNEL=rc bun script/build.ts --production --prepare-cli
./gradlew buildPlugin -Pproduction=true -Pkilo.version="$version" -Pkilo.channel=eap
./gradlew signPlugin -Pproduction=true -Pkilo.version="$version" -Pkilo.channel=eap

if [[ "$skip_verification" == "1" ]]; then
  printf '\nSkipping JetBrains plugin verification.\n'
else
  ./gradlew verifyPluginSignature -Pproduction=true -Pkilo.version="$version" -Pkilo.channel=eap
  ./gradlew verifyPlugin -Pproduction=true -Pkilo.version="$version" -Pkilo.channel=eap
fi

printf '\nSigned JetBrains plugin ZIP:\n'
ls -lh build/distributions/*-signed.zip
