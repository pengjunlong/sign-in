# 中国移动自动签到

基于 Android 无障碍服务（AccessibilityService）实现的中国移动 App 自动签到工具。

[![CI / CD](https://github.com/pengjunlong/sign-in/actions/workflows/ci.yml/badge.svg)](https://github.com/pengjunlong/sign-in/actions/workflows/ci.yml)

## 功能

- 自动跳过开屏广告（识别"跳过"/"跳过广告"等按钮）
- 自动点击首页左上角签到入口
- 自动点击签到页面中的签到按钮
- 全程无需人工干预

## 使用方法

1. 安装 APK 到 Android 设备（Android 7.0+）
2. 打开 App，点击 **「开启无障碍服务」** 跳转到系统无障碍设置
3. 找到「**中国移动自动签到**」并开启
4. 返回 App，点击 **「打开中国移动 App 并开始签到」**
5. 等待自动完成签到，过程中请勿触碰屏幕

## 工作原理

```
启动中国移动 App
       ↓
[SKIP_AD]       检测"跳过"按钮 → 点击跳过开屏广告
       ↓
[WAIT_HOME]     等待首页加载完成
       ↓
[CLICK_SIGN_ENTRY]  点击左上角签到入口
       ↓
[WAIT_SIGN_PAGE]    等待签到页面加载
       ↓
[CLICK_SIGN_BTN]    点击签到按钮 → 完成签到 ✅
```

状态机驱动，每步最多重试 20 次，超时自动跳至下一步，兼容页面加载慢的情况。

## 项目结构

```
app/src/main/
├── java/com/example/signin/
│   ├── service/
│   │   └── ChinaMobileSignInService.kt   # 无障碍服务核心逻辑
│   └── ui/
│       └── MainActivity.kt               # 引导界面
└── res/
    ├── xml/accessibility_service_config.xml  # 无障碍服务配置
    ├── layout/activity_main.xml
    └── values/{strings, themes, colors}.xml
```

## 构建

```bash
./gradlew assembleDebug
```

Release 构建需提供签名参数：

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=*** \
  -Pandroid.injected.signing.key.alias=*** \
  -Pandroid.injected.signing.key.password=***
```

## CI / CD

| 触发条件 | 执行内容 |
|---|---|
| PR → `main` / `develop` | Lint 检查 + 构建 Debug APK |
| Push tag `v*.*.*` | 构建签名 Release APK + 发布 GitHub Release |

Release 签名需在仓库 **Settings → Secrets** 中配置：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

生成 Base64：
```bash
base64 -i your.keystore | pbcopy   # macOS，结果直接复制到剪贴板
```

## 注意事项

- 本工具仅用于个人自动化签到，请勿用于商业用途
- 中国移动 App 更新后如界面变化可能需要调整节点匹配逻辑
- 签到过程中请勿手动操作屏幕，避免干扰无障碍服务的事件监听

