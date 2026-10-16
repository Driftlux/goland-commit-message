# 提交说明生成

Copyright 2026 spc. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

在 GoLand 提交说明工具栏点击生成按钮，根据当前勾选的变更和全局提示词生成提交说明，完成后填入提交框。

## 使用

需要 GoLand 2026.1 或更新版本，以及已安装、完成认证的 Codex CLI。

- 在 Settings、Version Control、提交说明生成 中配置 Codex 路径、模型和全局提示词。
- Codex 路径留空时，从 IDE 的 PATH 查找；找不到时指定可执行文件的完整路径，不依赖固定的 Node/nvm 版本。
- 模型可选择或手动输入；留空时沿用 Codex 自身配置。模型目录只是建议列表，不会替换已选模型。
- 模型目录读取 `CODEX_HOME`（未设置时为 `~/.codex`）中的 `config.toml` 顶层 `model_catalog_json`，未配置时读取 `magpie-models.json`。该配置项须为单行字符串；相对路径相对于 Codex 配置目录。目录读取失败会显示原因，但不阻止手动指定模型或沿用 Codex 配置。
- 生成不会自动触发，也不会执行 Git 提交。文件列表仅采用当前勾选范围，包含勾选的未版本化新文件，不再截断到 80 个文件。
- 生成最多等待三分钟，取消或超时会终止 Codex 进程；临时提示词和输出文件在结束时清理。
- 生成期间修改了提交框时，不会用生成结果覆盖你的输入。

## 隐私与限制

插件本身不直接请求模型服务、没有埋点。插件在本机运行 `codex exec`，将提示词和文件列表交给 Codex，由 Codex 读取实际 diff。

**本机运行 CLI 不等于本地模型推理。** Codex 按自己的配置访问模型服务，变更内容可能发送到云端，模型调用也可能产生费用。`--sandbox read-only` 和 `--ephemeral` 不代表“代码绝不出网”，也不构成模型服务的数据保留承诺。

文件列表和提示词用于限定生成说明的内容，不是文件读取隔离。插件不提供离线推理或网络隔离保证，有相关合规要求时需要另外验证模型服务和网络策略。

当前暂不支持部分行、部分变更或暂存区提交（Git Staging Area），检测到此类选择时会提示，不生成可能包含未选内容的说明。其他平台的运行兼容性仍需在对应系统验证；本仓库的构建验证使用 macOS 和 GoLand 2026.1.3。

插件不保存、不回显 CLI 运行日志。CLI 非正常退出时，错误输出会写入临时文件（结束时删除），插件只读取开头部分用于匹配提示：按常见关键字（如参数不支持、未登录、模型无效）显示对应的排查建议，不展示原文。复杂问题时请在自己的终端检查 Codex 的安装、认证和模型配置。生成结果仍需人工确认后提交。

## 构建与安装

构建需要 JDK 25。默认使用固定版本的 GoLand SDK，也可以通过 `-PlocalIdePath` 使用已有的 IDE 安装，避免依赖开发者机器上的固定路径。

在本机使用 GoLand 自带 JBR 构建：

```bash
export JAVA_HOME="/Applications/GoLand.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test buildPlugin -PlocalIdePath=/Applications/GoLand.app
```

打包产物位于 `build/distributions/`。在 GoLand 的 Settings、Plugins 中选择 Install Plugin from Disk，选择 ZIP 并按提示重启 IDE。

自动测试使用本地假 CLI 验证进程行为，不请求真实模型服务。已验证的 Codex CLI 版本为 0.162.1；更低版本可能不支持插件使用的 `--ephemeral`、`--sandbox`、`-o` 参数，此时会提示升级 Codex。

## 问题反馈

请通过 [GitHub Issues](https://github.com/Driftlux/goland-commit-message/issues) 反馈问题。附上 IDE、插件和 Codex 版本及错误信息即可，不要上传私有代码、密钥或完整运行日志。
