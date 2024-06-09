package test.hook.debug.xp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.widget.ProgressBar;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.EzXHelper;
import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import test.hook.debug.xp.utils.DexKit;
import test.hook.debug.xp.utils.Save;
import test.hook.debug.xp.utils.SignUtils;

public class MainHook implements IXposedHookLoadPackage {
    public MainHook() {
    }

    private static void gotoDebugPage(ClassLoader classLoader, Context activity) {
        try {
            Class<?> xmsManager = XposedHelpers.findClass("com.xms.wearable.export.XmsManager", classLoader);
            Object companionObj = XposedHelpers.getStaticObjectField(xmsManager, "Companion");

            Class<?> xmsManagerExtKt = XposedHelpers.findClass("com.xms.wearable.export.XmsManagerExtKt", classLoader);
            Object instance = XposedHelpers.callStaticMethod(xmsManagerExtKt, "getInstance", new Class<?>[]{XposedHelpers.findClass("com.xms.wearable.export.XmsManager$Companion", classLoader)}, companionObj);

            XposedHelpers.callMethod(instance, "gotoDebugPage", new Class<?>[]{Activity.class}, activity);
        } catch (Throwable e) {
            Log.e(e, "gotoDebugPage");
        }
    }

    /**
     * 读取指定表盘文件ID
     *
     * @param file 表盘文件路径
     * @return 表盘文件ID
     */
    private static String getWatchFaceId(File file) {
        if (!file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (FileInputStream stream = new FileInputStream(file)) {
            if (stream.skip(40) != 40) {
                return null;
            }
            int read;
            while ((read = stream.read()) != 0) {
                builder.append((char) read);
            }
        } catch (IOException e) {
            Log.e(e, "getWatchFaceId");
        }
        return builder.toString();
    }

    /**
     * 安装表盘文件
     *
     * @param loader 当前类加载器
     * @param file   表盘文件路径
     * @param object ThirdAppDebugFragment的上下文
     */
    private static void installWatchFace(ClassLoader loader, File file, Object object) {
        try {
            String watchFaceId = getWatchFaceId(file);
            if (watchFaceId == null) {
                Log.e("Failed to get id from " + file.getAbsolutePath(), null);
                return;
            }

            Class<?> model = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.viewmodel.FaceDetailViewModel", loader);
            Object instance = model.newInstance();

            Object controller = XposedHelpers.getObjectField(instance, "faceInstallController");

            Context context = (Context) XposedHelpers.callMethod(object, "getMActivity");

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(false);

            builder.setView(progressBar);
            AlertDialog dialog = builder.create();
            dialog.show();

            Class<?> callbackClass = XposedHelpers.findClass("com.xiaomi.fitness.watch.face.install.FaceInstallPushCallback", loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                try {
                    switch (method.getName()) {
                        case "onProgress": {
                            int pos = (int) args[0];
                            Log.i("p: " + pos, null);
                            progressBar.setProgress(pos);
                            break;
                        }
                        case "onFinish": {
                            boolean success = (boolean) args[0];
                            int code = (int) args[1];
                            Log.i("success: " + success + " code: " + code, null);
                            dialog.dismiss();
                            break;
                        }
                    }
                } catch (Throwable e) {
                    Log.e(e, method.toString());
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
                return null;
            });

            XposedHelpers.callMethod(controller, "doInstall", new Class<?>[]{
                    String.class, String.class, Integer.class, callbackClass
            }, file.getAbsolutePath(), watchFaceId, 0, callback);
        } catch (Throwable e) {
            Log.e(e, "installWatchFace");
        }
    }

    private static Object unInstall(ClassLoader classLoader, Object thisObj) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
        if (Save.sign == null) {
            return true;
        }
        Class<?> deviceManager = ClassUtils.loadFirstClass("com.xiaomi.fitness.device.manager.export.DeviceManager", "com.xiaomi.fitness.device.manager.export.WearableDeviceManager");
        Object companion = XposedHelpers.getStaticObjectField(deviceManager, "Companion");
        Class<?> deviceManagerExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManagerExtKt", classLoader);
        Object instance = ClassUtils.invokeStaticMethodBestMatch(deviceManagerExtKt, "getInstance", null, companion);
        Object deviceModel = XposedHelpers.callMethod(instance, "getCurrentDeviceModel");
        if (deviceModel == null || !(boolean) XposedHelpers.callMethod(deviceModel, "isDeviceConnected")) {
            return true;
        }

        Object did = XposedHelpers.callMethod(deviceModel, "getDid");

        Object pkgName = XposedHelpers.getObjectField(thisObj, "pkgName");
        Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.xms.wearable.extensions.DeviceModelExtKt", classLoader);
        Class<?> callback = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment$unInstallApp$1", classLoader);
        Object callbackObj = XposedHelpers.newInstance(callback, new Class<?>[]{XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader), String.class}, thisObj, did);

        Method uninstallApp = MethodFinder.fromClass(deviceModelExtKt).filterByName("uninstallApp").first();
        uninstallApp.setAccessible(true);
        uninstallApp.invoke(deviceModelExtKt, deviceModel, pkgName, Save.sign, callbackObj);
        return false;
    }


    @SuppressLint("DiscouragedApi")
    private static void loadHook(ClassLoader classLoader) throws ClassNotFoundException {
        // 使用关于页的 Activity 初始化 EzXHelper 的 context
        Class<?> clazzAboutActivity = ClassUtils.loadClass("com.xiaomi.fitness.about.AboutActivity", null);
        Method methodOnCreate = MethodFinder.fromClass(clazzAboutActivity).filterByName("onCreate").first();
        HookFactory.createMethodHook(methodOnCreate, hookFactory -> hookFactory.before(param -> EzXHelper.initAppContext((Activity) param.thisObject, false)));

        // 利用 WebViewUtilKt 这个未被混淆的工具类捕获启动用户协议的事件
        Class<?> clazzWebViewUtilKt = ClassUtils.loadClass("com.xiaomi.fitness.webview.WebViewUtilKt", null);

        // 小米运动健康 3.21.0
        MethodFinder startWebViewFinder = MethodFinder.fromClass(clazzWebViewUtilKt).filterByName("startWebView").filterByAssignableParamTypes(String.class, String.class, boolean.class, boolean.class, Integer.class, boolean.class, Boolean.class);
        Method methodStartWebView = startWebViewFinder.firstOrNull();
        if (methodStartWebView == null) {
            // 老版本 Hook 点
            methodStartWebView = MethodFinder.fromClass(clazzWebViewUtilKt).filterByName("startWebView").filterByAssignableParamTypes(String.class, String.class, boolean.class, boolean.class, Integer.class).firstOrNull();
        }
        if (methodStartWebView == null) {
            Log.e("Current version is not supported", null);
            return;
        }

        Class<?> thirdAppDebugFragment = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader);
        if (thirdAppDebugFragment == null) return;

        HookFactory.createMethodHook(methodStartWebView, hookFactory -> hookFactory.before(param -> {
            // 获取用户协议字符串
            Context appContext = EzXHelper.getAppContext();
            Resources appResources = appContext.getResources();
            int identifierAboutPrivacyLicenseAgreement = appResources.getIdentifier("about_privacy_license_agreement", "string", EzXHelper.hostPackageName);
            String stringAboutPrivacyLicenseAgreement = appResources.getString(identifierAboutPrivacyLicenseAgreement);

            // 若匹配，则拦截跳转
            if (!stringAboutPrivacyLicenseAgreement.equals(param.args[1])) return;

            // 弹出选择当前安装模式
            AlertDialog.Builder builder = new AlertDialog.Builder(appContext);
            builder.setPositiveButton("WatchFace", (dialog, which) -> {
                Save.isApp = false;
                gotoDebugPage(EzXHelper.getSafeClassLoader(), appContext);
            });
            builder.setNegativeButton("App", (dialog, which) -> {
                Save.isApp = true;
                gotoDebugPage(EzXHelper.getSafeClassLoader(), appContext);
            });

            builder.show();

            // 拦截文件选择
            XposedHelpers.findAndHookMethod(thirdAppDebugFragment, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    if (((int) param.args[0]) != 10 || ((int) param.args[1]) != -1) {
                        return null;
                    }
                    Intent arg = (Intent) param.args[2];
                    Uri data = arg.getData();
                    if (data == null) {
                        return null;
                    }

                    if (Save.isApp) {
                        XposedHelpers.callMethod(param.thisObject, "prepareInstall",
                                new Class<?>[]{String.class, Intent.class}, "thirdapp.rpk", arg);
                        return null;
                    }

                    // 保存到缓存目录
                    File tmpFace = new File(appContext.getCacheDir(), "tmp_face");
                    try (FileOutputStream stream = new FileOutputStream(tmpFace)) {
                        byte[] bytes = new byte[0x400];
                        try (InputStream inputStream = appContext.getContentResolver().openInputStream(data)) {
                            int read;
                            while ((read = inputStream.read(bytes)) != -1) {
                                stream.write(bytes, 0, read);
                            }
                        }
                    }
                    installWatchFace(EzXHelper.getSafeClassLoader(), tmpFace, param.thisObject);
                    return null;
                }
            });
            param.setResult(null);
        }));

        XposedHelpers.findAndHookMethod(thirdAppDebugFragment, "unInstallApp", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                return unInstall(classLoader, methodHookParam.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(thirdAppDebugFragment, "sendThirdAppFile", File.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Save.sign = SignUtils.generateSign((File) param.args[0]);
            }
        });

        // 安装表盘时不限制包名
        XposedHelpers.findAndHookMethod(thirdAppDebugFragment, "isPackageReady", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (Save.isApp) return;

                param.setResult(true);
            }
        });
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws ClassNotFoundException {
        String packageName = loadPackageParam.packageName;
        if (!"com.xiaomi.wearable".equals(packageName) && !"com.mi.health".equals(packageName))
            return;
        EzXHelper.initHandleLoadPackage(loadPackageParam);
        EzXHelper.setLogTag("WearableDebug");
        EzXHelper.setToastTag("WearableDebug");
        DexKit.INSTANCE.initDexKit(loadPackageParam);
        loadHook(loadPackageParam.classLoader);
        DexKit.INSTANCE.closeDexKit();
    }
}
