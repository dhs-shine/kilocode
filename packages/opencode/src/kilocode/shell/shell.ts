export function args(command: string) {
  return ["-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script(command)]
}

const setup = `[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false);
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false);
$OutputEncoding = [Console]::OutputEncoding;
`

function script(command: string) {
  const pos = prologue(command)
  const head = command.slice(0, pos)
  const body = command.slice(pos)
  const gap = head && !/[;\r\n]\s*$/.test(head) ? "\n" : ""
  return `${head}${gap}${setup}${body}`
}

function prologue(command: string) {
  const pos = scan(command, 0)
  const body = command.slice(pos)
  const match = /^\s*param\s*\(/i.exec(body)
  if (!match) return pos

  const start = match[0].lastIndexOf("(")
  const end = block(body, start)
  if (end === undefined) return pos
  return pos + end
}

function scan(command: string, start: number) {
  let pos = start
  while (pos < command.length) {
    const end = line(command, pos)
    const value = command.slice(pos, end)
    const text = value.trimStart()
    if (text.trim() === "" || text.startsWith("#") || /^using\s+(?:assembly|module|namespace|type)\b/i.test(text)) {
      pos = end
      continue
    }
    return pos
  }
  return pos
}

function line(command: string, start: number) {
  const index = command.indexOf("\n", start)
  if (index === -1) return command.length
  return index + 1
}

function block(command: string, start: number) {
  let depth = 0
  let quote: string | undefined
  for (let pos = start; pos < command.length; pos++) {
    const char = command[pos]
    if (quote) {
      if (quote === "'" && char === "'" && command[pos + 1] === "'") {
        pos++
        continue
      }
      if (quote === '"' && char === "`") {
        pos++
        continue
      }
      if (char === quote) quote = undefined
      continue
    }
    if (char === "'" || char === '"') {
      quote = char
      continue
    }
    if (char === "#") {
      pos = line(command, pos) - 1
      continue
    }
    if (char === "(") depth++
    if (char === ")") {
      depth--
      if (depth === 0) return pos + 1
    }
  }
}
