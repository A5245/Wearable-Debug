# Wearable Debug

Xposed插件启用Mi Fitness第三方小程序安装卸载，表盘安装，固件安装。

激活插件后会将Profile->About this app->User Agreemnt页面替换为隐藏的小程序安装界面。

## 注意

当前代码仅**8Pro**在Google版的**Mi Fitness 3.30.0i**版本测试正常。

## 使用

### 表盘安装

点击Profile->About this app->User Agreemnt后在弹窗中选择WatchFace。不需要输入包名，点击install third app选择表盘文件。等待安装完成。

### 固件安装

**注意该功能可能导致设备变砖，请谨慎使用**

点击Profile->About this app->User Agreemnt后在弹窗中选择Firmware。不需要输入包名，点击install third app选择固件文件。在更新页面点击Download。

### 小程序安装

点击Profile->About this app->User Agreemnt后在弹窗中选择App。先输入包名后点击install third app选择小程序文件，安装时可以随便输入包名。仅卸载时需要完整包名。

