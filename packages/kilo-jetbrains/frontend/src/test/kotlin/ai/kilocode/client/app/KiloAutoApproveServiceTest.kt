package ai.kilocode.client.app

import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class KiloAutoApproveServiceTest : BasePlatformTestCase() {

    fun `test auto approve starts disabled even with stale persisted value`() {
        PropertiesComponent.getInstance().setValue("kilo.permission.autoApprove.enabled", "true")

        val svc = KiloAutoApproveService()

        assertFalse(svc.active())
        PropertiesComponent.getInstance().unsetValue("kilo.permission.autoApprove.enabled")
    }

    fun `test toggle is runtime only`() {
        val svc = KiloAutoApproveService()

        assertTrue(svc.toggle())
        assertTrue(svc.active())

        assertFalse(PropertiesComponent.getInstance().getBoolean("kilo.permission.autoApprove.enabled", false))
    }
}
