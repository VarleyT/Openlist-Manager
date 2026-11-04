# DESIGN.md — Mika-inspired UI Design System

> 版本：1.0 · 适用场景：Web 工具站、AI 助手、设置面板及桌面/移动端响应式界面
>
> 参考：[Mika](https://github.com/wangzan101/Mika) 的 Jetpack Compose UI 源码。本规范提炼其设计语言并转换为 Web 可实现的规则；并非官方设计文档，也不要求逐像素复刻。

## 1. 设计目标

打造**简洁、轻盈、稳定、易用**的界面：接近系统原生应用的精致感，弱化容器，突出内容和操作。

### 设计原则

1. **内容优先**：用留白、字号和对比度建立层级，不靠大量装饰。
2. **中性色为主**：背景、卡片、图标块保持中性；蓝色仅用于可执行主操作、选中项和焦点。
3. **统一形状**：大面积卡片圆角，操作控件圆润，分组列表保持一致。
4. **轻量深度**：浅色模式用细描边和极淡阴影；深色模式更依赖色阶与描边。
5. **克制动效**：动画帮助说明状态变化，不为展示炫技。
6. **双主题一致**：深浅色使用同一套语义 token；禁止在业务组件中硬编码色值。
7. **可访问**：键盘可操作、触控目标足够大、支持减少动态效果。

## 2. 视觉设计 Tokens

### 2.1 颜色

| 语义 | 浅色模式 | 深色模式 | 用途 |
|---|---|---|---|
| `bg` | `#F7F7F8` | `#0E0E10` | 页面背景 |
| `surface` | `#FFFFFF` | `#1A1A1D` | 卡片/面板 |
| `ink` | `#1C1C1E` | `#F2F2F4` | 主要文字 |
| `ink2` | `#5F6068` | `#A4A5AD` | 次要文字 |
| `ink3` | `#9A9BA3` | `#6C6D75` | 辅助说明 |
| `accent` | `#0A7CFF` | `#3D97FF` | 主按钮、选中状态、焦点 |
| `success` | `#1F9D55` | `#3CCF7A` | 成功状态 |
| `warning` | `#B86E00` | `#FFB340` | 警告状态 |
| `danger` | `#E5483D` | `#FF6961` | 错误/危险操作 |

辅助色使用透明度表达：

- 浅色填充：`rgba(0,0,0,.04)`；分割线：`rgba(0,0,0,.07)`；卡片边线：`rgba(0,0,0,.05)`。
- 深色填充：`rgba(255,255,255,.06)`；分割线：`rgba(255,255,255,.08)`；卡片边线：`rgba(255,255,255,.06)`。
- 浅色毛玻璃底：`rgba(255,255,255,.72)`；深色毛玻璃底：`rgba(26,26,29,.72)`。
- 柔和强调底色：浅色 `rgba(10,124,255,.10)`；深色 `rgba(61,151,255,.16)`。

**使用限制**：同一页面不要同时出现多个高饱和度主色；不要给普通列表图标随机着色；成功、警告、危险色仅表达对应语义。

### 2.2 排版

推荐系统字体栈：`-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif`。

| 类型 | 字号 / 行高 | 字重 | 用途 |
|---|---|---|---|
| Large Title | 28 / 35 px | 700 | 欢迎页、空状态 |
| Title | 17 / 22 px | 600 | 页面顶栏标题 |
| Body | 15.5 / 22 px | 400 | 正文、列表项 |
| Body Strong | 15.5 / 22 px | 600 | 卡片标题、强调信息 |
| Caption | 13 / 18 px | 400 | 卡片说明 |
| Label | 13 / 18 px | 500 | 分组标签 |
| Hint | 12 / 17 px | 400 | 帮助、时间、脚注 |
| Value | 15 / 20 px | 400 | 行末数值 |

原则：正文优先保证可读性；尽量不使用全大写标签；对齐基线，避免过多字重。Web 下 `15.5px` 可按平台渲染效果调整为 `16px`。

### 2.3 间距与尺寸

- **间距基数**：`4 / 8 / 12 / 16 / 20 / 24 px`。
- 页面水平内边距：`16px`；常规卡片内容内边距：`16px`。
- 移动端顶栏：`56px`；列表行最小高度：`52px`。
- 大按钮：`52px`；中按钮：`42px`；小按钮：`32px`。
- 图标按钮：`40 × 40px`，常规图标：`22px`；列表图标块：`30px`。
- 分组之间保持约 `20px` 的视觉间隔；卡片内各元素优先用 `8–12px`。

### 2.4 圆角与阴影

| 元素 | 圆角 |
|---|---|
| 卡片 / 分组面板 | `16px` |
| 主按钮 | `14px` |
| 聊天气泡 | `20px`（尾角一侧可用 `6px`） |
| 小图标块 | `9px` |
| 分段选择器 | 外层 `10px`，内层 `8px` |
| 胶囊 / 悬浮输入栏 | `9999px` |

卡片采用 `1px` 极淡边框 + 微弱阴影；悬浮控件的阴影可更明显。不要使用黑色粗边框、强烈投影或高亮外发光。

## 3. 组件规范

### 3.1 页面骨架

- 页背景始终使用 `bg`；内容与浏览器/系统安全区域对齐。
- 顶栏：左侧返回或菜单，中间标题，右侧操作；标题文本不被两侧按钮挤压。
- 主内容区使用一致的水平间距；复杂页面可分为侧栏、内容区和上下文面板。
- 页面滚动独立于悬浮操作栏；滚动内容不能被底部输入栏遮挡。

### 3.2 卡片（Card）与分组（Group）

- 用 `surface` 背景、淡边框、16px 圆角。
- 同类设置项可合并为**一张分组卡片**，内部使用细分割线，而不是每一行一个大卡片。
- 卡片内容推荐：图标/标题/说明/右侧数值或箭头；整行可点击时提供悬停和按压反馈。
- 简单信息不必套卡片；防止出现多层嵌套卡片。

### 3.3 按钮（Button）

- **Primary**：强调蓝背景 + 白字；同一主要操作区域通常只保留一个。
- **Secondary**：表面色背景 + 蓝字 + 细描边。
- **Tonal**：浅填充背景 + 主文字色。
- **Text**：无背景，蓝色文字，用于低优先级动作。
- Disabled：降低不透明度，同时禁用交互；Loading：显示加载状态并避免重复提交。
- 点击区域至少达到 `44 × 44px`（图标视觉尺寸可小于点击区域）。

### 3.4 开关、分段控件和胶囊

- 开关视觉尺寸参考 `44 × 26px`；启用状态蓝色，关闭状态中性灰，滑块白色。
- 分段控件外层浅填充；选中项使用 `surface` 背景和很浅的投影。
- 胶囊用于状态或筛选；默认中性，只有选中或重要状态采用强调色。
- 标签尺寸随文本变化，避免为了统一宽度牺牲可读性。

### 3.5 输入栏与毛玻璃

- 适用于聊天输入、底部工具栏、浮动快捷操作条。
- 背景使用半透明 `glass` + `backdrop-filter: blur(30px)`，加细描边和柔和悬浮阴影。
- 圆角优先使用胶囊形；内容最小高度 `52px`。
- 不支持背景模糊时降级到不透明 `surface`，保证文字与图标可读。
- **不要全站滥用玻璃效果**；卡片和正文面板保持实色。

### 3.6 消息/AI 对话界面

- 用户消息使用强调色或柔和强调底；AI 回复使用中性浅灰底。
- 气泡圆角 20px，靠近发言侧的一角使用 6px 形成识别性。
- 工具调用、运行进度、附件预览使用独立弱强调模块，不与正文争抢视觉权重。
- 长文内容应支持 Markdown、代码块、复制、可访问的错误提示。

## 4. 动效和反馈

| 交互 | 时长 | 曲线 |
|---|---|---|
| 页面切换/出现 | `220ms` | `cubic-bezier(0,0,.2,1)` |
| 开关/状态切换 | `200ms` | `cubic-bezier(0,0,.2,1)` |
| Hover/Press 反馈 | `120–180ms`（Web 建议值） | `ease-out` |

- 动效优先作用于 `opacity`、`transform`，避免引起布局抖动。
- 不做持续的大面积漂浮、弹跳和花哨入场动画。
- 遵守 `prefers-reduced-motion: reduce`；此时去除非必要动画。
- 所有可交互项均应有 hover、active、focus-visible、disabled 等状态。

## 5. 响应式布局（Web 适配建议）

> 以下断点是 Web 实施建议，并非 Mika Android 源码中的硬性数值。

| 宽度 | 建议布局 |
|---|---|
| `<640px` | 单列，16px 页面边距；底部输入/操作栏固定或粘性 |
| `640–1023px` | 内容最大宽度约 760px，居中；侧栏改抽屉 |
| `≥1024px` | 侧栏 240–280px + 主内容；主内容最大宽度约 960px |

桌面可扩展顶栏至 60–64px，但视觉节奏仍与移动版一致。移动端须考虑软键盘、底部安全区及滚动穿透问题。

## 6. Web 实施基础 CSS

```css
:root {
  color-scheme: light;
  --bg: #f7f7f8;
  --surface: #fff;
  --fill: rgba(0, 0, 0, .04);
  --ink: #1c1c1e;
  --ink-2: #5f6068;
  --ink-3: #9a9ba3;
  --accent: #0a7cff;
  --accent-soft: rgba(10, 124, 255, .10);
  --edge: rgba(0, 0, 0, .05);
  --hair: rgba(0, 0, 0, .07);
  --glass: rgba(255, 255, 255, .72);
  --glass-edge: rgba(0, 0, 0, .06);
  --success: #1f9d55;
  --warning: #b86e00;
  --danger: #e5483d;
  --radius-card: 16px;
  --radius-button: 14px;
  --radius-bubble: 20px;
  --space-page: 16px;
  --ease-out: cubic-bezier(0, 0, .2, 1);
  --duration-page: 220ms;
  --duration-switch: 200ms;
}

[data-theme="dark"] {
  color-scheme: dark;
  --bg: #0e0e10;
  --surface: #1a1a1d;
  --fill: rgba(255, 255, 255, .06);
  --ink: #f2f2f4;
  --ink-2: #a4a5ad;
  --ink-3: #6c6d75;
  --accent: #3d97ff;
  --accent-soft: rgba(61, 151, 255, .16);
  --edge: rgba(255, 255, 255, .06);
  --hair: rgba(255, 255, 255, .08);
  --glass: rgba(26, 26, 29, .72);
  --glass-edge: rgba(255, 255, 255, .08);
  --success: #3ccf7a;
  --warning: #ffb340;
  --danger: #ff6961;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}

.ui-card {
  background: var(--surface);
  border: 1px solid var(--edge);
  border-radius: var(--radius-card);
  box-shadow: 0 2px 8px rgba(0, 0, 0, .035);
}
[data-theme="dark"] .ui-card { box-shadow: none; }

.ui-primary-button {
  min-height: 52px;
  padding: 0 20px;
  color: #fff;
  background: var(--accent);
  border: 0;
  border-radius: var(--radius-button);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 160ms ease-out, transform 160ms ease-out;
}
.ui-primary-button:active { transform: scale(.985); }
.ui-primary-button:disabled { opacity: .45; cursor: not-allowed; }

.ui-glass-bar {
  min-height: 52px;
  background: var(--glass);
  border: 1px solid var(--glass-edge);
  border-radius: 9999px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, .10);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
}

:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .ui-glass-bar { background: var(--surface); }
}
```

## 7. 工程约定

建议目录：

```text
src/
  design/
    tokens.css            # 颜色、间距、圆角、动效
    typography.css        # 字体层级
  components/
    ui/
      Card.*
      Button.*
      Switch.*
      SegmentedControl.*
      StatusPill.*
      GlassBar.*
      TopBar.*
  layouts/
    AppShell.*
```

1. 在基础组件里统一消化 token，业务页面只组合组件，不另造相似样式。
2. 采用语义类名/变量（如 `--surface`），而不是把颜色直接写在每个页面。
3. 明确区分“页面布局组件”“交互组件”和“状态提示组件”。
4. 浅色、深色、键盘焦点、缩小窗口、系统减少动画都须测试。
5. 避免强渐变、拟物质感、厚阴影、多色图标、无意义玻璃层以及密集小字。

## 8. 交付验收清单

- [ ] 页面背景、卡片、分组、边框符合设计 token。
- [ ] 深色模式不是简单反色，而是使用独立语义色值。
- [ ] 字号、圆角、间距遵循规定层级。
- [ ] 主要操作醒目，但整页仅少量使用强调蓝。
- [ ] 卡片和列表分组干净，没有多层容器堆叠。
- [ ] 玻璃效果只在悬浮场景使用，且具备降级方案。
- [ ] 按钮、开关、筛选、弹窗具有完整交互状态。
- [ ] 支持键盘操作、可见焦点、足够的触控尺寸。
- [ ] 低亮度和深色模式下保持可读性；正文对比度尽量符合 WCAG AA。
- [ ] 各尺寸下无溢出、输入栏遮挡、滚动穿透问题。
- [ ] 已适配 `prefers-reduced-motion`。

## 9. 参考源码

- [Colors.kt](https://github.com/wangzan101/Mika/blob/main/app/src/main/java/com/wangzan101/mika/ui/theme/Colors.kt)
- [Tokens.kt](https://github.com/wangzan101/Mika/blob/main/app/src/main/java/com/wangzan101/mika/ui/theme/Tokens.kt)
- [Theme.kt](https://github.com/wangzan101/Mika/blob/main/app/src/main/java/com/wangzan101/mika/ui/theme/Theme.kt)
- [Surfaces.kt](https://github.com/wangzan101/Mika/blob/main/app/src/main/java/com/wangzan101/mika/ui/components/Surfaces.kt)
- [Controls.kt](https://github.com/wangzan101/Mika/blob/main/app/src/main/java/com/wangzan101/mika/ui/components/Controls.kt)

> 说明：颜色、核心字号、尺寸、圆角与主要动画时长依据 Mika 源码；CSS 阴影、hover 时长、响应式断点、目录结构与 Web 无障碍约束为本规范给出的适配建议。
