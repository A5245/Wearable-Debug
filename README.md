# Wearable Debug

Xposed插件启用Mi Fitness第三方小程序安装卸载。

激活插件后会将Profile->About this app->User Agreemnt页面替换为隐藏的小程序安装界面。

## 使用

### 表盘安装

点击Profile->About this app->User Agreemnt后在弹窗中选择WatchFace。不需要输入包名信息，点击install third app选择表盘文件。等待安装完成。

### 小程序安装

点击Profile->About this app->User Agreemnt后在弹窗中选择App。先输入包名后点击install third app选择小程序文件，安装时可以随便输入包名。仅卸载时需要完整包名。

## 注意

当前代码兼容Google版的Mi Fitness 3.27.1i版本、国内版小米运动健康3.25.0版本。国内版未测试表盘安装。