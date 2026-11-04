# 开发计划

> 记录尚未实现或进行中的功能规划。已完成的功能见 README「功能特性」。

## 在线视频播放器（规划中）

### 目标

在 App 内直接在线播放 OpenList 上的视频，替代当前「预览页 → 外部应用打开」的流程。不需要完整下载，支持常见容器与流媒体格式，以及字幕、轨道、手势等常用播放能力。实际能播什么取决于编码格式和设备解码能力。

### 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 播放内核 | AndroidX Media3 ExoPlayer **1.11.1**（2026-09-10，最新稳定版） | 不使用已废弃的 `com.google.android.exoplayer2`。1.12 仍是 alpha，不采用 |
| 网络 | `media3-datasource-okhttp` | 复用项目现有的 OkHttp 连接池、超时和 UA |
| 渲染 | `media3-ui-compose` 的 `ContentFrame`（内部是 SurfaceView） | 控制层用 Compose 自绘，遵循 DESIGN.md |
| 字幕渲染 | `media3-ui` 的 `SubtitleView`（AndroidView 嵌入） | 稳定版 Compose 没有字幕组件（`CuesState` 从 1.12.0-alpha01 开始提供）。1.12 稳定后迁移 |
| 格式 | `media3-exoplayer-hls`、`media3-exoplayer-dash` | MP4/MKV/WebM 内置支持 |
| 解码 | 默认硬解优先，并开启 `setEnableDecoderFallback(true)` | 暂不引入 FFmpeg/VLC 扩展 |
| 后台/PiP | `media3-session`（第 4 阶段） | 采用 MediaSessionService 架构 |

依赖统一放在 `libs.versions.toml` 的 `media3 = "1.11.1"`，各模块版本必须一致。

**官方文档核对**（developer.android.com 的中国镜像 developer.android.google.cn，2026-11-04）：

- 发布页：稳定版 1.11.1 发布于 2026-09-10；最新为 1.12.0-alpha02（2026-10-26）。
- Compose 页：`media3-ui-compose` 提供 `PlayerSurface`、`ContentFrame` 和 `remember…State` 状态类，**不提供现成控件**，适合自有设计系统；`media3-ui-compose-material3` 提供现成的 `Player`、`MiniController`。两者只引入其一。本项目有 DESIGN.md，选用 `media3-ui-compose`。
- Compose 字幕：`CuesState` / `rememberCuesState` 在 **1.12.0-alpha01** 才加入。稳定版仍需 `SubtitleView`。
- 外挂字幕：官方支持 WebVTT（`TEXT_VTT`）、SubRip（`APPLICATION_SUBRIP`）、SSA/ASS（`TEXT_SSA`）、TTML。
- 软件解码扩展（FFmpeg、AV1 等）需**自行编译**引入，本期不做。
- 后台播放：需要 `FOREGROUND_SERVICE` 和 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限，Service 声明 `foregroundServiceType="mediaPlayback"`。
- 排障页：
  - 默认**不跟随跨协议重定向**（http↔https）。
  - HLS/DASH 被当作普通文件播放时会报 `UnrecognizedInputFormatException`，必须显式指定 MIME。
  - 没有 seek 索引的媒体被视为不可拖动。
  - 拖动慢可以用 `setSeekParameters` 降低精度来加快。
- 未核对：画中画 Compose 细节（官方页拉取不完整），留到第 4 阶段实现前再查。

### OpenList 链接机制（调研结论）

依据 OpenList 与 OpenList-Frontend 的 main 分支源码：

- **`POST /api/fs/get`** 返回 `raw_url`、`sign`、`provider`、`related`：
  - 驱动必须代理，或存储开启 `web_proxy` 时，`raw_url` = `down_proxy_url` 或 `{server}/p{path}?sign=`（OpenList 代理）。
  - 其他情况下，`raw_url` 是后端调用驱动生成的**第三方存储直链**，可能与请求方的 IP 和 UA 绑定。
  - `related`：只有视频才返回，内容是同目录中文件名前缀相同的文件，可用于找字幕。每项自带 `sign`。
- **`POST /api/fs/link`**：**仅管理员**可用，返回 `{url, header, content_length, ...}`。`header` 是驱动要求的请求头（如阿里 Referer、百度 UA）。`/api/fs/get` 不返回这些请求头。
- **`/d/{path}`**：不需要代理时 302 跳到驱动直链（第三方域名），否则由本机代理。**`/p/{path}`**：本机中转，不满足代理条件返回 403。两者都**不校验 Authorization**，只校验 `sign`。sign 有效期取设置项 `link_expiration`，默认 0 表示永不过期。
- **Range**：`/p` 透传 Range 到上游；直链是否支持取决于存储。
- **过期**：直链有时效，但 API 不返回过期时间。官方前端只给阿里云盘（video_preview）做了定时刷新。
- **官方前端**：先尝试 `POST /api/fs/transcode/play`（新增接口，返回 HLS `master_url`），失败再用 `raw_url`。字幕从 `related` 里筛选 `.srt/.ass/.vtt`，用 `/p{base_path}{dir}/{name}?sign=` 加载。

### 安全约束（必须遵守）

1. **播放器的网络请求一律不带 OpenList Token**。`/d`、`/p` 不需要认证；`raw_url` 和 302 目标可能是第三方域名。播放器 DataSource 使用不含 `SameOriginAuthInterceptor` 的 OkHttpClient。
2. Token 只用于 `/api/*` 请求，这部分沿用现有的 `session.request {}`。
3. `fs/link` 返回的驱动请求头只用于对应的那条 URL，不写入全局默认请求头。日志中脱敏 `Cookie`、`Authorization`，以及 URL 里的 `sign`。
4. 播放 URL 只接受 http/https，拒绝 `file:`、`content:` 等其他 scheme。

### 架构

播放器代码放在独立目录 `feature/player`，数据层放在 `data/player`。播放器只接收 OpenList 文件路径，不依赖文件列表 UI。

```
data/player/
  VideoPlaybackRepository   # fs/get、fs/link（管理员）、同目录列表；只负责 API 调用
  PlaybackSourceResolver    # 路径 → PlaybackSource（纯逻辑，可单测）
  PlaybackSource            # url、headers、mimeType 提示、subtitles、来源类型（直链/代理/驱动头）
  PlaybackPositionStore     # 播放位置记忆（第 2 阶段，DataStore，按 服务器+路径 存储，LRU 上限）
feature/player/
  VideoPlayerActivity       # 独立 Activity，见「决策」
  VideoPlayerScreen         # Compose：ContentFrame + 控制层
  VideoPlayerViewModel      # 持有 Controller，跨配置变更存活，onCleared 时 release
  VideoPlayerState          # 不可变 UI 状态：播放/缓冲/进度/错误/锁定/方向/倍速
  VideoPlayerController     # 封装 ExoPlayer：构建 MediaItem、DataSource，监听 Player.Listener 并映射为 State
  PlaybackErrorMapper       # PlaybackException → 中文提示 + 是否可通过重新获取链接恢复
```

**播放源解析流程（PlaybackSourceResolver）**

1. 调用 `fs/get(path)` 取得 `raw_url`、`sign`、`provider`、`related`。
2. 已知需要请求头的驱动（阿里、百度、189 等）且 `raw_url` 不是 `/p`、`/d` 代理地址时：
   - 当前用户是管理员：改调 `fs/link`，取 `url` 和 `header`。
   - 不是管理员：仍用 `raw_url`，403 时提示「该存储需要开启 Web 代理」。
3. 根据扩展名给出 MIME 提示：`.m3u8` 对应 HLS，`.mpd` 对应 DASH，**必须显式设置**（否则会被当作普通文件，报 `UnrecognizedInputFormatException`），其余交给 ExoPlayer 自动嗅探（直链的 Content-Type 常常不准）。
4. `related` 里的字幕文件，用各自的 `sign` 拼成 `/p{base_path}{dir}/{name}?sign=` 地址（第 3 阶段）。

**链接过期**：播放出错且错误码是 `IO_BAD_HTTP_STATUS` 401/403/410，或连接失败时，重新解析一次，然后 seek 回原位置继续播放。一次播放会话内最多自动恢复 1 次，避免死循环。

**不支持 Range 的服务器**：ExoPlayer 会从头读取并丢弃数据直到 seek 位置，播放能继续，但拖动很慢。处理方式：`isCurrentMediaItemSeekable == false` 时禁用进度条；能拖动但缓冲特别慢时，提示「服务器不支持断点续传，拖动进度会较慢」。快进、快退使用 `SeekParameters.CLOSEST_SYNC`（跳到最近关键帧）减少解码等待，拖动进度条时保持精确。

**跨协议重定向**：局域网 `http://` 的 OpenList 用 `/d` 302 跳到 `https://` 直链属于跨协议重定向。官方说明 ExoPlayer 自带的 `DefaultHttpDataSource` 默认不跟随这种跳转。本项目用 OkHttpDataSource，重定向交给 OkHttpClient 处理（`followSslRedirects` 默认开启），预期能正常跟随。需要用 MockWebServer 单测确认，并禁止 https 跳到 http 时继续携带驱动请求头。

### 决策

- **使用独立的 `VideoPlayerActivity`，不放进现有 NavHost**。原因：
  1. 画中画是 Activity 级别的，主 Activity 进入 PiP 会把整个导航一起缩小。
  2. 可以单独声明 `configChanges` 和方向，不影响其他页面。
  3. 更适合后台播放。
  
  从文件列表进入时，导航层先按 `FileKind.VIDEO` 判断，再用 Intent 传入 `path` 启动这个 Activity。
- 自动识别为视频的扩展名：mp4/mkv/webm/m4v/mov/ts/m3u8/mpd。其他格式（如 avi、rmvb、flv）保留预览页的「外部应用打开」。
- 暂不接入 `fs/transcode/play` 和阿里 `video_preview`，作为第 5 阶段的可选增强，需要先判断服务端是否支持。

### 分阶段计划

**第 1 阶段：基础在线播放（已实现，待提交）**

> 设备实测（2026-11-04，局域网本地存储）：MP4 在线播放；拖动到 1:10 处正常（Range 生效）；快进、暂停、锁定、横竖屏切换正常；MKV 使用硬件解码。DTS 音轨无法解码时会提示当前无声播放（实现时补充的处理）。
> 未实测：第三方直链（含 http→https 跨协议 302）、链接过期自动重取、断网错误面板——目前只有单测覆盖。

| # | 任务 | 验收 |
|---|---|---|
| 1.1 | 引入 Media3 1.11.1 依赖；确认 R8 release 包可正常播放 | release 包能播放 MP4 |
| 1.2 | `VideoPlaybackRepository` + `PlaybackSourceResolver` + `PlaybackSource` | 单测：直链、/p 代理、down_proxy_url、需要驱动头时管理员和非管理员两条分支、非法 scheme |
| 1.3 | 播放器专用 OkHttpClient（不含认证拦截器）+ 按条附加驱动请求头的 DataSource | 单测（MockWebServer）：请求不带 Authorization；302 跟随（含 http→https 跨协议）；Range 头 |
| 1.4 | `VideoPlayerController` / `ViewModel` / `State`：播放、暂停、进度、缓冲 | 旋转后播放器不重建，位置不丢 |
| 1.5 | `VideoPlayerActivity` + `VideoPlayerScreen`：全屏沉浸、点击显示或隐藏控制层、进度拖动、±10 秒 | 模拟器上手动验证 |
| 1.6 | 横竖屏：根据视频宽高比自动选择方向；手动旋转按钮 | 竖屏视频保持竖屏，横屏视频自动转横屏 |
| 1.7 | 锁定：控制层锁定（屏蔽触摸）+ 方向锁定 | 锁定后手势和按钮无效，只显示解锁按钮 |
| 1.8 | 错误处理：缓冲提示、`PlaybackErrorMapper` 中文分类、过期自动重取一次、手动重试 | 单测覆盖错误映射；断网后有提示并可重试 |
| 1.9 | 文件列表接入：视频直接打开播放器，预览页保留「外部应用打开」 | — |

**第 2 阶段：体验** — 倍速（0.5–3x）；手势（左侧上下滑调亮度，右侧调音量，横向滑动调进度，双击暂停）；播放位置记忆；同目录连续播放（复用 `fs/list` 结果，按当前排序方式）。

**第 3 阶段：字幕与轨道** — 外挂 SRT/ASS/VTT：自动匹配 `related`，也可手动从同目录选择；内封字幕和音轨的选择（`TrackSelectionOverride`）；字幕显示（SubtitleView）。

**第 4 阶段：PiP 与后台** — 迁移到 `MediaSessionService` + `MediaController`；画中画（Android 12+ 使用 `setAutoEnterEnabled`、`sourceRectHint`）；后台播放和媒体通知（`FOREGROUND_SERVICE_MEDIA_PLAYBACK`）。第 1 阶段的 Controller 保持面向 `Player` 接口编程，以便后续替换为 MediaController。

**第 5 阶段（可选）** — 支持 `fs/transcode/play` 云端转码 HLS；阿里云盘 `video_preview` 多清晰度。

### 兼容性风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 驱动直链需要特定请求头，但 `fs/get` 不返回这些头 | 阿里、百度、189 等直连返回 403 | 管理员走 `fs/link` 拿请求头；其他用户提示开启 Web 代理 |
| 直链与 IP、UA 绑定（如 115） | 无法播放 | 播放器与 API 使用同一个 OkHttp UA；网络切换后自动重取 |
| 直链过期，且没有过期时间字段 | 长时间暂停后续播失败 | 出错后重新解析一次并恢复位置 |
| 服务器不支持 Range | 拖动进度极慢 | 不可拖动时禁用进度条，慢时给提示 |
| 跨协议 302（http 服务器跳到 https 直链） | 使用默认 HTTP 栈时会播放失败 | 使用 OkHttpDataSource 并开启 `followSslRedirects`；单测覆盖 |
| HLS/DASH 直链的 Content-Type 或扩展名不准 | 报 `UnrecognizedInputFormatException` | 按扩展名显式设置 MIME；转码接口返回的地址固定按 HLS 处理 |
| `/p` 不满足代理条件返回 403 | 字幕加载失败 | 字幕加载失败不影响视频播放，只提示 |
| 用户有 base_path | `/p` 路径拼错 | 用 `currentUser.basePath` 拼完整路径；单测覆盖 |
| 没有 FFmpeg，DTS/TrueHD/部分 AC3 无法解码 | 无声或报错 | 识别 `DECODING_FORMAT_UNSUPPORTED`，提示「设备不支持该音频编码，可切换音轨或用外部播放器」 |
| ASS 只能部分渲染（特效、动画、内嵌字体不支持） | 字幕样式简化 | 文档说明，不阻塞播放 |
| `media3-ui-compose` 是 `@UnstableApi` | 升级时 API 可能变化 | 只在 `feature/player` 内使用，并锁定版本 |
| 跨主机 302 后 OkHttp 仍保留自定义请求头 | 驱动 Cookie 可能被带到重定向目标 | 驱动请求头只附加在 `fs/link` 返回的那条 URL 上；Cookie 类请求头在重定向时移除 |
| `fs/transcode/play` 是新接口 | 旧服务端没有 | 第 5 阶段先探测，404 就静默降级 |

### 待确认

- 是否接受用独立的 `VideoPlayerActivity`（推荐），而不是放进现有 NavHost 的路由？
- 管理员账号是否默认走 `fs/link` 获取驱动请求头？还是一律使用 `raw_url`，只依赖服务端开启 Web 代理？
- 最低支持的 OpenList 服务端版本（影响第 5 阶段转码接口的探测策略）。
