// kilocode_change - new file
const yes = new Set(["1", "true", "yes", "on"])
const no = new Set(["0", "false", "no", "off"])

const modern = {
  tui: [
    `██  ██ ██🬺🬏   ██  ██   ██🬺🬏     ████ ██     ██🬺🬏   `,
    `████🬺🬏 ~~██   ██  ~~ ██~~██   ██~~~~ ██     ~~██   `,
    `██  ██ ██████ 🬁🬬████ 🬁🬬██~~   🬁🬬████ 🬁🬬████ ██████ `,
    `~~  ~~ ~~~~~~  ~~~~~  ~~~       ~~~~   ~~~~ ~~~~~~ `,
  ],
  plain: [
    `██  ██ ██🬺🬏   ██  ██   ██🬺🬏     ████ ██     ██🬺🬏   `,
    `████🬺🬏   ██   ██     ██  ██   ██     ██       ██   `,
    `██  ██ ██████ 🬁🬬████ 🬁🬬██     🬁🬬████ 🬁🬬████ ██████ `,
  ],
}

const fallback = {
  tui: [
    `██  ██ ████   ██  ██   ██       ████ ██     ████   `,
    `████   ~~██   ██  ~~ ██~~██   ██~~~~ ██     ~~██   `,
    `██  ██ ██████ ██████   ██~~     ████   ████ ██████ `,
    `~~  ~~ ~~~~~~  ~~~~~   ~~       ~~~~   ~~~~ ~~~~~~ `,
  ],
  plain: [
    `██  ██ ████   ██  ██   ███      ████ ██     ████   `,
    `████     ██   ██     ██  ██   ██     ██       ██   `,
    `██  ██ ██████ ██████   ██       ████ ██████ ██████ `,
  ],
}

function flag(value: string | undefined) {
  const key = value?.toLowerCase()
  if (!key) return
  if (yes.has(key)) return true
  if (no.has(key)) return false
}

export function supports(env = process.env, platform = process.platform) {
  const override = flag(env.KILO_UNICODE_LOGO)
  if (override !== undefined) return override
  // Terminals do not expose font glyph coverage over SSH, so prefer the safe logo for remote sessions.
  if (env.TERM === "dumb") return false
  if (env.SSH_TTY) return false
  if (env.SSH_CLIENT) return false
  if (env.SSH_CONNECTION) return false
  if (env.ConEmuPID) return false
  if (env.ANSICON) return false
  return true
}

export function tui(env = process.env, platform = process.platform) {
  return supports(env, platform) ? modern.tui : fallback.tui
}

export function plain(env = process.env, platform = process.platform) {
  return supports(env, platform) ? modern.plain : fallback.plain
}
