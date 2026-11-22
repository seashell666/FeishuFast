# -*- coding: utf-8 -*-
"""无 Gradle 依赖的 Xposed 模块构建脚本（javac + d8 + aapt2 + apksigner）"""
import os
import glob
import shutil
import subprocess
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
T = os.path.join(ROOT, "tools")
JDK = os.path.join(T, "jdk", "jdk-17.0.21+9", "bin")
BT = os.path.join(T, "bt", "android-15")
AJAR = os.path.join(T, "pf", "android-35", "android.jar")
B = os.path.join(ROOT, "build")

JAVAC = os.path.join(JDK, "javac.exe")
JAVA = os.path.join(JDK, "java.exe")
KEYTOOL = os.path.join(JDK, "keytool.exe")
AAPT2 = os.path.join(BT, "aapt2.exe")
ZIPALIGN = os.path.join(BT, "zipalign.exe")


def run(cmd, quiet=False):
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out = r.stdout.decode("utf-8", "replace")
    if r.returncode != 0:
        print(out)
        raise SystemExit("[FAILED] " + os.path.basename(str(cmd[0])))
    if out.strip() and not quiet:
        print(out.strip()[:1500])
    return out


def one(pattern):
    hits = glob.glob(pattern)
    if not hits:
        raise SystemExit("[MISSING] " + pattern)
    return hits[0]


print("=== clean ===")
shutil.rmtree(B, ignore_errors=True)
classes = os.path.join(B, "classes")
dexdir = os.path.join(B, "dex")
os.makedirs(classes, exist_ok=True)
os.makedirs(dexdir, exist_ok=True)

print("=== 1/7 javac ===")
srcs = glob.glob(os.path.join(ROOT, "src", "**", "*.java"), recursive=True)
run([JAVAC, "-source", "8", "-target", "8", "-encoding", "UTF-8",
     "-Xlint:-options", "-bootclasspath", AJAR, "-d", classes] + srcs)

# Xposed API 由框架在运行时提供，桩类绝不能打进 APK
shutil.rmtree(os.path.join(classes, "de"), ignore_errors=True)

print("=== 2/7 d8 ===")
d8jar = one(os.path.join(BT, "lib", "d8*.jar"))
appclasses = glob.glob(os.path.join(classes, "**", "*.class"), recursive=True)
run([JAVA, "-cp", d8jar, "com.android.tools.r8.D8",
     "--lib", AJAR, "--min-api", "26", "--output", dexdir] + appclasses)
dex = os.path.join(dexdir, "classes.dex")
if not os.path.isfile(dex):
    raise SystemExit("no classes.dex produced")

print("=== 3/7 aapt2 link ===")
base_apk = os.path.join(B, "base.apk")
run([AAPT2, "link", "-o", base_apk, "-I", AJAR,
     "--manifest", os.path.join(ROOT, "AndroidManifest.xml"),
     "-A", os.path.join(ROOT, "assets"),
     "--min-sdk-version", "26", "--target-sdk-version", "29"])

print("=== 4/7 inject classes.dex ===")
with zipfile.ZipFile(base_apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")

print("=== 5/7 zipalign ===")
aligned = os.path.join(B, "aligned.apk")
run([ZIPALIGN, "-f", "4", base_apk, aligned], quiet=True)

print("=== 6/7 keystore ===")
ks = os.path.join(ROOT, "feishufast.jks")
if not os.path.isfile(ks):
    run([KEYTOOL, "-genkeypair", "-keystore", ks,
         "-storepass", "android", "-keypass", "android",
         "-alias", "feishufast", "-keyalg", "RSA", "-keysize", "2048",
         "-validity", "10000", "-dname",
         "CN=FeishuFast, OU=WorkBuddy, O=WorkBuddy, C=CN"], quiet=True)

print("=== 7/7 apksigner ===")
signer = one(os.path.join(BT, "lib", "apksigner*.jar"))
out_apk = os.path.join(ROOT, "FeishuFast.apk")
run([JAVA, "-cp", signer, "com.android.apksigner.ApkSignerTool", "sign",
     "--ks", ks, "--ks-pass", "pass:android", "--key-pass", "pass:android",
     "--out", out_apk, aligned])
run([JAVA, "-cp", signer, "com.android.apksigner.ApkSignerTool", "verify",
     "--verbose", out_apk])

with zipfile.ZipFile(out_apk) as z:
    names = z.namelist()
    assert "classes.dex" in names and "assets/xposed_init" in names
    assert "AndroidManifest.xml" in names
    print("APK entries:", names)

print("\n=== DONE ===")
print(out_apk, os.path.getsize(out_apk), "bytes")
