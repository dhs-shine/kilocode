package ai.kilocode.backend.dev

import ai.kilocode.log.KiloEnvironment

object KiloDevMode {
    fun enabled(): Boolean = KiloEnvironment.sandbox()
}
