import * as fs from "fs"
import * as path from "path"
import type { TuiAttentionSoundName } from "@kilocode/plugin/tui"
import { exec } from "../../util/process"

const files: Record<TuiAttentionSoundName, string> = {
  default: "bip-bop-01",
  question: "bip-bop-03",
  permission: "staplebops-06",
  error: "nope-03",
  done: "bip-bop-01",
  subagent_done: "yup-01",
}

const root = path.join(__dirname, "../audio-wav")
let chain = Promise.resolve(false)
let queued = 0
const limit = 3

async function run(commands: Array<{ cmd: string; args: string[]; env?: NodeJS.ProcessEnv }>) {
  for (const command of commands) {
    const ok = await exec(command.cmd, command.args, command.env ? { env: command.env } : {}).then(
      () => true,
      (error) => {
        console.debug("[Kilo New] notification sound command failed", { cmd: command.cmd, error })
        return false
      },
    )
    if (ok) return true
  }
  return false
}

function commands(file: string): Array<{ cmd: string; args: string[]; env?: NodeJS.ProcessEnv }> {
  if (process.platform === "darwin") {
    return [
      { cmd: "afplay", args: [file] },
      { cmd: "play", args: [file] },
    ]
  }
  if (process.platform === "linux") {
    return [
      { cmd: "aplay", args: [file] },
      { cmd: "paplay", args: [file] },
      { cmd: "play", args: [file] },
    ]
  }
  if (process.platform === "win32") {
    return [
      {
        cmd: "powershell",
        args: [
          "-NoProfile",
          "-NonInteractive",
          "-Command",
          "$sound = [System.Media.SoundPlayer]::new($env:KILO_SOUND_PATH); $sound.PlaySync(); $sound.Dispose()",
        ],
        env: { ...process.env, KILO_SOUND_PATH: file },
      },
    ]
  }
  return []
}

async function perform(name: TuiAttentionSoundName, dir: string) {
  const file = path.resolve(dir, `${files[name]}.wav`)
  if (!file.startsWith(`${path.resolve(dir)}${path.sep}`)) return false
  if (!fs.existsSync(file)) {
    console.warn("[Kilo New] notification sound is missing", { file })
    return false
  }
  const ok = await run(commands(file))
  if (ok) console.debug("[Kilo New] notification sound played", { name })
  return ok
}

export async function playSound(name: TuiAttentionSoundName, dir = root) {
  if (queued >= limit) return false
  queued += 1
  const task = chain.catch(() => false).then(() => perform(name, dir))
  chain = task.finally(() => {
    queued -= 1
  })
  return task
}
