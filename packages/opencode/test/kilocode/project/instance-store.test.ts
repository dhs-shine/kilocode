import { describe, expect } from "bun:test"
import { CrossSpawnSpawner } from "@opencode-ai/core/cross-spawn-spawner"
import { Deferred, Effect, Fiber, Layer } from "effect"
import { registerDisposer } from "../../../src/effect/instance-registry"
import { InstanceBootstrap } from "../../../src/project/bootstrap-service"
import { InstanceStore } from "../../../src/project/instance-store"
import { tmpdirScoped } from "../../fixture/fixture"
import { awaitWithTimeout, testEffect } from "../../lib/effect"

const bootstrap = Layer.succeed(InstanceBootstrap.Service, InstanceBootstrap.Service.of({ run: Effect.void }))
const it = testEffect(
  Layer.mergeAll(InstanceStore.defaultLayer, CrossSpawnSpawner.defaultLayer).pipe(Layer.provide(bootstrap)),
)

const register = (disposer: (directory: string) => Promise<void>) =>
  Effect.acquireRelease(
    Effect.sync(() => registerDisposer(disposer)),
    (off) => Effect.sync(off),
  )

describe("InstanceStore disposal", () => {
  it.live("disposes four directories concurrently", () =>
    Effect.gen(function* () {
      const dirs = yield* Effect.all(
        Array.from({ length: 4 }, () => tmpdirScoped({ git: true })),
        { concurrency: "unbounded" },
      )
      const store = yield* InstanceStore.Service
      const ready = yield* Deferred.make<void>()
      const release = yield* Deferred.make<void>()
      const started = new Set<string>()

      yield* Effect.addFinalizer(() => Deferred.succeed(release, undefined).pipe(Effect.ignore))
      yield* register(async (directory) => {
        if (!dirs.includes(directory)) return
        started.add(directory)
        if (started.size === dirs.length) Deferred.doneUnsafe(ready, Effect.void)
        await Effect.runPromise(Deferred.await(release))
      })

      yield* Effect.forEach(dirs, (directory) => store.load({ directory }), { discard: true })
      const fiber = yield* store.disposeAll().pipe(Effect.forkScoped)

      yield* awaitWithTimeout(Deferred.await(ready), "instance disposal remained serial", "1 second")
      expect(started).toEqual(new Set(dirs))

      yield* Deferred.succeed(release, undefined)
      yield* Fiber.join(fiber)
    }),
  )
})
