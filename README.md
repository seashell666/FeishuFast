# 闪电飞鸟 · 飞书秒进 / 扣子加速

**点飞书图标 → 一秒内直接进入你指定的会话；点扣子图标 → 跳过原生开屏，自动领每日免费积分。**

> 这是一个 Xposed 模块（兼容 Vector / LSPosed），需要 root。

## 实测数据（小米 M2011K2C · Android 13）

| 路径 | 耗时 |
|---|---|
| 飞书原生点图标（开屏 → 列表 → 手动点会话） | **8.2s+** 才能开始聊天 |
| 闪电飞鸟 · 飞书冷启动 | **1.42s** 直达会话页 |
| 闪电飞鸟 · 飞书温启动 | **0.06 ~ 0.17s** 直达会话页 |
| 扣子原生冷启动（开屏 → 白屏 → 聊天页） | **6.5s+** |
| 闪电飞鸟 · 扣子冷启动（进程被杀后首次） | **0.7~1.2s** 首帧骨架，**约 5s** 内容完整（最慢 6s） |
| 闪电飞鸟 · 扣子温启动（进程常驻） | **0.14 ~ 0.20s** 直接回聊天页 |

扣子冷启动的约 5 秒大头是 WebView 加载和 JS 初始化，模块拦不住；但已经把原生 1.5~2 秒开屏、白屏和 3 小时后整页重置全部去掉，并用同款聊天骨架把等待期盖住。日常只要进程常驻，点图标就是 0.15 秒级。

## 工作原理

模块没有去硬啃飞书的混淆类，而是利用飞书官方的 applink：

```
https://applink.feishu.cn/client/chat/open?openChatId=<会话ID>
```

这条链接本身就会绕过开屏 Activity 和消息列表。模块在 `MainActivity.onCreate` 时：

1. 用 Application Context 抢先发起这条 applink（不能直接 `startActivity`，飞书把 `MainActivity.startActivity()` 重写成了内部委托，onCreate 阶段委托还是 null，会 NPE）；
2. 立即结束开屏 Activity。

之后飞书自己的初始化、消息同步、数据库打开全部照常在后台跑完。

## 扣子加速原理

扣子（`com.coze.space`）是 Capacitor WebView 应用，冷启动链路拆开后是这样：

1. `com.coze.EntryActivity` 先解析引擎，默认要等原生就绪约 0.8~1 秒；
2. `com.coze.space.MainActivity` 再创建 Bridge / WebView；
3. 设备评分 ≤ 11 时，`x.z.c(Context)` 返回 1500~2000ms，原生开屏必须展示够这么久；
4. WebView 加载期间是纯白空屏；
5. 后台超过 3 小时，`x.a.b(Context)` 为 true，会用 `CLEAR_TASK` 整页重开，等于每次都要再走一遍冷启动。

模块对应处理：

- `EntryActivity.resolveAndLaunch` 直接拉起 `MainActivity`，省掉引擎解析等待；
- `x.z.c` / `x.z.a` 返回 0，`MainActivity.D()` 直接跳过，原生打字机开屏不再创建；
- `x.a.b` 恒 false、`x.a.h` 返回极大值，3 小时重置失效，WebView 现场一直保留；
- 窗口和 WebView 先铺一层暖白聊天骨架，页面里再注入同款 DOM 骨架，白屏阶段看到的是“正在加载的聊天页”；
- 页面加载完，先让「每日赠送」弹窗停留约 1 秒，再自动点掉「立即领取」，保留弹窗和飞金币动画作为过程提示；
- 积分本身是页面加载时官方接口自动到账的，「立即领取」不发请求，所以模块只做点击，不伪造接口。

## 安装步骤

1. 下载 [最新 Release](../../releases) 里的 `FeishuFast.apk` 并安装。
2. 打开 Vector / LSPosed 管理器 → 启用「闪电飞鸟」模块。
3. 作用域勾选 **飞书**（`com.ss.android.lark`）和 **扣子**（`com.coze.space`）。
4. 分别**强制停止**飞书、扣子一次，再点桌面图标。

> ⚠️ 模块更新后，必须强制停止飞书一次才会在新代码下重新注入。

## 配置会话 ID

打开「闪电飞鸟」app（安装后桌面就有）：

- **目标会话 ID**：在飞书里进入该会话 → 右上角 `…` → 拉到最底部 → 「会话 ID」，长按复制粘贴进来，点**保存**，重启飞书生效。留空则恢复默认。
- **模块状态开关**：一键启用 / 停用飞书直达。
- **扣子加速开关**：一键启用 / 停用扣子跳过开屏 + 自动领积分。
- **立即测试**：不重启飞书直接验证 applink 是否能打开目标会话。
- **让扣子常驻（推荐）**：需要 root，自动设置待机白名单、后台运行、`oom_score_adj`，让扣子进程常驻，点图标回到 0.15 秒级。

配置文件实际存储在：

- `/sdcard/Download/fastfeishu_chatid.txt` — 飞书会话 ID（一行）
- `/sdcard/Download/fastfeishu_off.txt` — 存在这个文件即表示飞书加速停用
- `/sdcard/Download/lightningbird_coze_speed_off.txt` — 存在即表示扣子加速停用
- `/sdcard/Download/lightningbird_claim_off.txt` — 存在即表示扣子自动领积分停用

## 让每次都在 1 秒内

给飞书开启：

- MIUI「自启动」
- 后台锁定（最近任务下拉加锁）
- 省电策略 → 无限制

给扣子：设置页点「让扣子常驻（推荐）」，或手动执行：

```bash
su -c 'am set-standby-bucket com.coze.space active; cmd deviceidle whitelist +com.coze.space; appops set com.coze.space RUN_IN_BACKGROUND allow; appops set com.coze.space RUN_ANY_IN_BACKGROUND allow'
```

进程常驻后，飞书是 100ms 级、扣子是 150ms 级温启动。冷启动的大头是 Android 创建进程 + WebView 初始化，Xposed 拦不住，这是物理下限。

## 常见问题

**Q：点图标没反应 / 还是进了消息列表？**
检查 Vector 里模块是否启用、作用域是否勾了飞书，然后强制停止飞书再试。看日志过滤 `FeishuFast` 能看到 `redirect -> oc_...`。

**Q：保存配置时提示写入被拒绝？**
部分系统不给 app 写 `/sdcard/Download`，用 root 手动写入：
```bash
su -c 'echo oc_你的会话ID > /sdcard/Download/fastfeishu_chatid.txt'
```

**Q：换飞书版本后失效？**
本模块只 hook `com.ss.android.lark.main.app.MainActivity` 的 `onCreate`，对飞书版本依赖很低；但飞书若改名 MainActivity 就需要适配。当前验证于飞书 8.7.8。

**Q：扣子还是会出现开屏文字？**
先确认 Vector 作用域勾了 `com.coze.space`，再强制停止扣子一次。看日志过滤 `LightningBird`，正常应出现 `hooked x.z.c -> 0`、`hooked MainActivity.D`、`hooked x.a.b -> no reset`。

**Q：扣子自动领积分会不会把弹窗藏掉？**
不会。弹窗和飞金币动画都会照常出现，模块只是在「立即领取」按钮出现后自动点一下。积分本身在页面加载请求时已到账，按钮不发请求。

**Q：扣子升级后失效？**
扣子这部分依赖混淆名 `x.z.c`、`x.a.b`、`MainActivity.D`，升级后可能改名；飞书部分不受影响。若升级后失效，重新抓包看新名字即可。

## 从源码构建

不需要 Gradle / Android Studio，只要 JDK + build-tools（javac / d8 / aapt2 / apksigner / zipalign）：

```bash
python build.py
```

产出根目录 `FeishuFast.apk`，签名 keystore 为 `feishufast.jks`（仓库外保存，签名固定可覆盖安装）。

## 项目结构

```
├── AndroidManifest.xml        # Xposed 模块声明（scope: com.ss.android.lark + com.coze.space）
├── assets/xposed_init         # Xposed 入口类声明
├── build.py                   # 一键构建脚本（javac + d8 + aapt2 + apksigner）
├── make_icon.py               # 图标生成：飞书矢量鸟换红色系（adaptive icon）
├── res/                       # 图标资源（mipmap + drawable 矢量前景 + xposedscope）
├── src/de/robv/android/xposed # Xposed API 编译桩（打包时剔除）
└── src/com/fastfeishu
    ├── HookMain.java          # 注入主逻辑：飞书 applink 直达 + 扣子开屏/重置/积分处理
    └── SettingsActivity.java  # 卡片式设置页（纯代码布局）
```

## 图标说明

模块图标 1:1 复用飞书官方启动器图标（自适应图标：纯白背景 + 矢量鸟前景），
仅把小鸟的三块填色换成红色系，明暗梯度与原图一致：

| 部位 | 飞书原色 | 本模块 |
|---|---|---|
| 翅膀（上叶） | `#00D6B9` 青 | `#D60700` 正红 |
| 身体（下叶） | `#3370FF` 亮蓝 | `#FF3341` 亮红 |
| 头部（重叠） | `#133C9A` 深藏蓝 | `#9A1317` 深红 |

## 更新日志

### v2.0
- 模块改名「闪电飞鸟」，图标保持不变
- 新增扣子（`com.coze.space`）加速：
  - 跳过原生 1.5~2 秒打字机开屏，`EntryActivity` 直接拉起 `MainActivity`
  - 阻止后台 3 小时整页重置，保住 WebView 现场
  - 冷启动白屏期铺聊天骨架，1.2 秒首帧不再纯白
  - 自动点「每日赠送 / 立即领取」，保留弹窗和飞金币动画
- 设置页新增「扣子加速」卡片和「让扣子常驻（推荐）」按钮
- 实测：扣子温启动 0.14~0.20s，冷启动 0.7~1.2s 首帧、约 5s 内容完整

### v1.2
- 🐦 模块图标复用飞书官方图标：白色圆角底 + 同款小鸟，鸟身改为红色系（亮红/正红/深红三色梯度，保持原明暗层次）
- 图标为 adaptive icon（Android 8+ 矢量前景 + 纯白背景），各密度 PNG 兜底
- `make_icon.py`：从飞书 APK 提取矢量鸟 pathData 并自动重映射色相，可复现图标生成

### v1.1
- 设置页全新卡片式 UI（淡紫主题 + 白色圆角卡片 + 彩色图标芯片 + 开关直达启停）
- 新增「真机实测」数据卡片与「启用步骤」引导

### v1.0
- 首个可用版本：点图标直达指定会话

## 许可

仅供个人效率用途，请遵守飞书用户协议。
