package com.bytegrep.oplist_manager

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/**
 * 仪器测试用的 Application 基类。
 * 正式 App 关闭了 WorkManager 默认初始化并由 [OpenListApp] 提供配置；测试 Application 必须同样提供，
 * 否则任务中心、文件页观察上传进度时会因 WorkManager 未初始化而崩溃。
 */
open class TestAppBase : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}

@CustomTestApplication(TestAppBase::class)
interface HiltTestApp

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApp_Application::class.java.name, context)
}
