# 拾零 · Android 图文日记

把普通日子里的文字、照片和心情收集起来。拾零是使用 Kotlin 与 Jetpack Compose 开发的 Android 原生日记应用，当前版本为 **1.2.3**。

## 功能

- 完整正文时间线、日历回忆、搜索、年月跳转与回到今天。
- 到达阅读边界后拉动切换日期；普通长文保持连续滚动。
- 沉浸式阅读、双击正文原位编辑、草稿保存与取消提醒。
- 图片私有存储，保留导入文件原始内容；全屏预览、缩放和左右浏览。
- 纸页与简洁时间线，自定义背景、文字颜色、毛玻璃透明度和模糊。
- 48 款主题颜色预设，包含 16 款莫兰迪色；自定义心情和独立标签。
- 在本机生成每日分享图片，支持全文分页、预览、保存及系统分享。包含纸页诗意、生活杂志、胶片记忆、雾色书信及八款经典风格。
- Room 本地数据库、ZIP 数据与原始图片备份、合并恢复。
- 用户主动定位与足迹地图；离线每日一言。

书架功能尚未实现。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。工程自带 Gradle 8.9 Wrapper；首次构建需要联网下载依赖。

在 Android Studio 打开项目，配置本机 SDK。Windows PowerShell 设置 `JAVA_HOME` 与 `ANDROID_HOME` 后执行：

```powershell
.\build.ps1
```

脚本在工程内创建测试签名与缓存。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，应用名称为“拾零·测试”，包名带 `.debug` 后缀，与正式版隔离。

Android Studio 或其他平台直接运行 Wrapper 前，可以把自有测试密钥放在 `signing/debug.keystore`；密钥不得提交到仓库。

## 正式版签名与升级

`sign-release.ps1` 构建并签名正式版，签名材料保存在被 Git 排除的 `signing/` 目录。该脚本在没有密钥时会创建新密钥，**已有应用的维护者必须先恢复原签名材料，再运行正式版签名**。

覆盖升级必须保持原有签名、应用 ID `com.fuguang.localdiary.nativeapp` 和数据库名。不可通过卸载重装或清除数据来处理普通升级。仓库不包含发布密钥、口令、用户数据库、照片、设备备份或真实日记截图。

## 工程结构

| 目录 | 内容 |
| --- | --- |
| `app/src/main` | Compose 界面、Room 数据、备份、图片与本地分享排版 |
| `app/src/androidTest` | 滚动、阅读、编辑、图片、分享、数据升级等设备测试 |
| `gradle/wrapper` | Gradle Wrapper |
| `tools` | 打包、语料整理和本机备份辅助脚本 |
| `design-previews/share-imagegen-v2` | 使用示例内容生成的四款分享方向稿 |
| `design-previews/share-1.2.1` | 使用示例内容的应用实际排版导出 |

## 验证范围

已在 Huawei PLA-AL10（Android API 31）进行设备测试。1.2.1 完成 34 项测试；1.2.2 另完成 7 项系统栏、背景、编辑、图片和分享相关测试。1.2.3 构建、lint、背景颜色持久化用例及预设颜色手动选择通过。

这些结果不等于所有机型均已验证。设备测试可能改写测试应用的数据，仅可对隔离的 `.debug` 包运行；有需要保留的测试记录时应先备份。不能把测试目标改为正式包。

## 数据与隐私

日记、照片、心情与分享排版在本机处理，不上传应用服务器。目前没有账户、云同步、支付或广告服务。地图底图使用 OpenStreetMap 在线瓦片，请求会暴露所查看区域，足迹记录本身不上传。

本地存储不代表额外加密保险箱。卸载、清除数据或设备损坏都可能使记录丢失，应定期导出并妥善保存 ZIP 备份。

毛玻璃通过预模糊壁纸及对应区域采样实现，不使用苹果专有 Liquid Glass 引擎。设计稿中的文字和照片均为示例，实际分享图片按用户选中的日记内容动态绘制。

## 可复用的原生开发 Skill

[android-app-delivery](.agents/skills/android-app-delivery/SKILL.md) 整理了从需求到可安装 Android 原生应用的流程：工程与工具链、Kotlin/Compose 页面、状态与导航、Room 数据、图片和权限、Gradle 编译、真机调试、保留数据升级与 APK 签名。附只读环境检查脚本，GitHub 发布为可选环节。

在支持项目级 skills 的 Codex 环境中可以使用 `$android-app-delivery`。用于其他项目时，将完整的 `android-app-delivery` 目录复制到目标工程的 `.agents/skills/` 下。

本仓库暂未授予开源许可证。
