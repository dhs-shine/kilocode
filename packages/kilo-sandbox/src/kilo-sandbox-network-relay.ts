import { spawn } from "node:child_process"
import { createServer, connect } from "node:net"

const split = process.argv.indexOf("--")
const socket = process.argv[2]
const seccomp = process.argv[3]
const command = split > -1 ? process.argv.slice(split + 1) : []

if (!socket || !seccomp || command.length === 0) {
  process.stderr.write("Invalid sandbox network relay invocation\n")
  process.exit(2)
}

const server = createServer((client) => {
  const upstream = connect({ path: socket })
  client.on("error", () => upstream.destroy())
  upstream.on("error", () => client.destroy())
  client.pipe(upstream)
  upstream.pipe(client)
})

server.listen(3128, "127.0.0.1", () => {
  const environment = { ...process.env }
  delete environment.BUN_BE_BUN
  const child = spawn(seccomp, command, { stdio: "inherit", env: environment })
  const forward = (signal: NodeJS.Signals) => child.kill(signal)
  for (const signal of ["SIGTERM", "SIGINT", "SIGHUP"] as const) process.on(signal, () => forward(signal))
  child.once("error", (cause) => {
    process.stderr.write(`${cause.message}\n`)
    server.close(() => process.exit(126))
  })
  child.once("exit", (code, signal) => {
    server.close(() => {
      if (signal) process.kill(process.pid, signal)
      process.exit(code ?? 1)
    })
  })
})

server.on("error", (cause) => {
  process.stderr.write(`${cause.message}\n`)
  process.exit(125)
})
