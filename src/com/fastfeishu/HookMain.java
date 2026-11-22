package com.fastfeishu;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 飞书秒进 (Vector / LSPosed / EdXposed, Xposed API 82+)
 *
 * 原理（真机 8.7.8 实测）：
 *   飞书自己的 applink「https://applink.feishu.cn/client/chat/open?openChatId=xxx」
 *   会绕过开屏页和消息列表，直接把 ChatWindowActivity 摆到前台。
 *   冷启动 1423ms / 温启动 62ms；原生点图标路径要 8s+ 才到列表、再手动点会话。
 *
 * 本模块在飞书启动 Activity 的 onCreate 里：
 *   - before: 立刻发起 applink（会话页并行开始创建，输入区可用）
 *   - after : 结束启动 Activity（开屏/列表不再渲染），让会话页立即接管前台
 *   飞书自身的初始化、消息同步、数据库打开照常在后台跑完，不阻塞会话输入。
 */
public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "FeishuFast";
    private static final String PKG = "com.ss.android.lark";
    private static final String LAUNCHER = "com.ss.android.lark.main.app.MainActivity";
    private static final String DEFAULT_CHAT_ID = "oc_859272d7df89ad17aa2b76e8e2733e00";
    private static final String CONF_CHAT = "/sdcard/Download/fastfeishu_chatid.txt";
    private static final String CONF_OFF = "/sdcard/Download/fastfeishu_off.txt";
    private static final String APPLINK =
            "https://applink.feishu.cn/client/chat/open?openChatId=";

    private static volatile long sLastRedirect = 0L;
    private static volatile boolean sPendingFinish = false;

    @Override
    public void handleLoadPackage(final LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;

        XposedBridge.log(TAG + ": loaded in process " + lp.processName);

        final XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity a = (Activity) param.thisObject;
                    if (!shouldRedirect(a)) return;

                    Intent jump = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(APPLINK + readChatId()));
                    jump.setPackage(PKG);
                    jump.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startChat(a, jump);

                    sLastRedirect = System.currentTimeMillis();
                    sPendingFinish = true;
                    XposedBridge.log(TAG + ": redirect -> " + readChatId());
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": before failed: " + t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!sPendingFinish) return;
                sPendingFinish = false;
                try {
                    final Activity a = (Activity) param.thisObject;
                    a.finish();
                    a.overridePendingTransition(0, 0);
                    XposedBridge.log(TAG + ": launcher finished");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": after failed: " + t);
                }
            }
        };

        // 主路径：hook 飞书自己的启动 Activity（作用域内，最稳）
        try {
            XposedHelpers.findAndHookMethod(LAUNCHER, lp.classLoader,
                    "onCreate", Bundle.class, hook);
            XposedBridge.log(TAG + ": hooked " + LAUNCHER);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": primary hook failed: " + t);
        }

        // 兜底：某些版本/二开包改了启动类名，动态识别 launcher
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Activity a = (Activity) param.thisObject;
                                if (!LAUNCHER.equals(a.getClass().getName())
                                        && isLauncherActivity(a) && shouldRedirect(a)) {
                                    Intent jump = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(APPLINK + readChatId()));
                                    jump.setPackage(PKG);
                                    jump.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startChat(a, jump);
                                    sLastRedirect = System.currentTimeMillis();
                                    sPendingFinish = true;
                                    XposedBridge.log(TAG + ": fallback redirect on "
                                            + a.getClass().getName());
                                }
                            } catch (Throwable ignored) {
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!sPendingFinish) return;
                            sPendingFinish = false;
                            try {
                                Activity a = (Activity) param.thisObject;
                                a.finish();
                                a.overridePendingTransition(0, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": fallback hook unavailable: " + t);
        }
    }

    /** 只处理桌面图标点进来的那一次，避免通知 / 分享 / 返回 / applink 回退被误伤 */
    private static boolean shouldRedirect(Activity a) {
        if (new File(CONF_OFF).exists()) return false;           // 总开关
        if (System.currentTimeMillis() - sLastRedirect < 1500L) return false; // 防重复
        Intent in = a.getIntent();
        if (in == null) return false;
        if (!Intent.ACTION_MAIN.equals(in.getAction())) return false;
        if (!in.hasCategory(Intent.CATEGORY_LAUNCHER)) return false;
        return true;
    }

    /**
     * 飞书把 MainActivity.startActivity() 重写成内部委托，onCreate 阶段该委托还是 null（实测 NPE）。
     * 用 Application Context 启动，走系统 ContextImpl 路径，绕开飞书的覆写。
     */
    private static void startChat(Activity a, Intent jump) {
        try {
            a.getApplicationContext().startActivity(jump);
            XposedBridge.log(TAG + ": started via app context");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": app ctx failed (" + t + "), fallback to activity");
            a.startActivity(jump);
        }
    }

    private static boolean isLauncherActivity(Activity a) {        try {
            PackageManager pm = a.getPackageManager();
            Intent li = pm.getLaunchIntentForPackage(PKG);
            if (li == null) return false;
            ComponentName cn = li.getComponent();
            return cn != null && cn.getClassName().equals(a.getClass().getName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 会话 ID 可用文件覆盖；无文件则用已抓到的默认 ID */
    private static String readChatId() {
        try {
            File f = new File(CONF_CHAT);
            if (f.isFile()) {
                BufferedReader r = new BufferedReader(new FileReader(f));
                String s = r.readLine();
                r.close();
                if (s != null) {
                    s = s.trim();
                    if (s.length() > 8) return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_CHAT_ID;
    }
}
