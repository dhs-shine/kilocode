package ai.kilocode.backend.dev

object KiloDevMode {
    fun enabled(): Boolean = System.getProperty("idea.plugin.in.sandbox.mode", "false").toBoolean()
}
