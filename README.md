# 提交说明生成

Copyright 2026 spc. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

在 GoLand 提交说明工具栏点击生成按钮，根据当前变更和全局提示词生成提交说明。

模型和提示词都在 Settings、Version Control、提交说明生成 中配置。

插件不会自动填写。生成时调用本机 `codex exec`，模型列表读取 `~/.codex/config.toml` 指向的模型目录。

## 安装

已安装到：

`~/Library/Application Support/JetBrains/GoLand2026.1/plugins/goland-commit-message`

重新打包安装：

```bash
export JAVA_HOME="/Applications/GoLand.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test buildPlugin
unzip -qo build/distributions/goland-commit-message-0.1.0.zip -d /tmp/goland-commit-message-plugin
cp -R /tmp/goland-commit-message-plugin/goland-commit-message/. \
  "$HOME/Library/Application Support/JetBrains/GoLand2026.1/plugins/goland-commit-message/"
```
