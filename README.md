# 拾光橘 · 全局悬浮窗版（跨应用置顶）

时间记录与复盘工具，支持**全局悬浮窗**：在微信/抖音/桌面等其他界面也能看到计时器，并直接 ▶ 开始 / ⏸ 暂停 / 💾 保存 / 📂 切换类别。

## 目录结构

| 文件 | 作用 |
|---|---|
| `index.html` | 原应用（仅新增右上角 📌 按钮，业务逻辑未改） |
| `config.xml` / `plugin.xml` | Cordova 配置 + 插件声明（含 SYSTEM_ALERT_WINDOW 权限） |
| `src/main/java/com/shiguangju/FloatingWindowService.java` | 全局悬浮窗原生服务（TYPE_APPLICATION_OVERLAY + 前台服务保活） |
| `src/main/java/com/shiguangju/FloatingWindowPlugin.java` | JS ↔ 原生桥接 |
| `res/layout/floating_window.xml` + `res/drawable/*` | 悬浮窗布局与样式 |
| `www/floating-window.js` | 挂载到 `cordova.plugins.FloatingWindow` |
| `manifest.json` / `sw.js` | PWA 配置（应用内仍可作 PWA 安装） |
| `icon-192.png` / `icon-512.png` | 应用图标（橘子钟） |
| `build.sh` | 一键构建脚本（需 Cordova + Android SDK） |

## 悬浮窗能力

- 跨应用置顶显示计时器（TYPE_APPLICATION_OVERLAY）
- ▶ 开始 / ⏸ 暂停（无需回 App）
- 💾 **保存**：把当前计时作为一条记录保存，并重置计时器
- 📂 切换类别（循环切换 8 个类别）
- 拖拽移动（位置自动保存）+ ◀ 折叠成细胶囊 + ✕ 隐藏
- 绑前台服务 + 状态栏常驻通知，降低被杀概率

## 编译成 APK（3 选 1）

### 方式 A：PhoneGap Build 在线编译（推荐，电脑/手机浏览器均可）

1. 下载本仓库 ZIP → 解压，得到 `shiguangju-global-float` 文件夹
2. 全选文件夹内**所有文件**（不含外层父文件夹），压缩成新 ZIP
   - 根目录应直接是 `index.html` / `config.xml` / `plugin.xml` / `icon-192.png` …，不要多套一层
3. 打开 https://build.phonegap.com → 登录 → **New App → Upload .zip**
4. 等 3~5 分钟 → 下载 `app-debug.apk`

### 方式 B：GitHub Actions 免费云端编译

1. GitHub 新建仓库 `shiguangju`
2. 把本目录所有文件推送到仓库
3. 在仓库创建文件 `.github/workflows/build-apk.yml`，内容：

```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm install -g cordova
      - run: |
          cordova create app com.shiguangju.app 拾光橘
          cp config.xml app/
          cp index.html manifest.json sw.js icon-192.png icon-512.png app/www/ 2>/dev/null || true
          mkdir -p app/www
          cp index.html manifest.json sw.js icon-192.png icon-512.png app/www/
          cp plugin.xml app/plugin.xml
          mkdir -p app/plugin-src/main/java/com/shiguangju app/plugin-src/layout app/plugin-src/drawable app/plugin-src/www
          cp src/main/java/com/shiguangju/*.java app/plugin-src/main/java/com/shiguangju/
          cp res/layout/floating_window.xml app/plugin-src/layout/
          cp res/drawable/*.xml app/plugin-src/drawable/
          cp www/floating-window.js app/plugin-src/www/
          cd app && cordova platform add android && cordova plugin add plugin.xml && cordova build android --debug
      - uses: actions/upload-artifact@v4
        with:
          name: shiguangju-apk
          path: app/platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

4. 推送后到 **Actions** 标签，等绿勾 → 底部 Artifacts 下载 APK

### 方式 C：本地 Cordova 编译

```bash
npm install -g cordova
cordova create app com.shiguangju.app 拾光橘
cp config.xml app/ && cp plugin.xml app/
cp index.html manifest.json sw.js icon-192.png icon-512.png app/www/
cp -r src app/plugin-src/ && cp -r res app/plugin-src/ && cp -r www app/plugin-src/
cd app && cordova platform add android && cordova plugin add plugin.xml
cordova build android --debug
# APK: platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

## 安装后：开启 4 个系统开关（悬浮窗生效的关键）

| 顺序 | 路径（红米 K80 至尊版 · 澎湃OS） | 操作 |
|---|---|---|
| ① | 设置 → 应用设置 → 权限管理 → ⋮ → 特殊权限 → **允许显示在其他应用上方** → 拾光橘 | 选 **始终允许** |
| ② | 设置 → 应用设置 → 应用管理 → 拾光橘 → 权限管理 → **后台弹出界面** | 允许 |
| ③ | 同上 → 电池与性能 → 省电策略 | **无限制** |
| ④ | 同上 → 自启动 | 开启 |

> ② 是小米特有开关，**不开启则悬浮窗退后台必失效**，90% 失败都卡在这。

## 使用

1. 打开拾光橘 → 正常 ▶ 开始计时
2. 点右上角 **📌** → 首次跳转到 ① 的设置页，开启后返回，悬浮窗出现
3. 回到桌面或打开微信/抖音，悬浮窗**仍在置顶**
4. 付款（微信/支付宝）时悬浮窗会暂时隐藏，付完自动恢复（系统 FLAG_SECURE 机制，所有 App 都这样）

## 权限说明

- ✅ 保留：SYSTEM_ALERT_WINDOW（悬浮窗必需）、FOREGROUND_SERVICE（后台计时保活）、POST_NOTIFICATIONS、READ/WRITE_EXTERNAL_STORAGE（读图标）
- ❌ 无网络/无定位/无相机/无麦克风/无通讯录/无蓝牙/NFC/电话

应用完全离线运行。

## 已知限制

- 银行/支付安全界面会隐藏第三方悬浮窗（系统强制，无法绕过）
- 厂商 ROM 差异大，若悬浮窗不显示，优先检查 ② 后台弹出界面 是否开启
