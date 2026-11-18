# fx-991CN X 科学计算器（Android）

把网页版 [calculator.weo.asia](https://calculator.weo.asia) 的 fx-991CN X 模拟器打包成的 Android APK。

**完全离线 · 完全免费 · 无广告 · 无内购**——原机 ROM（nX-U8 指令级模拟 → WebAssembly）与全部资源都内置在 APK 里，装好后不联网也能用。

## 特性

- 原机固件模拟，按键逻辑、显示字形与真机一致（基于 CasioEmuNeo）
- 支持方程、矩阵、统计、积分、复数、Base-N 等全部科学计算功能
- 黑色主题，计算记忆自动保存在本机
- 强制竖屏；右下角悬浮按钮可展开工具（重新开机 / 导出记忆 / 截图等）
- 开屏显示署名

## 安装

下载 [fx991cnx.apk](fx991cnx.apk) 传到手机直接安装（需允许"安装未知来源"），或：

```bash
adb install fx991cnx.apk
```

## 自己构建

需要 macOS + Java 17+、Android SDK（build-tools 36.0.0 / platform android-36）。无需 Gradle / Maven，不联网拉依赖。

```bash
./build.sh
```

脚本会依次完成：编译 Java → d8 转 dex → aapt2 编译资源 → 组装 APK → zipalign → 用 `casio991.keystore` 签名。首次运行若没有 keystore 会自动生成一个（**请自行保管，不要提交到仓库**）。

## 技术说明

- 原生 WebView + 内置 `LocalServer`（只监听 127.0.0.1 的随机端口），把 assets 里的站点以同源 http 供给 WebView，绕开 `file://` 下 fetch/WebAssembly 的 CORS 限制。零第三方依赖。
- 站点资源在 `assets/site/`，含 `model/rom.bin`（262 KB）、`runtime/casioemu_web.wasm`（425 KB）等。
- 包名 `com.anonusal.casio991`，minSdk 24 / targetSdk 36。

## 版权 / 免责声明

本项目为独立打包作品，**非 Casio 官方产品**。计算器固件、机身与品牌归原权利人（卡西欧）所有；底层模拟内核来自开源项目 [CasioEmuNeo](https://github.com/qiufuyu123/CasioEmuNeo)（GPL-3.0），网页版来自 [WENGENG-boop/fx991cnx-web](https://github.com/WENGENG-boop/fx991cnx-web)。请仅作个人学习与备份用途，勿用于商业分发。
