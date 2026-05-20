package ai.kilocode.client.settings

import ai.kilocode.client.settings.profile.UserProfileConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.Settings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import java.awt.Container
import javax.swing.AbstractButton
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

@Suppress("UnstableApiUsage")
class KiloSettingsConfigurableTest : BasePlatformTestCase() {

    fun `test id matches xml registration`() {
        val cfg = KiloSettingsConfigurable()
        assertEquals("ai.kilocode.jetbrains.settings", cfg.id)
    }

    fun `test hasOwnContent is true`() {
        val cfg = KiloSettingsConfigurable()
        assertTrue(cfg.hasOwnContent())
    }

    fun `test getConfigurables contains UserProfileConfigurable`() {
        val cfg = KiloSettingsConfigurable()
        val kids = cfg.configurables
        assertTrue("expected at least one child configurable", kids.isNotEmpty())
        val profile = kids.find { it is UserProfileConfigurable }
        assertNotNull("expected UserProfileConfigurable in children", profile)
        assertEquals(UserProfileConfigurable.ID, (profile as UserProfileConfigurable).id)
    }

    fun `test createComponent contains description text`() {
        val cfg = KiloSettingsConfigurable()
        edt {
            val panel = cfg.createComponent()
            assertNotNull(panel)
            val all = text(panel as Container)
            assertTrue("root panel should contain description text", all.isNotEmpty())
        }
    }

    fun `test createComponent contains User Profile link`() {
        val cfg = KiloSettingsConfigurable()
        edt {
            val panel = cfg.createComponent()
            val links = links(panel as Container)
            assertTrue("root panel should contain at least one ActionLink", links.isNotEmpty())
            assertTrue(
                "expected a link labeled 'User Profile'",
                links.any { it.text == "User Profile" }
            )
        }
    }

    fun `test User Profile link selects registered configurable`() {
        val cfg = KiloSettingsConfigurable()
        val settings = TestSettings(cfg)
        cfg.open(settings, cfg.configurables.first { it is UserProfileConfigurable })
        assertEquals(UserProfileConfigurable.ID, (settings.selected as UserProfileConfigurable).id)
    }

    fun `test isModified always false`() {
        assertFalse(KiloSettingsConfigurable().isModified)
    }

    // -- helpers --

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun links(root: Container): List<ActionLink> = buildList {
        for (comp in root.components) {
            if (comp is ActionLink) add(comp)
            if (comp is Container) addAll(links(comp))
        }
    }

    private fun text(root: Container): String {
        val acc = mutableListOf<String>()
        collectText(root, acc)
        return acc.joinToString("\n")
    }

    private fun collectText(root: Container, acc: MutableList<String>) {
        for (comp in root.components) {
            when (comp) {
                is AbstractButton -> comp.text?.let { acc.add(it) }
                is javax.swing.JLabel -> comp.text?.let { acc.add(it) }
            }
            if (comp is Container) collectText(comp, acc)
        }
    }

    private class TestSettings(private val root: KiloSettingsConfigurable) : Settings(listOf(Group(root))) {
        var selected: Configurable? = null

        override fun selectImpl(configurable: Configurable): Promise<Any> {
            selected = configurable
            return AsyncPromise<Any>().also { it.setResult(configurable) }
        }

        override fun getConfigurableWithInitializedUiComponentImpl(
            configurable: Configurable,
            initializeUiComponentIfNotYet: Boolean,
        ): Configurable = configurable

        override fun checkModifiedImpl(configurable: Configurable) = Unit

        override fun setSearchText(option: String) = Unit

        private class Group(private val root: KiloSettingsConfigurable) : ConfigurableGroup {
            override fun getDisplayName(): String = root.displayName

            override fun getConfigurables(): Array<Configurable> = arrayOf(root)
        }
    }
}
