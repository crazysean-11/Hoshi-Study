# 隐私与数据 / Privacy

- 本公开源码不包含作者或使用者的 API key、邮箱账号、SMTP 密码、签名私钥、私人书库或学习记录。
- API 和邮件配置由使用者在设备上填写；凭据通过 Android Keystore 的 AES-GCM 密钥加密后存储，相关偏好排除在系统备份与设备迁移之外。设备解锁、恶意软件、root 等仍会影响设备安全。
- 书籍、辞典、阅读记录、笔记与缓存主要保存在应用本地。卸载应用可能删除这些数据。
- 请求 AI 时，选中的文字、相关词典释义或生成复习所需笔记会发送至使用者配置的服务商；该服务可能记录请求并产生费用，请自行审阅其政策。
- 发送复习邮件时，所选笔记、讲解与练习会通过配置的 SMTP 服务发送到配置的收件人。每日邮件默认关闭；仅在使用者开启后运行，系统后台限制可能延迟发送。
- Anki 功能与使用者授权的 AnkiDroid 或 AnkiConnect 服务通信。同步、音频、组件下载、更新检查等继承功能在使用时可能访问对应第三方服务；公开版更新检查指向本仓库，部分原生运行时组件仍引用上游公开发行资源。
- 不提供中央托管的 AI 或邮件服务器，不要求向项目维护者提交凭据。调试日志与 Issue 截图应自行脱敏。

The public edition contains no personal account configuration. Credentials are entered on-device and encrypted using Android Keystore. AI requests send selected text/review content to your configured provider; email sends chosen notes and review material through your SMTP provider. Optional upstream features communicate with their configured services. Review provider policies before enabling network features. Do not put credentials or private books into issues or pull requests.
