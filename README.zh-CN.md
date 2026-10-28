# Hoshi Study · 星读学习

**把日语 EPUB 阅读、查词、AI 讲解、学习笔记和复习连接起来的 Android 开源应用，面向日语学习者及墨水屏设备。**

[English](README.en.md) · [许可证](LICENSE) · [来源与修改说明](NOTICE.md) · [隐私说明](PRIVACY.md)

Hoshi Study 基于 [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) 二次开发；后者是 [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) 的 Android 原生实现。本项目保留上游阅读与查词能力，在此基础上增加 AI 辅助学习、笔记和批量复习工作流。它是独立维护的社区衍生项目，与上游官方版本有不同的功能及发布节奏。

## 能做什么

| 功能 | 使用方式 |
| --- | --- |
| 日语 EPUB 阅读 | 导入书籍，横排或竖排，分页或连续滚动；支持主题、字体、书架、标注及阅读统计。 |
| 离线查词 | 导入 Yomitan 辞典后，在阅读器或辞典页查词；支持释义内递归查询。 |
| AI 句子讲解 | 选中句子，获取中文翻译、语法拆解、词汇解释和练习；支持可配置的 HTTPS chat-completions 接口，包括 DeepSeek。 |
| 学习笔记 | 保存单词、句子、原文来源和讲解，方便集中回顾。 |
| 批量复习 | 勾选多个知识点，一次生成复习材料；讲解与试题放在前面，答案统一放在末尾，支持预览与分享。 |
| 合并复习邮件 | 将选中的多个知识点放进一封邮件；支持 SMTP 连接测试及可选的每日复习邮件。 |
| Anki 制卡 | 通过 AnkiDroid 或 AnkiConnect 添加卡片；支持 Basic/基本及 Lapis、Kiku、Senren 字段映射，提供成功、权限和设置提示。 |
| 墨水屏阅读 | 默认白底黑字，沿用上游墨水屏选项与音量键翻页。 |
| 上游扩展能力 | 保留有声书跟读、Google Drive 同步及备份迁移等模块；相关服务需要使用者独立配置。 |

## 相比原版的主要改造

- 增加阅读器选中文本的 AI 讲解入口，以及本地学习笔记和复习界面。
- 增加多选知识点、分批生成后合并的复习材料，以及单封邮件发送流程。
- 改进 AI 返回格式解析，区分内容生成失败与邮件发送失败。
- 为标准 Anki 正反面模板配置默认字段映射，并增加制卡结果反馈及失败重试。
- 默认启用适合墨水屏的显示设置，阅读进度显示到小数点后一位。
- 公开版使用独立应用 ID `org.hoshistudy.reader`；不附带任何个人账户或私用构建配置。

## 开始使用

1. 自行构建并安装应用，导入 EPUB。
2. 在辞典设置中导入你有权使用的 Yomitan 辞典。**公开版不附带辞典和书籍。**
3. 在学习设置中填写自己的 API 地址、模型名和 API key，然后测试连接。
4. 如需邮件复习，填写 SMTP 主机、发件账号、收件地址及密码。Gmail 的 SMTP 登录需要应用专用密码，不能直接填写普通账户密码。
5. 如需 Anki，在同一 Android 设备安装 AnkiDroid，获取牌组与笔记类型、授予访问权限，再选择制卡模板；也可自行配置 AnkiConnect。

API key、发件账号、收件地址和 SMTP 密码均默认为空。AI 和邮件服务可能收费，其账号、额度及可用性由使用者自行管理。模型名可在应用中修改，以服务商当前支持的名称为准。

## 构建

需要 JDK 21、Android SDK 36.1、NDK 29.0.14206865、CMake 3.31.6、Rust Android target 和 cargo-ndk；Gradle Wrapper 固定为 9.4.1。

```bash
git clone --recurse-submodules https://github.com/crazysean-11/Hoshi-Study.git
cd Hoshi-Study
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
./gradlew test lint assembleDebug
node --test app/src/test/js/*.test.mjs
```

默认调试构建面向 arm64-v8a，产物位于 `app/build/outputs/apk/debug/`。原生模块首次构建需要下载依赖；可用 `CARGO` 指定 cargo 路径。若发生 Java 堆内存不足，可为 Gradle 配置 `-Xmx4096m` 并限制 worker 数。

发布版：`./gradlew :app:assembleRelease`。需要自行提供签名，通过 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 配置；不要提交签名文件或口令。当前仓库只发布源码，旧的私人 APK 不属于开源发行物。构建与测试细节见 [验证指南](docs/VALIDATION.md)。

## 隐私与状态

书库与笔记保存在设备本地；API key 和邮件凭据通过 Android Keystore 加密存储。使用 AI 时，所选文本及相关上下文会发送给配置的 AI 服务；邮件功能会向配置的 SMTP 服务发送所选笔记与复习材料。离线查词不需要 AI。详见 [PRIVACY.md](PRIVACY.md)。

公开源码从私人开发版分离，删除了个人配置、密钥预置入口、辞典资源和本机脚本，并重新建立提交历史，避免私人 Git 作者邮箱或历史配置进入公开仓库。保留上游许可证、源码版权声明及公共依赖引用。

学习和 Anki 改造的原始开发版曾通过 1946 项 JVM 单元测试及 322 项 JavaScript 测试；**这些数字不是本公开版重新编译或真机验证的结果**。本次公开版已通过 322 项 JavaScript 测试和 19 项学习模型测试（含默认凭据为空的检查）；完整 Android 构建仍待 CI 验证。实际 Anki 写卡、账户邮件投递、墨水屏残影及后台任务时机仍需在目标设备验证；系统省电可能延后每日邮件。

## 来源、许可证与鸣谢

基于 Hoshi Reader Android 的 GPLv3 源码改造，继续以 **GNU GPL v3** 发布，完整条款见 [LICENSE](LICENSE)。修改说明见 [NOTICE.md](NOTICE.md)。第三方组件沿用各自许可证，不因本项目改名而改变。

感谢 [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android)、[Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader)、[hoshidicts](https://github.com/Manhhao/hoshidicts)、[Yomitan](https://github.com/yomidevs/yomitan) 和 [AnkiDroid](https://github.com/ankidroid/Anki-Android)。

欢迎通过本仓库 Issues 和 Pull Requests 交流改进。提交问题前请删除日志、截图和配置中的密钥、邮箱及私人书籍内容。
