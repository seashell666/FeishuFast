package com.fastfeishu;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 闪电飞鸟（原飞书秒进）— Vector / LSPosed / EdXposed，Xposed API 82+
 *
 * 飞书：
 *   点图标直接用 applink 打开指定会话，跳过开屏和消息列表。
 *
 * 扣子（com.coze.space）：
 *   1. 跳过开屏强制展示：EntryActivity / MainActivity 的开屏文字动画由
 *      x.z.c() 按设备评分返回 1500~2000ms，这里直接改为 0，并跳过 MainActivity.D()。
 *   2. 阻止后台整页重置：x.a.b() 在后台超过 3 小时会 CLEAR_TASK 重开，
 *      这里恒返回 false，保住 WebView 现场，再点就是 100ms 级温启动。
 *   3. WebView 文档启动脚本：监听“立即领取”按钮并自动点击，
 *      保留“每日赠送”弹窗和飞金币动画，用户能看到整个过程。
 *
 * 官方每日积分本来就是页面加载时 POST /api/marketplace/incentive/activity/reward
 * 自动到账的，“立即领取”只是展示动画；模块只负责让它自动完成，不伪造请求。
 */
public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "LightningBird";

    private static final String PKG_LARK = "com.ss.android.lark";
    private static final String LAUNCHER = "com.ss.android.lark.main.app.MainActivity";
    private static final String DEFAULT_CHAT_ID = "oc_859272d7df89ad17aa2b76e8e2733e00";
    private static final String CONF_CHAT = "/sdcard/Download/fastfeishu_chatid.txt";
    private static final String CONF_OFF = "/sdcard/Download/fastfeishu_off.txt";
    private static final String APPLINK =
            "https://applink.feishu.cn/client/chat/open?openChatId=";

    private static final String PKG_COZE = "com.coze.space";
    private static final String COZE_SPEED_OFF = "/sdcard/Download/lightningbird_coze_speed_off.txt";
    private static final String COZE_CLAIM_OFF = "/sdcard/Download/lightningbird_claim_off.txt";
    private static final String COZE_CLAIM_TEST = "/sdcard/Download/lightningbird_claim_test.txt";
    private static final int COZE_WARM_BG = 0xFFF4F2F0;

    private static volatile long sLastRedirect = 0L;
    private static volatile boolean sPendingFinish = false;

    /** 扣子扣积分按钮自动点击脚本：只在“每日/积分/赠送”弹窗里点，保留弹窗本体 */
    private static final String COZE_CLAIM_SCRIPT =
            "(function(){"
            + "if(window.__lbClaim){return;}window.__lbClaim=1;"
            + "function lbSkeleton(){try{"
            + "if(document.getElementById('lbSkeleton'))return;"
            + "if(document.getElementById('lbSkeleton'))return;"
            + "var d=document.createElement('div');d.id='lbSkeleton';"
            + "d.style.cssText='position:fixed;left:0;top:0;right:0;bottom:0;background:#F4F2F0;z-index:2147483647;pointer-events:none;overflow:hidden';"
            + "d.innerHTML='<div style=\"position:absolute;left:24px;top:34px;width:13px;height:13px;border-left:2px solid #2B2B2B;border-bottom:2px solid #2B2B2B;transform:rotate(45deg)\"></div>'"
            + "+'<div style=\"position:absolute;left:50%;top:20px;width:80px;height:12px;margin-left:-40px;border-radius:4px;background:#E8E5E1\"></div>'"
            + "+'<div style=\"position:absolute;left:50%;top:46px;width:52px;height:8px;margin-left:-26px;border-radius:4px;background:#EFECE9\"></div>'"
            + "+'<div style=\"position:absolute;left:24px;top:88px;width:20px;height:20px;border-radius:50%;background:#EFECE9\"></div>'"
            + "+'<div style=\"position:absolute;left:60px;top:90px;width:160px;height:18px;border-radius:5px;background:#E8E5E1\"></div>'"
            + "+'<div style=\"position:absolute;left:60px;top:118px;width:260px;height:18px;border-radius:5px;background:#E8E5E1\"></div>'"
            + "+'<div style=\"position:absolute;left:60px;top:146px;width:190px;height:18px;border-radius:5px;background:#E8E5E1\"></div>'"
            + "+'<div style=\"position:absolute;left:24px;top:190px;width:20px;height:20px;border-radius:50%;background:#EFECE9\"></div>'"
            + "+'<div style=\"position:absolute;left:60px;top:192px;width:150px;height:18px;border-radius:5px;background:#E8E5E1\"></div>'"
            + "+'<div style=\"position:absolute;left:14px;right:14px;bottom:26px;height:52px;border-radius:26px;background:#FFFEFC\"></div>'"
            + "+'<div style=\"position:absolute;left:26px;bottom:40px;width:28px;height:28px;border-radius:50%;background:#EFECE9\"></div>'"
            + "+'<div style=\"position:absolute;left:62px;bottom:49px;width:150px;height:10px;border-radius:4px;background:#EFECE9\"></div>';"
            + "document.documentElement.appendChild(d);"
            + "}catch(e){}}"
            + "lbSkeleton();"
            + "var lbTimer=setInterval(function(){try{"
            + "if(performance.now()>5200||document.querySelector('[role=\"dialog\"]')){"
            + "var s=document.getElementById('lbSkeleton');if(s)s.remove();clearInterval(lbTimer);}"
            + "}catch(e){}},250);"
            + "var clicked=[];"
            + "function vis(el){try{var r=el.getBoundingClientRect();return r.width>8&&r.height>8;}catch(e){return false;}}"
            + "function dlgOf(el){var p=el;while(p&&p!==document.body){try{if(p.getAttribute&&(p.getAttribute('role')==='dialog'||p.getAttribute('aria-modal')==='true'))return p;}catch(e){}p=p.parentElement;}return null;}"
            + "function scan(){try{"
            + "var list=document.querySelectorAll('button,[role=\"button\"]');"
            + "for(var i=0;i<list.length;i++){var el=list[i];var t=(el.textContent||'').replace(/\\s+/g,'');"
            + "if(t!=='立即领取'&&t!=='立即領取')continue;"
            + "if(!vis(el)||clicked.indexOf(el)>=0)continue;"
            + "var d=dlgOf(el);var ctx=d?d.textContent:(el.parentElement?el.parentElement.textContent:'');"
            + "if(!(ctx&&(ctx.indexOf('每日')>=0||ctx.indexOf('积分')>=0||ctx.indexOf('赠送')>=0)))continue;"
            + "clicked.push(el);console.log('[LightningBird] claim found');"
            + "setTimeout(function(){try{el.click();console.log('[LightningBird] claim clicked');}catch(e){}},1000);"
            + "}}catch(e){}}"
            + "setInterval(scan,250);"
            + "document.addEventListener('DOMContentLoaded',scan,false);"
            + "console.log('[LightningBird] observer ready');"
            + "})();";

    /** 自测：塞一个假“每日赠送/立即领取”弹窗，验证自动点击是否生效（只读 /sdcard 标记文件时启用） */
    private static final String COZE_FAKE_DIALOG_SCRIPT =
            "(function(){try{"
            + "if(window.__lbFake){return;}window.__lbFake=1;"
            + "var d=document.createElement('div');d.setAttribute('role','dialog');"
            + "d.style.cssText='position:fixed;left:18px;top:180px;width:300px;height:190px;background:#fff;border-radius:16px;z-index:99999;padding:18px;box-shadow:0 8px 30px rgba(0,0,0,.18)';"
            + "d.innerHTML='<div style=\"font-size:18px;font-weight:600;margin-bottom:12px\">每日赠送</div><div style=\"color:#666;margin-bottom:22px\">每日登录可得 10 积分</div><button id=\"lbFakeBtn\" style=\"width:100%;height:44px;border:0;border-radius:22px;background:#7c5cff;color:#fff;font-size:16px\">立即领取</button>';"
            + "var b=d.querySelector('#lbFakeBtn');b.addEventListener('click',function(){window.__lbTestClicked=1;d.remove();});"
            + "document.body.appendChild(d);console.log('[LightningBird] fake dialog added');"
            + "}catch(e){console.log('[LightningBird] fake dialog error '+e);}})();";

    private static final String COZE_PROBE_SCRIPT =
            "(function(){try{var t=[];var bs=document.querySelectorAll('button');"
            + "for(var i=0;i<bs.length&&i<8;i++){t.push((bs[i].textContent||'').replace(/\\s+/g,''));}"
            + "return 'claim='+String(window.__lbClaim)+' fake='+String(window.__lbFake||0)"
            + "+' clicked='+String(window.__lbTestClicked||0)+' buttons='+bs.length+' texts='+t.join('|');"
            + "}catch(e){return 'probe error '+e;}})();";

    @Override
    public void handleLoadPackage(final LoadPackageParam lp) throws Throwable {
        if (PKG_LARK.equals(lp.packageName)) {
            hookFeishu(lp);
            return;
        }
        if (PKG_COZE.equals(lp.packageName)) {
            if (lp.processName == null || PKG_COZE.equals(lp.processName)) {
                hookCoze(lp);
            }
        }
    }

    // ───────────────────────────── 飞书 ─────────────────────────────

    private static void hookFeishu(final LoadPackageParam lp) {
        XposedBridge.log(TAG + ": loaded in 飞书 " + lp.processName);

        final XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final Activity a = (Activity) param.thisObject;
                    if (!shouldRedirect(a)) return;

                    Intent jump = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(APPLINK + readChatId()));
                    jump.setPackage(PKG_LARK);
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

        try {
            XposedHelpers.findAndHookMethod(LAUNCHER, lp.classLoader,
                    "onCreate", Bundle.class, hook);
            XposedBridge.log(TAG + ": hooked " + LAUNCHER);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": primary hook failed: " + t);
        }

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
                                    jump.setPackage(PKG_LARK);
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

    private static boolean shouldRedirect(Activity a) {
        if (new File(CONF_OFF).exists()) return false;
        if (System.currentTimeMillis() - sLastRedirect < 1500L) return false;
        Intent in = a.getIntent();
        if (in == null) return false;
        if (!Intent.ACTION_MAIN.equals(in.getAction())) return false;
        if (!in.hasCategory(Intent.CATEGORY_LAUNCHER)) return false;
        return true;
    }

    private static void startChat(Activity a, Intent jump) {
        try {
            a.getApplicationContext().startActivity(jump);
            XposedBridge.log(TAG + ": started via app context");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": app ctx failed (" + t + "), fallback to activity");
            a.startActivity(jump);
        }
    }

    private static boolean isLauncherActivity(Activity a) {
        try {
            PackageManager pm = a.getPackageManager();
            Intent li = pm.getLaunchIntentForPackage(PKG_LARK);
            if (li == null) return false;
            ComponentName cn = li.getComponent();
            return cn != null && cn.getClassName().equals(a.getClass().getName());
        } catch (Throwable t) {
            return false;
        }
    }

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

    // ───────────────────────────── 扣子 ─────────────────────────────

    private static void hookCoze(LoadPackageParam lp) {
        final ClassLoader cl = lp.classLoader;
        final boolean speed = !new File(COZE_SPEED_OFF).exists();
        final boolean claim = !new File(COZE_CLAIM_OFF).exists();

        Log.i(TAG, "loaded in coze, speed=" + speed + " claim=" + claim);
        XposedBridge.log(TAG + ": loaded in 扣子, speed=" + speed + " claim=" + claim);

        if (speed) {
            hookCozeSplash(cl);
            hookCozeAppStateReset(cl);
            hookCozeBackground(cl);
            hookCozeEntryDirect(cl);
        }
        hookCozeWebView(cl, claim);
    }

    /** 开屏最短展示：设备评分 <=11 时官方强制 1.5~2 秒文字动画，这里归零 */
    private static void hookCozeSplash(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("x.z", cl, "c", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(0L);
                        }
                    });
            Log.i(TAG, "hooked x.z.c -> 0");
        } catch (Throwable t) {
            Log.w(TAG, "x.z.c hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod("x.z", cl, "a", float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(0L);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "x.z.a hook failed: " + t);
        }

        // 双保险：MainActivity 自己也会起一层 NativeTypewriter，直接跳过
        try {
            XposedHelpers.findAndHookMethod("com.coze.space.MainActivity", cl, "D",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
            Log.i(TAG, "hooked MainActivity.D -> skip splash");
        } catch (Throwable t) {
            Log.w(TAG, "MainActivity.D hook failed: " + t);
        }
    }

    /**
     * 入口页直接起主页面，省掉 KMP 引擎解析 + 原生就绪等待（实测约 0.8~1 秒）。
     * 只处理普通图标点击；带 data / embedded history 的深链保持官方流程。
     */
    private static void hookCozeEntryDirect(final ClassLoader cl) {
        try {
            final Class<?> mainCls = XposedHelpers.findClass("com.coze.space.MainActivity", cl);
            XposedHelpers.findAndHookMethod("com.coze.EntryActivity", cl, "resolveAndLaunch", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity entry = (Activity) param.thisObject;
                            Intent src = entry.getIntent();
                            if (src != null && (src.getData() != null
                                    || src.getBooleanExtra("coze.open_embedded_history", false))) {
                                return;
                            }
                            try {
                                Intent i = new Intent(src != null ? src : new Intent());
                                i.setClass(entry, mainCls);
                                entry.startActivity(i);
                                entry.overridePendingTransition(0, 0);
                                entry.finish();
                                param.setResult(null);
                                Log.i(TAG, "EntryActivity -> MainActivity direct");
                            } catch (Throwable t) {
                                Log.w(TAG, "direct launch failed: " + t);
                            }
                        }
                    });
            Log.i(TAG, "hooked EntryActivity.resolveAndLaunch -> direct MainActivity");
        } catch (Throwable t) {
            Log.w(TAG, "EntryActivity direct hook failed: " + t);
        }
    }


    /** 后台超过 3 小时会 CLEAR_TASK 整页重开；恒 false 保住 WebView 现场 */
    private static void hookCozeAppStateReset(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("x.a", cl, "b", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    });
            Log.i(TAG, "hooked x.a.b -> no reset");
        } catch (Throwable t) {
            Log.w(TAG, "x.a.b hook failed: " + t);
        }

        // 再兜一层：把阈值改成极大，任何漏网路径也不会触发
        try {
            XposedHelpers.findAndHookMethod("x.a", cl, "h",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(Long.MAX_VALUE);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "x.a.h hook failed: " + t);
        }
    }

    /** 把 WebView 透明露出的纯白窗口改成开屏同款暖白 #F4F2F0 */
    private static void hookCozeBackground(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.coze.space.MainActivity", cl, "z",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            paintWarm((Activity) param.thisObject);
                        }
                    });
            Log.i(TAG, "hooked MainActivity.z -> warm background");
        } catch (Throwable t) {
            Log.w(TAG, "MainActivity.z hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod("com.coze.space.MainActivity", cl, "onCreate",
                    Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            paintWarm((Activity) param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "MainActivity.onCreate hook failed: " + t);
        }
    }

    private static void paintWarm(Activity a) {
        float density = a.getResources().getDisplayMetrics().density;
        try {
            if (a.getWindow() != null) {
                a.getWindow().setBackgroundDrawable(new SkeletonDrawable(density));
            }
        } catch (Throwable ignored) {
        }
        try {
            Object bridge = XposedHelpers.callMethod(a, "getBridge");
            if (bridge != null) {
                WebView wv = (WebView) XposedHelpers.callMethod(bridge, "getWebView");
                // WebView 本身也铺一层聊天骨架：页面白底未画出来之前先显示骨架，画出来后被盖住
                if (wv != null) wv.setBackgroundDrawable(new SkeletonDrawable(density));
            }
        } catch (Throwable ignored) {
        }
        // 视图树最底下几层容器设成骨架/透明，避免主题白底把骨架盖住
        try {
            View content = a.findViewById(android.R.id.content);
            View v = content;
            for (int depth = 0; v != null && depth < 4; depth++) {
                if (depth == 1) {
                    v.setBackgroundDrawable(new SkeletonDrawable(density));
                } else {
                    v.setBackgroundColor(0);
                }
                if (!(v instanceof ViewGroup) || ((ViewGroup) v).getChildCount() == 0) break;
                v = ((ViewGroup) v).getChildAt(0);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 冷启动 1~3 秒的占位骨架，避免纯白空屏；尺寸按屏幕密度缩放 */
    private static final class SkeletonDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        SkeletonDrawable(float density) {
            this.d = density <= 0 ? 2.75f : density;
        }

        private void bar(Canvas c, float l, float t, float w, float h, int color) {
            fill.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(l * d, t * d, (l + w) * d, (t + h) * d), 4 * d, 4 * d, fill);
        }

        @Override
        public void draw(Canvas c) {
            c.drawColor(COZE_WARM_BG);
            int block = 0xFFE8E5E1;
            int block2 = 0xFFEFECE9;

            // 返回箭头
            stroke.setColor(0xFF2B2B2B);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.2f * d);
            float cx = 30 * d, cy = 44 * d;
            c.drawLine(cx + 4 * d, cy - 7 * d, cx - 3 * d, cy, stroke);
            c.drawLine(cx - 3 * d, cy, cx + 4 * d, cy + 7 * d, stroke);

            // 标题 / 副标题
            bar(c, 166, 30, 80, 13, block);
            bar(c, 180, 55, 52, 8, block2);

            // 左侧对话骨架
            fill.setColor(block2);
            c.drawCircle(34 * d, 100 * d, 10 * d, fill);
            bar(c, 60, 92, 160, 18, block);
            bar(c, 60, 120, 260, 18, block);
            bar(c, 60, 148, 190, 18, block);
            c.drawCircle(34 * d, 202 * d, 10 * d, fill);
            bar(c, 60, 194, 150, 18, block);

            // 底部输入栏
            float bottom = getBounds().height() / d;
            fill.setColor(0xFFFFFEFC);
            c.drawRoundRect(new RectF(14 * d, (bottom - 82) * d, (412 - 14) * d, (bottom - 30) * d), 22 * d, 22 * d, fill);
            fill.setColor(block2);
            c.drawCircle(38 * d, (bottom - 56) * d, 14 * d, fill);
            bar(c, 62, bottom - 64, 150, 10, block2);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /**
     * 拿到 Capacitor 的 WebView：
     *   - 首选 Bridge 构造完成后用 androidx.webkit 的 document-start 脚本（页面 JS 之前执行）
     *   - 兜底在 loadUrl 之后循环 evaluateJavascript，脚本自带幂等 guard
     */
    private static void hookCozeWebView(final ClassLoader cl, final boolean claim) {
        if (!claim) {
            Log.i(TAG, "claim disabled by file");
            return;
        }

        try {
            Class<?> bridgeCls = XposedHelpers.findClass("com.getcapacitor.Bridge", cl);
            XposedBridge.hookAllConstructors(bridgeCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object bridge = param.thisObject;
                        Object wvObj = XposedHelpers.getObjectField(bridge, "webView");
                        if (wvObj instanceof WebView) {
                            injectClaim((WebView) wvObj, cl);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Bridge constructor inject failed: " + t);
                    }
                }
            });
            Log.i(TAG, "hooked Bridge constructors");
        } catch (Throwable t) {
            Log.w(TAG, "Bridge hook failed: " + t);
        }

        // 兜底：任何 WebView 打开 localhost 后，循环注入（最多 8 秒）
        try {
            Class<?> wvCls = XposedHelpers.findClass("android.webkit.WebView", cl);
            XposedBridge.hookAllMethods(wvCls, "loadUrl", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        WebView wv = (WebView) param.thisObject;
                        Object url = param.args != null && param.args.length > 0 ? param.args[0] : null;
                        if (url instanceof String && ((String) url).contains("localhost")) {
                            injectClaim(wv, cl);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "loadUrl inject failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "WebView.loadUrl hook failed: " + t);
        }
    }

    private static void injectClaim(final WebView wv, final ClassLoader cl) {
        // 1) 文档启动脚本：新文档一创建就装上观察器
        try {
            Class<?> wvc = Class.forName("androidx.webkit.WebViewCompat", false, cl);
            Method m = wvc.getMethod("addDocumentStartJavaScript",
                    WebView.class, String.class, Set.class);
            m.invoke(null, wv, COZE_CLAIM_SCRIPT, Collections.singleton("*"));
            Log.i(TAG, "document-start claim script installed");
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            Log.w(TAG, "document-start unavailable, fallback polling: " + c);
        }

        // 2) 再补几次：覆盖热重载/路由重启，脚本本身有 __lbClaim 幂等 guard
        for (int i = 0; i < 12; i++) {
            final int delay = 300 + i * 400;
            try {
                wv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wv.evaluateJavascript(COZE_CLAIM_SCRIPT, null);
                        } catch (Throwable ignored) {
                        }
                    }
                }, delay);
            } catch (Throwable ignored) {
            }
        }

        // 3) 8 秒后探针：确认脚本真的在页面里跑起来了
        try {
            wv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        wv.evaluateJavascript(COZE_PROBE_SCRIPT, new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                Log.i(TAG, "claim probe: " + value);
                            }
                        });
                    } catch (Throwable t) {
                        Log.w(TAG, "claim probe failed: " + t);
                    }
                }
            }, 8000);
        } catch (Throwable ignored) {
        }

        // 4) 自测模式：塞假弹窗，3 秒后看 clicked 是否变成 1
        if (new File(COZE_CLAIM_TEST).exists()) {
            try {
                wv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wv.evaluateJavascript(COZE_FAKE_DIALOG_SCRIPT, null);
                        } catch (Throwable ignored) {
                        }
                    }
                }, 2500);
                wv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wv.evaluateJavascript(COZE_PROBE_SCRIPT, new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    Log.i(TAG, "claim test probe: " + value);
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    }
                }, 5000);
            } catch (Throwable ignored) {
            }
        }
    }
}
