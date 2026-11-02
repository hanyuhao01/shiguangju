# 拾光橘 V2

保留现有 Cordova 页面、计时、任务、复盘、吉祥物、主题和悬浮窗；加入可靠计时、本地规则复盘、休息提醒与 JSON 备份。核心功能离线运行，没有模型 API。

详细证据、限制与手机验收见 [V2 验收记录](docs/V2-VALIDATION.md)。

## 测试

```sh
npm install --ignore-scripts
npm test
node tests/browser.cjs
```

浏览器测试要求 Node22+ 和 Chrome；可设置 CHROME_BIN。隔离配置、截图和模拟导出文件保存在被忽略的 artifacts/。

## 构建独立测试 APK

安装 JDK17、Android SDK34、Build Tools34.0.0、Gradle8.7 后，在全新工作副本执行：

```sh
node scripts/prepare.cjs --test
npx cordova platform add android@13.0.0 --no-telemetry
npx cordova plugin add ./plugin_src
npx cordova build android --debug -- --packageType=apk
node scripts/android-tests.cjs
gradle -p platforms/android :app:testDebugUnitTest
```

prepare --test 会修改该副本 config.xml 的包名为 com.shiguangju.test，显示名为“拾光橘 V2 测试”。APK 在 platforms/android/app/build/outputs/apk/debug/app-debug.apk。

PR 自动执行上述流程，绿色运行的 shiguangju-v2-test 制品含 APK、SHA256 和界面截图；java-test-reports 含原生测试报告。不要用中间失败运行的包。

## 正式签名

build.yml 仅 main 手动触发，使用 production 环境，必须配置长期固定密钥的 ANDROID_KEYSTORE_BASE64、ANDROID_STORE_PASSWORD、ANDROID_KEY_PASSWORD、ANDROID_KEY_ALIAS Secrets。缺少任何一项就失败；不再生成生产密钥，不把密码或私钥写入仓库。

当前未提供原签名私钥，不能保证覆盖手机旧版；测试包独立安装，切勿卸载旧版。保护正式数据的迁移需要另行验证，详见验收记录。
