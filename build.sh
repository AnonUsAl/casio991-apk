#!/bin/bash
# fx-991CN X 科学计算器 APK 一键构建脚本
# 纯 Android SDK 工具链，不依赖 Gradle / Maven 网络
set -e

# 用绝对路径绕开 shell 里 find->fd / ls->lsd 的 alias 干扰
FIND=/usr/bin/find
LS=/bin/ls

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="$HOME/Library/Android/sdk"
BT="$SDK/build-tools/36.0.0"
AJAR="$SDK/platforms/android-36/android.jar"
KS="$PROJ/casio991.keystore"
KS_ALIAS=casio991
KS_PASS=casio991

cd "$PROJ"
echo "==> 工程目录: $PROJ"

# 校验工具
for t in aapt2 d8 apksigner zipalign; do
  [ -x "$BT/$t" ] || { echo "缺少 $BT/$t"; exit 1; }
done
[ -f "$AJAR" ] || { echo "缺少 $AJAR"; exit 1; }
[ -f "$KS" ] || {
  echo "生成签名 keystore..."
  keytool -genkeypair -v -keystore "$KS" -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=AnonUsAl, OU=WorkBuddy, O=ClouderyStudio, L=CN, ST=CN, C=CN" >/dev/null 2>&1
}

rm -rf build classes
mkdir -p build classes

echo "==> [1/6] 编译 Java"
$FIND src -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$AJAR" -d classes 2>/dev/null

echo "==> [2/6] 转换 dex"
$FIND classes -name '*.class' -print0 | xargs -0 "$BT/d8" --lib "$AJAR" --min-api 24 --output build

echo "==> [3/6] 编译并链接资源"
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJAR" \
  --manifest AndroidManifest.xml --java gen \
  --min-sdk-version 24 --target-sdk-version 36 \
  --auto-add-overlay -R build/res.zip

echo "==> [4/6] 组装 APK (base + assets + classes.dex)"
cp build/base.apk build/unsigned.apk
zip -q -r build/unsigned.apk assets
cd build && zip -q unsigned.apk classes.dex && cd "$PROJ"

echo "==> [5/6] zipalign 对齐"
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

echo "==> [6/6] 签名"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$PROJ/fx991cnx.apk" build/aligned.apk 2>/dev/null

echo "==> 验证签名"
"$BT/apksigner" verify "$PROJ/fx991cnx.apk" 2>/dev/null

echo ""
echo "构建完成:"
$LS -lh "$PROJ/fx991cnx.apk"
"$BT/aapt2" dump badging "$PROJ/fx991cnx.apk" 2>/dev/null | grep -E "^package|application-label|application-icon|launchable-activity" | head -6
