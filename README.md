# FeishuFast · 飞书秒进

**点飞书图标 → 一秒内直接进入你指定的会话。** 跳过 Logo 开屏、消息列表和手动点击，飞书的初始化 / 消息同步照常在后台完成，不影响你打字和传文件。

> 这是一个 Xposed 模块（兼容 Vector / LSPosed），需要 root。

## 实测数据（小米 M2011K2C · 飞书 8.7.8）

| 路径 | 耗时 |
|---|---|
| 原生点图标（开屏 → 列表 → 手动点会话） | **8.2s+** 才能开始聊天 |
| 本模块 · 冷启动（进程被杀后首次打开） | **1.42s** 直达会话页 |
| 本模块 · 温启动（进程常驻时点图标） | **0.06 ~ 0.17s** 直达会话页 |

## 工作原理

模块没有去硬啃飞书的混淆类，而是利用飞书官方的 applink：

```
https://applink.feishu.cn/client/chat/open?openChatId=<会话ID>
```

这条链接本身就会绕过开屏 Activity 和消息列表。模块在 `MainActivity.onCreate` 时：

1. 用 Application Context 抢先发起这条 applink（不能直接 `startActivity`，飞书把 `MainActivity.startActivity()` 重写成了内部委托，onCreate 阶段委托还是 null，会 NPE）；
2. 立即结束开屏 Activity。

之后飞书自己的初始化、消息同步、数据库打开全部照常在后台跑完。

## 安装步骤

1. 下载 [最新 Release](../../releases) 里的 `FeishuFast.apk` 并安装。
2. 打开 Vector / LSPosed 管理器 → 启用「飞书秒进」模块。
3. 作用域只勾选 **飞书**（`com.ss.android.lark`）。
4. **强制停止飞书**，然后点桌面图标 —— 直接落到你配置的会话页。

> ⚠️ 模块更新后，必须强制停止飞书一次才会在新代码下重新注入。

## 配置会话 ID

打开「飞书秒进」app（安装后桌面就有）：

- **目标会话 ID**：在飞书里进入该会话 → 右上角 `…` → 拉到最底部 → 「会话 ID」，长按复制粘贴进来，点**保存**，重启飞书生效。留空则恢复默认。
- **模块状态开关**：一键启用 / 停用直达。
- **立即测试**：不重启飞书直接验证 applink 是否能打开目标会话。

配置文件实际存储在：

- `/sdcard/Download/fastfeishu_chatid.txt` — 会话 ID（一行）
- `/sdcard/Download/fastfeishu_off.txt` — 存在这个文件即表示模块停用

## 让每次都在 1 秒内

给飞书开启：

- MIUI「自启动」
- 后台锁定（最近任务下拉加锁）
- 省电策略 → 无限制

进程常驻后，点图标就是 100ms 级温启动。冷启动 1.4s 里的大头是 Android 创建进程 + 飞书 `Application.onCreate` 的初始化，Xposed 拦不住，这是物理下限。

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

## 从源码构建

不需要 Gradle / Android Studio，只要 JDK + build-tools（javac / d8 / aapt2 / apksigner / zipalign）：

```bash
python build.py
```

产出根目录 `FeishuFast.apk`，签名 keystore 为 `feishufast.jks`（仓库外保存，签名固定可覆盖安装）。

## 项目结构

```
├── AndroidManifest.xml        # Xposed 模块声明（scope: com.ss.android.lark）
├── assets/xposed_init         # Xposed 入口类声明
├── build.py                   # 一键构建脚本（javac + d8 + aapt2 + apksigner）
├── src/de/robv/android/xposed # Xposed API 编译桩（打包时剔除）
└── src/com/fastfeishu
    ├── HookMain.java          # 注入主逻辑：onCreate 抢先 redirect + 结束开屏
    └── SettingsActivity.java  # 卡片式设置页（纯代码布局）
```

## 许可

仅供个人效率用途，请遵守飞书用户协议。
