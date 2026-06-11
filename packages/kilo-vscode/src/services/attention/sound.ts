import * as fs from "fs"
import * as path from "path"
import { exec } from "../../util/process"
import type { AttentionKind } from "./attention"

export const CustomSoundIDs = [
  "alert-01",
  "alert-02",
  "alert-03",
  "alert-04",
  "alert-05",
  "alert-06",
  "alert-07",
  "alert-08",
  "alert-09",
  "alert-10",
  "bip-bop-01",
  "bip-bop-02",
  "bip-bop-03",
  "bip-bop-04",
  "bip-bop-05",
  "bip-bop-06",
  "bip-bop-07",
  "bip-bop-08",
  "bip-bop-09",
  "bip-bop-10",
  "staplebops-01",
  "staplebops-02",
  "staplebops-03",
  "staplebops-04",
  "staplebops-05",
  "staplebops-06",
  "staplebops-07",
  "nope-01",
  "nope-02",
  "nope-03",
  "nope-04",
  "nope-05",
  "nope-06",
  "nope-07",
  "nope-08",
  "nope-09",
  "nope-10",
  "nope-11",
  "nope-12",
  "yup-01",
  "yup-02",
  "yup-03",
  "yup-04",
  "yup-05",
  "yup-06",
] as const

export type CustomSoundID = (typeof CustomSoundIDs)[number]
export type SoundID = "system" | CustomSoundID

const ids = new Set<string>(CustomSoundIDs)
const root = path.join(__dirname, "../audio-wav")
const defaults: Record<AttentionKind, CustomSoundID> = {
  done: "bip-bop-01",
  subagent_done: "yup-01",
  question: "bip-bop-03",
  permission: "staplebops-06",
  error: "nope-03",
}

let chain = Promise.resolve(false)
let queued = 0
const limit = 3

export function resolveSoundID(value: string | undefined, kind: AttentionKind): SoundID | undefined {
  if (!value || value === "none") return
  if (value === "default") return defaults[kind]
  if (value === "system") return "system"
  if (ids.has(value)) return value as CustomSoundID
}

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

function systemCommands(): Array<{ cmd: string; args: string[] }> {
  if (process.platform === "darwin") return [{ cmd: "osascript", args: ["-e", "beep"] }]
  if (process.platform === "linux") {
    return [
      { cmd: "canberra-gtk-play", args: ["-i", "message-new-instant"] },
      { cmd: "paplay", args: ["/usr/share/sounds/freedesktop/stereo/message.oga"] },
    ]
  }
  if (process.platform === "win32") {
    return [
      {
        cmd: "powershell",
        args: ["-NoProfile", "-NonInteractive", "-Command", "[System.Media.SystemSounds]::Exclamation.Play()"],
      },
    ]
  }
  return []
}

function fileCommands(file: string): Array<{ cmd: string; args: string[]; env?: NodeJS.ProcessEnv }> {
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

async function perform(id: SoundID, dir: string) {
  const play = async () => {
    if (id === "system") return run(systemCommands())
    const file = path.resolve(dir, `${id}.wav`)
    if (!file.startsWith(`${path.resolve(dir)}${path.sep}`)) return false
    if (!fs.existsSync(file)) {
      console.warn("[Kilo New] notification sound is missing", { file })
      return false
    }
    return run(fileCommands(file))
  }
  const ok = await play()
  if (ok) console.debug("[Kilo New] notification sound played", { id })
  return ok
}

export async function playSound(id: SoundID, dir = root) {
  if (queued >= limit) return false
  queued += 1
  const task = chain.catch(() => false).then(() => perform(id, dir))
  chain = task.finally(() => {
    queued -= 1
  })
  return task
}
