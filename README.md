<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_logo.png" alt="OpenList Manager Logo" width="112" />
</p>

<h1 align="center">OpenList Manager</h1>

<p align="center"><strong>随时随地管理你的 OpenList 服务器。</strong></p>

<p align="center">
  一款基于 Kotlin 与 Jetpack Compose 开发的原生 Android 客户端，支持文件管理、在线视频播放、存储挂载维护及服务器管理，无需打开网页后台。
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/11"><img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&amp;logoColor=white" /></a>
  <a href="https://github.com/VarleyT/Openlist-Manager/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/VarleyT/Openlist-Manager/ci.yml?branch=main&amp;label=CI" /></a>
  <a href="https://github.com/VarleyT/Openlist-Manager/releases"><img alt="Release" src="https://img.shields.io/github/v/release/VarleyT/Openlist-Manager" /></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue.svg" /></a>
</p>

<p align="center">
  <a href="https://github.com/VarleyT/Openlist-Manager/releases/latest">下载安装</a> ·
  <a href="#主要功能">功能介绍</a> ·
  <a href="#界面预览">界面预览</a> ·
  <a href="#从源码构建">源码构建</a> ·
  <a href="https://github.com/VarleyT/Openlist-Manager/issues">反馈问题</a>
</p>

> [!NOTE]
> 本项目是 [OpenList](https://github.com/OpenListTeam/OpenList) 的第三方 Android 客户端，与 OpenList 官方无关联。它**连接并管理已有的 OpenList 服务端**，不会在手机上部署 OpenList 服务。

## 快速开始

**使用前准备**

- 一台运行 **OpenList v4** 且手机能够访问的服务器；
- **Android 11（API 30）或更高版本**的手机；
- 一个可登录 OpenList 的账户。存储挂载、用户和系统配置等管理操作需要相应管理员权限。

**开始使用**

1. 前往 [Releases](https://github.com/VarleyT/Openlist-Manager/releases/latest) 下载 APK，或从 [全部版本](https://github.com/VarleyT/Openlist-Manager/releases) 选择所需版本。
2. 在手机上安装并启动 OpenList Manager。
3. 填写 OpenList 服务器地址、用户名和密码；如果账户启用了两步验证（2FA），再输入验证码。
4. 登录后即可浏览文件，在「我的」页面管理服务器连接、应用设置及其他管理功能。

服务器地址示例：

```text
https://nas.example.com
http://192.168.1.10:5244
```

优先使用 **HTTPS**。如需通过 HTTP 连接局域网内的 OpenList，请确保网络可信；HTTP 不会加密账号密码及传输内容。

## 界面预览

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/nav.gif" alt="服务器概览和导航" width="250" /><br /><strong>服务器概览与导航</strong></td>
    <td align="center"><img src="docs/screenshots/files.gif" alt="文件列表与文件操作" width="250" /><br /><strong>文件管理</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/storage.gif" alt="存储挂载管理" width="250" /><br /><strong>存储挂载管理</strong></td>
    <td align="center"><img src="docs/screenshots/theme.gif" alt="深色主题" width="250" /><br /><strong>深色主题</strong></td>
  </tr>
</table>

> 以上截图展示部分界面；在线视频播放器等新增功能不一定包含在现有动图中。

## 主要功能

| 模块 | 功能 |
| --- | --- |
| **服务器概览** | 查看服务器状态、存储挂载统计、异常挂载提醒和常用操作入口。 |
| **文件管理** | 目录浏览、面包屑导航、列表/网格视图、排序、搜索、上传、下载、新建文件夹、重命名、复制、移动、删除。 |
| **文件预览** | 图片缩放、文本/代码预览、复制直链，以及调用外部应用打开其他类型文件。 |
| **在线视频播放** | 内置播放器直接播放支持的在线视频，提供倍速、手势、播放记忆、连续播放和字幕/音轨切换。 |
| **存储挂载** | 查看挂载状态、启停与重载存储；依据驱动字段生成编辑表单，提供部分字段的中文说明。 |
| **任务中心** | 查看上传、复制、移动及离线下载等任务的状态和进度。 |
| **用户管理** | 创建、编辑、禁用、删除用户，配置角色、基础路径和权限。 |
| **系统设置** | 查看并修改 OpenList 的站点配置。 |
| **多服务器** | 保存、切换和移除多个服务器连接，凭据按服务器隔离。 |
| **应用体验** | 浅色/深色/跟随系统主题、Android 12+ 动态配色、版本更新检查。 |

### 在线视频播放

内置播放器基于 **AndroidX Media3 / ExoPlayer**。在文件列表点击支持的格式即可尝试直接播放，无需先完整下载。

- **支持的文件扩展名**：`mp4`、`m4v`、`mkv`、`webm`、`mov`、`ts`、`3gp`、`m3u8`（HLS）、`mpd`（DASH）。对于 `avi`、`rmvb`、`flv` 等文件，可在预览页选择外部应用打开。
- **播放控制**：播放/暂停、进度拖动、快进/快退、倍速播放、横竖屏切换、屏幕锁定，以及手势调节进度、亮度和音量。
- **连续播放与进度记忆**：支持同一文件夹内的视频切换和播完自动播放下一条，并记住上次播放位置。
- **音轨与字幕**：支持选择内嵌音轨/字幕，以及自动查找同文件夹的 `srt`、`ass`、`ssa`、`vtt` 外挂字幕。
- **音频解码兜底**：优先采用设备系统解码器；设备不支持时，尝试使用内置 **FFmpeg** 扩展软件解码 DTS（包括 DTS-HD 核心音轨）、TrueHD、AC3、E-AC3、ALAC 等音频。
- **网络恢复**：对部分播放链接过期或网络中断的情况，尝试重新获取直链并恢复播放进度。

> [!IMPORTANT]
> 文件扩展名在支持列表中，**不代表所有视频编码、字幕和网络环境都能播放**。视频解码主要依赖设备能力；某些 OpenList 存储还需要正确配置直链权限或「Web 代理」。FFmpeg 扩展主要用于补充**音频解码**，并非通用的全格式视频解码器。

更多版本变更请查看 [Release Notes](docs/release-notes/)。

## 从源码构建

### 构建要求

| 项目 | 当前仓库配置 |
| --- | --- |
| Android Studio | 支持当前 Android Gradle Plugin 的版本 |
| Gradle Daemon JDK | **25**（见 `gradle/gradle-daemon-jvm.properties`） |
| Java 源码兼容级别 | 17（**不同于**运行 Gradle 的 JDK 版本） |
| Gradle | **9.6.0**（通过 Gradle Wrapper） |
| Android Gradle Plugin | **9.5.0** |
| Kotlin | **2.3.21** |
| Android SDK | `compileSdk 37`，`targetSdk 36`，`minSdk 30` |
| Media3 | **1.11.1** |

构建机器还需要安装相应的 Android SDK/Build Tools，并确保 Gradle 可以下载项目依赖。无需手动安装全局 Gradle。

### 构建与测试

```bash
git clone https://github.com/VarleyT/Openlist-Manager.git
cd Openlist-Manager

# 构建 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Release APK（签名可选，见下文）
./gradlew assembleRelease
```

Windows 命令提示符或 PowerShell 中，将 `./gradlew` 替换为 `./gradlew.bat`。

输出位置：

- Debug APK：`app/build/outputs/apk/debug/`
- Release APK：`app/build/outputs/apk/release/`

### Release 签名（可选）

项目会读取 `keystore/keystore.properties`。如需在本地构建**已签名**的 Release APK，请在仓库根目录创建 `keystore/`，放入私有签名文件 `release.jks`，并创建以下配置：

```properties
storeFile=release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

`keystore/` 已列入 `.gitignore`；**不要将 keystore 或真实密码提交到仓库**。未提供本地签名配置时，`assembleRelease` 仍可构建，但产物不会自动签名，不能按已签名安装包使用。

### CI、发布与 FFmpeg 扩展

- [CI](.github/workflows/ci.yml)：推送到 `main`、提交 PR 或手动触发时执行单元测试和 Debug 构建；纯文档变更推送到 `main` 时通常不会触发。
- [Release](.github/workflows/release.yml)：推送 `v*` 标签时构建签名 APK，发布 GitHub Release，并附带 SHA-256 校验文件。该工作流需要仓库 Actions Secrets 中配置 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS` 和 `KEY_PASSWORD`，且必须有对应的 `docs/release-notes/<tag>.md`。
- [Build FFmpeg decoder](.github/workflows/build-ffmpeg-decoder.yml)：手动构建 Media3 官方 FFmpeg 音频扩展。仓库已经包含 `app/libs/lib-decoder-ffmpeg-release.aar`；如果升级 Media3，应使用**与项目一致**的 Media3 版本重新构建并替换 AAR。

## 技术架构

- **界面**：Kotlin、Jetpack Compose、Material 3、Navigation Compose。
- **架构**：单 Activity、MVVM、按功能分包，Hilt 依赖注入，Coroutines / Flow 状态管理。
- **网络**：Retrofit、OkHttp、Kotlinx Serialization，通过 OpenList REST API 与服务器通信。
- **数据与任务**：DataStore、Android Keystore、WorkManager；Coil 用于图片加载。
- **播放器**：Media3 ExoPlayer、HLS/DASH、FFmpeg 音频解码扩展。
- **测试**：JUnit、MockK、Turbine、MockWebServer。

<details>
<summary>查看主要源码目录</summary>

```text
app/src/main/java/com/bytegrep/oplist_manager/
├── core/
│   ├── datastore/      # 服务器与应用偏好
│   ├── designsystem/   # UI 组件与主题
│   ├── network/        # API 客户端与认证
│   └── security/       # Keystore 加密
├── data/
│   ├── model/          # OpenList API 数据模型
│   ├── player/         # 播放源、播放列表与进度
│   ├── repository/     # 数据访问
│   ├── session/        # 会话管理
│   └── update/         # 版本更新
├── feature/
│   ├── dashboard/      # 服务器概览
│   ├── files/          # 文件浏览、传输与预览
│   ├── login/          # 登录与二步验证
│   ├── player/         # 在线视频播放器
│   ├── profile/        # 我的、应用设置与关于
│   ├── servers/        # 多服务器管理
│   ├── settings/       # OpenList 系统设置
│   ├── storage/        # 存储挂载管理
│   ├── tasks/          # 任务中心
│   └── users/          # 用户管理
└── navigation/         # 路由与底部导航
```

</details>

## 安全与隐私

- 服务器 Token 与保存的密码使用 **Android Keystore 中的 AES-256-GCM 密钥**加密；服务器凭据数据排除在系统备份之外。
- 网络请求按服务器来源区分认证信息，不会将 OpenList Token 无条件发送给第三方播放直链。
- HTTPS 使用系统信任的 CA 证书，不主动忽略证书验证错误。
- 为兼容局域网 OpenList，允许使用 `http://`，但**HTTP 流量不加密**；公网访问建议配置 HTTPS。
- 检查应用更新时访问 GitHub Release，不会将 OpenList 登录凭据作为更新请求参数。

## 常见问题

<details>
<summary>文件搜索没有结果？</summary>

文件搜索依赖 OpenList 服务端索引。请先在 OpenList 管理后台开启、构建并检查索引。

</details>

<details>
<summary>视频无法播放或只有画面没有声音？</summary>

请确认文件格式和视频编码受设备支持，并尝试切换音轨。内置 FFmpeg 扩展可以为部分缺失的音频解码器提供兜底，但不能解决所有视频解码问题。遇到 `403` 等访问问题，请检查 OpenList 存储直链权限或「Web 代理」设置；必要时改用外部播放器。

</details>

<details>
<summary>管理操作不可用？</summary>

挂载、用户及系统配置属于 OpenList 管理功能，需使用具有相应权限的账户。普通账户仍可根据服务端授权使用文件功能。

</details>

<details>
<summary>如何检查更新？</summary>

可在「我的 → 关于」中手动检查更新；也可以在「我的 → 应用设置」中控制启动时检查更新。新版本下载页面由 GitHub 提供。

</details>

## 参与贡献

欢迎通过 [Issues](https://github.com/VarleyT/Openlist-Manager/issues) 反馈 Bug、提出建议，或提交 Pull Request。提交代码前请先运行：

```bash
./gradlew testDebugUnitTest
```

建议在 PR 中说明改动原因、验证方式；如果新增功能或修改交互，请同步更新 README 或相应文档。

## 许可证与致谢

本项目主体采用 [MIT License](LICENSE) 开源。内置的 **FFmpeg 音频解码组件**使用 FFmpeg 的 LGPL 授权，与应用主体的 MIT 许可相区分；分发和再构建时请遵循对应第三方组件的许可要求。

感谢 [OpenListTeam/OpenList](https://github.com/OpenListTeam/OpenList)、[AndroidX Media3](https://github.com/androidx/media) 和 [FFmpeg](https://ffmpeg.org/) 等开源项目。