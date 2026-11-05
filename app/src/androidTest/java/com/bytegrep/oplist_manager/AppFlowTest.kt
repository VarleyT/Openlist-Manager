package com.bytegrep.oplist_manager

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 端到端界面流程：真实的 Activity、导航、ViewModel、仓库与网络栈，服务端由 [FakeOpenListServer] 模拟。
 *
 * 测试会临时清空本机已保存的服务器并关闭启动检查更新（避免更新弹窗遮挡界面），结束后恢复原状。
 */
@HiltAndroidTest
class AppFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var serverStore: ServerStore
    @Inject lateinit var settingsStore: AppSettingsStore

    private val fakeServer = FakeOpenListServer()
    private lateinit var savedServers: ServerState
    private var savedAutoCheckUpdate = true
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        fakeServer.start()
        runBlocking {
            savedServers = serverStore.current()
            savedAutoCheckUpdate = settingsStore.settings.first().autoCheckUpdate
            settingsStore.setAutoCheckUpdate(false)
            clearServers()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        fakeServer.shutdown()
        runBlocking {
            clearServers()
            savedServers.servers.forEach { serverStore.upsert(it, makeActive = false) }
            serverStore.setActive(savedServers.activeId)
            settingsStore.setAutoCheckUpdate(savedAutoCheckUpdate)
        }
    }

    private suspend fun clearServers() {
        serverStore.current().servers.forEach { serverStore.remove(it.id) }
        serverStore.setActive(null)
    }

    // ---------- 登录页 ----------

    @Test
    fun submitIsDisabledUntilAddressUsernameAndPasswordAreFilled() {
        waitForText(SUBMIT)
        submitButton().assertIsNotEnabled()

        textField(URL).performTextInput(fakeServer.address)
        textField(USERNAME).performTextInput(FakeOpenListServer.USERNAME)
        submitButton().assertIsNotEnabled()

        textField(PASSWORD).performTextInput(FakeOpenListServer.PASSWORD)
        submitButton().assertIsEnabled()
    }

    @Test
    fun plainHttpAddressShowsCleartextWarning() {
        waitForText(SUBMIT)

        textField(URL).performTextInput("192.168.1.2:5244")

        waitForText("HTTP 为明文传输")
    }

    @Test
    fun wrongPasswordShowsServerErrorAndStaysOnLogin() {
        waitForText(SUBMIT)

        fillLogin(password = "wrong")
        submitButton().performClick()

        waitForText(FakeOpenListServer.WRONG_PASSWORD_MESSAGE)
        submitButton().assertIsEnabled()
    }

    // ---------- 登录后主流程 ----------

    @Test
    fun loginThenSwitchTabsShowsServerData() {
        waitForText(SUBMIT)
        fillLogin(password = FakeOpenListServer.PASSWORD)
        submitButton().performClick()
        waitForText("服务器概览")

        tab("文件").performClick()
        waitForText(FakeOpenListServer.FILE_NAME)
        waitForText(FakeOpenListServer.FOLDER_NAME)

        tab("存储").performClick()
        waitForText(FakeOpenListServer.MOUNT_PATH)

        tab("我的").performClick()
        waitForText("应用设置")

        tab("概览").performClick()
        waitForText("服务器概览")
    }

    // ---------- 工具 ----------

    private fun fillLogin(password: String) {
        textField(URL).performTextInput(fakeServer.address)
        textField(USERNAME).performTextInput(FakeOpenListServer.USERNAME)
        textField(PASSWORD).performTextInput(password)
    }

    /** 登录表单输入框按界面顺序：地址、用户名、密码、2FA */
    private fun textField(index: Int): SemanticsNodeInteraction =
        composeRule.onAllNodes(hasSetTextAction())[index]

    private fun submitButton(): SemanticsNodeInteraction = composeRule.onNodeWithText(SUBMIT)

    private fun tab(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and isSelectable())

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val SUBMIT = "连接服务器"
        const val URL = 0
        const val USERNAME = 1
        const val PASSWORD = 2
        const val TIMEOUT_MS = 10_000L
    }
}
