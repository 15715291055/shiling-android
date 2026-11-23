# 工程搭建与架构

## 环境检查与版本选择

先识别已有的 `settings.gradle(.kts)`、根与模块构建文件、版本目录、Wrapper、`local.properties` 和项目脚本。读取约束后再决定要补哪些文件。

新工程需要确定以下配置的兼容组合，而不是把每一项分别选成最新版本：

| 配置 | 应确认的关系 |
| --- | --- |
| Android Gradle Plugin | 支持的 Gradle、JDK、SDK |
| Kotlin | AGP 支持范围、Compose 编译器插件、KSP |
| Compose BOM | Compose 库版本；BOM 不管理 Kotlin/AGP 的版本 |
| Room / KSP | Room 处理器与所选 Kotlin/KSP 组合 |
| minSdk / targetSdk / compileSdk | 目标用户设备、平台行为与依赖要求 |

使用当前官方说明：[Android 构建配置](https://developer.android.com/build)、[Compose 编译器配置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。Kotlin 2.0 及以上的 Compose 工程使用对应的 Compose Compiler Gradle 插件；接手旧工程时先确认其现状。

环境助手会列出 SDK 平台和构建工具、Java 版本及 Wrapper 文件是否存在。它不是兼容性判定器，也不接受 SDK 许可证或自动安装工具。实际构建仍可能因为网络、许可证或未下载的组件失败。

## 从零建立可构建工程

可使用 Android Studio 官方模板，也可在命令行建立同等原生工程。没有 Studio 不代表无法构建，但必须补齐真实 Wrapper、SDK 和构建配置。

建议先完成：

1. 包名、显示名称、SDK 范围和版本号。
2. Gradle 插件与仓库配置、应用模块、可运行的 Wrapper；Wrapper JAR 来自可信发行或已验证工程，不能伪造空文件。配置发行校验值。
3. Manifest、入口 Activity、启动主题、图标与最小 Compose/Views 页面。
4. 构建 Debug APK，在模拟器或授权设备上打开该页面。
5. 再接入第一条真实数据流程。

确认确实需要的权限后才加入 Manifest；相册选择通常可使用系统 Photo Picker 或文件选择器，不应为了选择一张图片索取所有文件访问权。

典型的小型工程可采用下面的结构，按产品需要增减：

```text
app/src/main/
  AndroidManifest.xml
  java/<包路径>/
    MainActivity.kt
    ui/                 页面、导航、公共组件与主题
    data/               Entity、DAO、Database、Repository/ViewModel
    storage/            图片与备份等文件操作
  res/
app/src/androidTest/    需要设备能力的行为测试
app/src/test/           纯逻辑测试（确有价值时添加）
gradle/wrapper/
```

依赖注入框架、多模块、网络后端不是构建原生应用的前置条件。确实出现共享状态、复杂边界或复用需要时再引入。

## 页面、状态与导航

- 页面消费清晰的状态并发出事件；数据库和文件 IO 不放在 Composable 的重组路径中。
- 使用 ViewModel 管理需要跨重建保留的业务状态；按生命周期收集 Flow。短暂 UI 状态与业务草稿分开管理。
- 保存中的状态限制重复提交；失败后保留输入并允许重试。返回到列表后能看到实际数据库更新。
- 导航参数优先传 ID、日期等小值，目标页读取数据。明确返回栈、编辑取消与外部分享返回行为。
- 进程重建与屏幕旋转的保留策略由需求决定；`remember` 不等于磁盘持久化。

## 持久化从第一条数据开始贯通

结构化内容按需使用 Room；偏好可使用项目已有的 DataStore/SharedPreferences 等方案；大图片留在文件存储，数据库保存稳定引用。

先让“创建 → 列表 → 详情 → 编辑 → 重启读回”贯通，再增加复杂外观。日期、时区、排序和唯一 ID 在这一阶段定义，避免后续用展示文案反推业务数据。

Room schema 变化提升数据库版本并实现迁移，保留所需历史 schema。迁移测试应覆盖真实旧版本结构及字段值，不只验证新库能创建。不能用 destructive migration 解决已有用户数据库问题。参考 [Room 迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)。

## 版本与身份

`applicationId` 是安装和升级身份，`namespace` 是代码/资源命名空间，显示名称是用户看到的文字。重命名应用通常只需改显示名称，不应顺便改应用 ID。

正式与测试包可用后缀和不同名称隔离。已有正式用户时，测试签名不能覆盖正式签名。versionCode 单调增加；只修改文档或 skill 时，不需要重新提升 App 版本或重新安装手机。
