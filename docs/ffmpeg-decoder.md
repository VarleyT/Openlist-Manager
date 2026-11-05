# FFmpeg 音频解码扩展

在线播放器使用系统解码器（MediaCodec）解码音视频。多数手机系统不带 DTS、TrueHD，部分手机也不带 AC3、E-AC3。这些格式的音频由 Media3 官方 FFmpeg 扩展（`decoder_ffmpeg`）软解兜底。

## 现状

- 产物：`app/libs/lib-decoder-ffmpeg-release.aar`，以二进制形式提交到仓库。
- 版本：Media3 1.11.1、FFmpeg release/6.0（573f2b8）、NDK 28.2.13676358、原生 API 级别 23。
- 包含 armeabi-v7a、arm64-v8a、x86、x86_64 四个 ABI，每个 `libffmpegJNI.so` 约 1.1–1.2 MB；64 位 ABI 已 16KB 页对齐。
- 启用的解码器：`ac3 eac3 dca truehd mlp alac`，只有音频，没有视频软解。
- 播放器的扩展渲染模式为 `EXTENSION_RENDERER_MODE_ON`：优先用系统解码器，系统不支持时才使用 FFmpeg。
- 许可：Media3 扩展代码是 Apache 2.0。FFmpeg 按默认配置构建（LGPL 2.1+），未启用 GPL 组件，与本项目的 MIT 许可兼容。分发时须遵守 LGPL：说明使用了 FFmpeg 并提供对应源码获取方式（本仓库工作流即构建脚本，FFmpeg 源码来自官方 release/6.0 分支）。DTS、杜比等编码可能涉及专利，公开分发前请自行评估。

## 重新构建

Google Maven 不提供该扩展，需在 Linux + NDK 环境下编译。仓库提供 GitHub Actions 工作流 `.github/workflows/build-ffmpeg-decoder.yml`：

1. 打开 Actions → 「Build FFmpeg decoder」→ Run workflow（或执行 `gh workflow run build-ffmpeg-decoder.yml`）。可以修改 Media3 版本、FFmpeg 分支、解码器列表、原生 API 级别、NDK 版本。
   - 原生 API 级别须等于 `decoder_ffmpeg` 模块的 minSdk（1.11.1 为 23），不能填 App 的 minSdk：更高的级别会让 FFmpeg 启用 `iconv` 等新 API，导致 JNI 按模块 minSdk 链接时符号未定义。
   - NDK 版本须与该 Media3 版本 build-logic 固定的 `ndkVersion` 一致（1.11.1 为 28.2.13676358），否则 Gradle 报 CXX1100。
2. 运行成功后下载产物 `lib-decoder-ffmpeg-<版本>`，里面有 aar 和 `build-info.txt`（版本、NDK、sha256）。工作流会校验 64 位 ABI（arm64-v8a、x86_64）的 `libffmpegJNI.so` 是否 16KB 页对齐，不满足时运行失败；32 位 ABI 为 4KB 对齐属正常。
3. 用新 aar 替换 `app/libs/lib-decoder-ffmpeg-release.aar`，并更新本文的版本信息。

**升级 Media3 时必须用同一版本重新构建本扩展**。`FfmpegAudioRenderer` 属于 `@UnstableApi`，版本不一致可能在运行时出现 `NoSuchMethodError`。

## 已知限制

- 手机通常不支持音频直通（passthrough），多声道音轨由 FFmpeg 解码后再下混输出。
- 视频编码（如 HEVC 10-bit）仍依赖设备硬件解码，本扩展不涉及。
