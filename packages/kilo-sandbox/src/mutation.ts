import { spawn } from "node:child_process"
import { fileURLToPath } from "node:url"
import { Context, Effect, PlatformError } from "effect"
import { confine } from "./backend"
import { isResponse, type Failure, type Request } from "./mutation-protocol"
import type { Profile } from "./profile"

declare const KILO_SANDBOX_MUTATION_WORKER_PATH: string

function worker() {
  if (typeof KILO_SANDBOX_MUTATION_WORKER_PATH === "undefined") {
    return { args: [fileURLToPath(new URL("./mutation-worker.ts", import.meta.url))], environment: {} }
  }
  if (KILO_SANDBOX_MUTATION_WORKER_PATH === "") {
    return { args: [], environment: { KILO_SANDBOX_MUTATION_WORKER: "1" } }
  }
  const path = KILO_SANDBOX_MUTATION_WORKER_PATH.startsWith(".")
    ? fileURLToPath(new URL(KILO_SANDBOX_MUTATION_WORKER_PATH, import.meta.url))
    : KILO_SANDBOX_MUTATION_WORKER_PATH
  return { args: [path], environment: {} }
}

function tag(code: string | undefined): PlatformError.SystemErrorTag {
  switch (code) {
    case "EEXIST":
      return "AlreadyExists"
    case "EBADF":
    case "EISDIR":
    case "ELOOP":
    case "ENOTDIR":
      return "BadResource"
    case "EBUSY":
      return "Busy"
    case "EINVAL":
      return "InvalidData"
    case "ENOENT":
      return "NotFound"
    case "EACCES":
    case "EPERM":
      return "PermissionDenied"
    case "ETIMEDOUT":
      return "TimedOut"
    case "EAGAIN":
      return "WouldBlock"
    default:
      return "Unknown"
  }
}

function failure(method: string, path: string, error: Failure) {
  const cause = Object.assign(new Error(error.message), {
    name: error.name,
    code: error.code,
    errno: error.errno,
    syscall: error.syscall,
    path: error.path,
    dest: error.dest,
  })
  if (!error.code) {
    return PlatformError.badArgument({ module: "FileSystem", method, description: error.message, cause })
  }
  return PlatformError.systemError({
    _tag: tag(error.code),
    module: "FileSystem",
    method,
    pathOrDescriptor: error.path ?? path,
    syscall: error.syscall,
    description: error.message,
    cause,
  })
}

function infrastructure(path: string, description: string, cause?: unknown) {
  return PlatformError.systemError({
    _tag: "Unknown",
    module: "Sandbox",
    method: "mutate",
    pathOrDescriptor: path,
    description,
    cause,
  })
}

function target(request: Request) {
  if ("path" in request) return request.path
  if ("to" in request) return request.to
  return request.options?.directory ?? process.cwd()
}

function output(stream: NodeJS.ReadableStream) {
  return new Promise<Buffer>((resolve, reject) => {
    const chunks: Buffer[] = []
    stream.on("data", (chunk: Buffer | string) => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)))
    stream.on("end", () => resolve(Buffer.concat(chunks)))
    stream.on("error", reject)
  })
}

export type Runner = (
  profile: Profile,
  request: Request,
) => Effect.Effect<string | undefined, PlatformError.PlatformError>

export const mutate: Runner = (profile, request) =>
  Effect.scoped(
    Effect.gen(function* () {
      const child = worker()
      const launch = yield* confine(profile, {
        command: process.execPath,
        args: child.args,
        cwd: process.cwd(),
        environment: process.env,
      })
      const path = target(request)
      const result = yield* Effect.tryPromise({
        try: async (signal) => {
          const proc = spawn(launch.command, launch.args, {
            cwd: launch.cwd,
            env: { ...launch.environment, ...child.environment },
            stdio: ["pipe", "pipe", "pipe"],
            windowsHide: process.platform === "win32",
          })
          const abort = () => proc.kill()
          signal.addEventListener("abort", abort, { once: true })
          const exited = new Promise<number | null>((resolve, reject) => {
            proc.once("error", reject)
            proc.once("close", resolve)
          })
          const stdout = output(proc.stdout)
          const stderr = output(proc.stderr)
          proc.stdin.end(JSON.stringify(request))
          const [out, err, code] = await Promise.all([stdout, stderr, exited]).finally(() =>
            signal.removeEventListener("abort", abort),
          )
          if (code !== 0) {
            throw infrastructure(path, err.toString("utf8").trim() || `Filesystem worker exited with code ${code}`)
          }
          try {
            const response: unknown = JSON.parse(out.toString("utf8"))
            if (isResponse(response)) return response
            throw new TypeError("Invalid filesystem worker response")
          } catch (cause) {
            throw infrastructure(path, "Filesystem worker returned an invalid response", cause)
          }
        },
        catch: (cause) =>
          cause instanceof PlatformError.PlatformError
            ? cause
            : infrastructure(path, cause instanceof Error ? cause.message : String(cause), cause),
      })
      if (!result.ok) return yield* Effect.fail(failure(request.op, path, result.error))
      return result.value
    }),
  )

const CurrentRunner = Context.Reference<Runner>("@kilocode/sandbox/CurrentMutationRunner", {
  defaultValue: () => mutate,
})

export const currentRunner: Effect.Effect<Runner> = Effect.gen(function* () {
  return yield* CurrentRunner
})

export function withRunner<A, E, R>(runner: Runner, effect: Effect.Effect<A, E, R>) {
  return effect.pipe(Effect.provideService(CurrentRunner, runner))
}
