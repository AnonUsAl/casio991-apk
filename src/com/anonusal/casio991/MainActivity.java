package com.anonusal.casio991;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private LocalServer server;
    private View splash;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long splashStart = 0;
    private boolean splashDismissed = false;
    private static final long SPLASH_MIN_MS = 1200;   // 开屏最短展示时间
    private static final long SPLASH_TIMEOUT_MS = 6000; // 加载卡住时的兜底

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStart = System.currentTimeMillis();

        // 根容器：WebView 在底层，启动画面叠在上层
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0b0d")); // 配合黑色主题
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 启动画面：黑底居中显示 AnonUsAl
        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage 保存计算器记忆
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // 只允许本地资源；外部链接交给系统浏览器
                if (url.startsWith("http://127.0.0.1:") || url.startsWith("about:")) return false;
                try {
                    android.content.Intent i = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    startActivity(i);
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // 页面（含 WASM 初始化）就绪后再收起开屏，保证至少展示最短时间
                long elapsed = System.currentTimeMillis() - splashStart;
                long remain = Math.max(0, SPLASH_MIN_MS - elapsed);
                handler.postDelayed(() -> dismissSplash(), remain);
            }
        });

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        try {
            File site = new File(getFilesDir(), "site");
            copyAssets("site", site);
            server = new LocalServer(site);
            server.start();
            web.loadUrl("http://127.0.0.1:" + server.port() + "/index.html");
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // 兜底：即使页面加载卡住，超时后也强制收起开屏
        handler.postDelayed(() -> dismissSplash(), SPLASH_TIMEOUT_MS);
    }

    /** 构造启动画面视图 */
    private View buildSplash() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#0b0b0d"));

        TextView name = new TextView(this);
        name.setText("AnonUsAl");
        name.setTextColor(Color.parseColor("#ff6a2b"));
        name.setTextSize(30);
        name.setLetterSpacing(0.12f);
        name.setGravity(Gravity.CENTER);
        box.addView(name);

        TextView credits = new TextView(this);
        credits.setText("AnonUsAl · 屿99捏");
        credits.setTextColor(Color.parseColor("#edeae3"));
        credits.setTextSize(15);
        credits.setLetterSpacing(0.06f);
        credits.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = 12;
        box.addView(credits, cp);

        TextView qq = new TextView(this);
        qq.setText("QQ 3353739856");
        qq.setTextColor(Color.parseColor("#a7a49c"));
        qq.setTextSize(14);
        qq.setLetterSpacing(0.08f);
        qq.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams qp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        qp.topMargin = 16;
        box.addView(qq, qp);
        return box;
    }

    /** 淡出并移除启动画面（幂等） */
    private void dismissSplash() {
        if (splashDismissed || splash == null || splash.getParent() == null) return;
        splashDismissed = true;
        handler.removeCallbacksAndMessages(null);
        AlphaAnimation fade = new AlphaAnimation(1f, 0f);
        fade.setDuration(350);
        fade.setFillAfter(true);
        fade.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override public void onAnimationStart(android.view.animation.Animation a) { }
            @Override public void onAnimationRepeat(android.view.animation.Animation a) { }
            @Override public void onAnimationEnd(android.view.animation.Animation a) {
                android.view.ViewGroup parent = (android.view.ViewGroup) splash.getParent();
                if (parent != null) parent.removeView(splash);
                splash = null;
            }
        });
        splash.startAnimation(fade);
    }

    /** 把 assets/site 递归复制到内部存储（仅首次或文件更新时） */
    private void copyAssets(String assetPath, File dest) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            // 叶子文件
            copyFile(assetPath, dest);
            return;
        }
        if (!dest.exists() && !dest.mkdirs()) throw new IOException("无法创建 " + dest);
        for (String child : children) {
            String nextAsset = assetPath + "/" + child;
            File nextDest = new File(dest, child);
            String[] sub = getAssets().list(nextAsset);
            if (sub == null || sub.length == 0) copyFile(nextAsset, nextDest);
            else copyAssets(nextAsset, nextDest);
        }
    }

    private void copyFile(String assetPath, File dest) throws IOException {
        if (dest.exists() && dest.length() > 0) return;
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("无法创建 " + parent);
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.close();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
