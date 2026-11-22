package com.fastfeishu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 模块设置页：卡片式淡紫主题，纯代码布局，不依赖任何资源文件。
 * 风格参考：日刻 app（白色大圆角卡片 + 淡紫背景 + 彩色圆形图标芯片 + 红色强调开关）。
 */
public class SettingsActivity extends Activity {

    private static final String CHAT = "/sdcard/Download/fastfeishu_chatid.txt";
    private static final String OFF = "/sdcard/Download/fastfeishu_off.txt";
    private static final String DEFAULT = "oc_859272d7df89ad17aa2b76e8e2733e00";

    // 主题色板
    private static final int C_BG = Color.parseColor("#F3F1FA");      // 页面淡紫底
    private static final int C_CARD = Color.parseColor("#FFFFFF");    // 卡片白
    private static final int C_TITLE = Color.parseColor("#1F1B2E");   // 主标题
    private static final int C_TEXT = Color.parseColor("#3B3747");    // 正文
    private static final int C_SUB = Color.parseColor("#8A86A0");     // 次要文字
    private static final int C_ACCENT = Color.parseColor("#E5484D");  // 强调红
    private static final int C_PURPLE = Color.parseColor("#7C5CE0");  // 芯片紫
    private static final int C_CHIP_PURPLE = Color.parseColor("#ECE5FB");
    private static final int C_CHIP_GREEN = Color.parseColor("#E2F3EB");
    private static final int C_GREEN = Color.parseColor("#2E9E6B");
    private static final int C_FIELD = Color.parseColor("#F4F2FB");   // 卡片内输入框底
    private static final int C_TRACK_OFF = Color.parseColor("#D9D6E3");

    private TextView statusSub;
    private Switch sw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(32), pad, dp(28));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ── 页头 ────────────────────────────────────────────────
        root.addView(overline("点击图标 · 一点直达"));
        TextView title = new TextView(this);
        title.setText("飞书秒进");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTextColor(C_TITLE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(4), 0, 0);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("跳过开屏与消息列表，直接进入指定会话");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(C_SUB);
        subtitle.setPadding(0, dp(6), 0, 0);
        root.addView(subtitle);

        // ── 状态卡片 ────────────────────────────────────────────
        LinearLayout statusCard = card(dp(18));
        LinearLayout sRow = row();
        sRow.addView(chip("✓", C_CHIP_GREEN, C_GREEN));
        LinearLayout sCol = col();
        sCol.addView(cardTitle("模块状态"));
        statusSub = new TextView(this);
        statusSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusSub.setTextColor(C_SUB);
        statusSub.setPadding(0, dp(2), 0, 0);
        sCol.addView(statusSub);
        LinearLayout.LayoutParams sColLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sColLp.leftMargin = dp(14);
        sRow.addView(sCol, sColLp);
        sw = new Switch(this);
        int[][] ss = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        sw.setTrackTintList(new ColorStateList(ss, new int[]{C_ACCENT, C_TRACK_OFF}));
        sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sw.setChecked(!new File(OFF).exists());
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                applyToggle(on);
            }
        });
        sRow.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        statusCard.addView(sRow);
        root.addView(statusCard, cardLp(dp(20)));

        // ── 目标会话卡片 ────────────────────────────────────────
        LinearLayout chatCard = card(dp(18));
        LinearLayout cRow = row();
        cRow.addView(chip("»", C_CHIP_PURPLE, C_PURPLE));
        LinearLayout cCol = col();
        cCol.addView(cardTitle("目标会话"));
        cCol.addView(cardSub("进入飞书后直接打开下面这个会话"));
        LinearLayout.LayoutParams cColLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cColLp.leftMargin = dp(14);
        cRow.addView(cCol, cColLp);
        chatCard.addView(cRow);

        final EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setHint("oc_ 开头的会话 ID");
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        edit.setTextColor(C_TEXT);
        edit.setHintTextColor(C_SUB);
        edit.setBackground(rounded(C_FIELD, dp(14)));
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setSingleLine(true);
        edit.setText(currentId());
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(-1, -2);
        editLp.topMargin = dp(14);
        chatCard.addView(edit, editLp);

        chatCard.addView(hint("获取方式：飞书 → 该会话 → 右上角 … → 拉到最底部「会话 ID」，长按复制。\n保存后需重启一次飞书生效；留空则恢复默认。", dp(10)));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(14), 0, 0);
        TextView save = pillBtn("保存", C_ACCENT, Color.WHITE);
        TextView test = pillBtn("立即测试", C_CHIP_PURPLE, C_PURPLE);
        btnRow.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(dp(12), -2);
        btnRow.addView(new View(this), midLp);
        btnRow.addView(test, new LinearLayout.LayoutParams(0, -2, 1f));
        chatCard.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(chatCard, cardLp(dp(14)));

        // ── 实测数据卡片 ────────────────────────────────────────
        LinearLayout statCard = card(dp(18));
        statCard.addView(cardTitle("真机实测 · 飞书 8.7.8"));
        statCard.addView(statsGrid());
        root.addView(statCard, cardLp(dp(14)));

        // ── 使用说明卡片 ────────────────────────────────────────
        LinearLayout helpCard = card(dp(18));
        helpCard.addView(cardTitle("启用步骤"));
        helpCard.addView(helpItem("1", "在 Vector / LSPosed 管理器中启用本模块"));
        helpCard.addView(helpItem("2", "作用域勾选「飞书」(com.ss.android.lark)"));
        helpCard.addView(helpItem("3", "强制停止飞书，再点桌面图标"));
        helpCard.addView(hint("想让每次都在 1 秒内：给飞书开「自启动 / 后台锁定 / 电池不优化」，"
                + "让进程常驻，点击就是 100ms 级温启动。冷启动 1.4s 是进程创建 + "
                + "飞书自身初始化的物理下限，任何模块都拦不住。", dp(12)));
        root.addView(helpCard, cardLp(dp(14)));

        // ── 页脚 ────────────────────────────────────────────────
        TextView footer = new TextView(this);
        footer.setText("飞书秒进 v1.1 · 需 root + Vector / LSPosed");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setTextColor(C_SUB);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);

        // ── 交互 ────────────────────────────────────────────────
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = edit.getText().toString().trim();
                if (writeFile(CHAT, id.length() > 8 ? id : "")) {
                    toast("已保存，重启飞书生效");
                } else {
                    toast("写入被系统拒绝，请用 root 执行：\nsu -c 'echo " + id
                            + " > /sdcard/Download/fastfeishu_chatid.txt'");
                }
                refreshStatus();
            }
        });

        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://applink.feishu.cn/client/chat/open?openChatId="
                                    + currentId()));
                    i.setPackage("com.ss.android.lark");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) {
                    toast("跳转失败：" + t);
                }
            }
        });

        refreshStatus();
        setContentView(sv);
    }

    private void applyToggle(boolean on) {
        File f = new File(OFF);
        if (on) {
            if (f.exists() && !f.delete()) {
                toast("删除失败，请手动删除 " + OFF);
                sw.setChecked(false);
                return;
            }
            toast("已启用，重启飞书生效");
        } else {
            if (!writeFile(OFF, "off")) {
                toast("写入失败，请手动创建 " + OFF);
                sw.setChecked(true);
                return;
            }
            toast("已停用，重启飞书生效");
        }
        refreshStatus();
    }

    private void refreshStatus() {
        boolean on = !new File(OFF).exists();
        statusSub.setText(on
                ? "已启用 · 点飞书图标即直达会话页"
                : "已停用 · 点击开关重新开启");
        sw.setChecked(on);
    }

    /** 三列数据：原生 / 冷启动 / 温启动 */
    private View statsGrid() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        g.setPadding(0, dp(10), 0, 0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
        g.addView(statCell("8.2s+", "原生路径", C_SUB), clp);
        g.addView(statCell("1.42s", "冷启动直达", C_PURPLE), clp);
        g.addView(statCell("0.06s", "温启动直达", C_GREEN), clp);
        return g;
    }

    private View statCell(String big, String label, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView b = new TextView(this);
        b.setText(big);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setTextColor(color);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        c.addView(b);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        l.setTextColor(C_SUB);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(2), 0, 0);
        c.addView(l);
        return c;
    }

    private View helpItem(String num, String text) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, 0);
        TextView n = new TextView(this);
        n.setText(num);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        n.setTextColor(C_PURPLE);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setGravity(Gravity.CENTER);
        n.setBackground(oval(C_CHIP_PURPLE));
        r.addView(n, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(C_TEXT);
        t.setPadding(dp(12), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    // ── 通用控件 ────────────────────────────────────────────────

    private LinearLayout card(int padding) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(padding, padding, padding, padding);
        c.setBackground(rounded(C_CARD, dp(24)));
        return c;
    }

    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = top;
        return lp;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    /** 圆形图标芯片 */
    private View chip(String glyph, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(oval(bg));
        return t;
    }

    private TextView cardTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(C_TITLE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView cardSub(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(C_SUB);
        t.setPadding(0, dp(2), 0, 0);
        return t;
    }

    private TextView overline(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(C_SUB);
        return t;
    }

    private TextView hint(String s, int top) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTextColor(C_SUB);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, top, 0, 0);
        return t;
    }

    /** 药丸按钮，带 ripple */
    private TextView pillBtn(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(13), 0, dp(13));
        GradientDrawable gd = rounded(bg, dp(16));
        t.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33000000), gd, gd));
        t.setClickable(true);
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    // ── 业务逻辑 ────────────────────────────────────────────────

    private String currentId() {
        java.io.BufferedReader r = null;
        try {
            File f = new File(CHAT);
            if (f.isFile()) {
                r = new java.io.BufferedReader(new java.io.FileReader(f));
                String s = r.readLine();
                if (s != null && s.trim().length() > 8) return s.trim();
            }
        } catch (Throwable ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) {}
        }
        return DEFAULT;
    }

    private static boolean writeFile(String path, String content) {
        FileOutputStream o = null;
        try {
            o = new FileOutputStream(path);
            o.write(content.getBytes("UTF-8"));
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (o != null) try { o.close(); } catch (Throwable ignored) {}
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
