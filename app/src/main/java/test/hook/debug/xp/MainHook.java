package test.hook.debug.xp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.EzXHelper;
import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    private static void gotoDebugPage(ClassLoader classLoader, Object activity) {
        try {
            Class<?> xmsManager = XposedHelpers.findClass("com.xms.wearable.export.XmsManager", classLoader);
            Object companionObj = XposedHelpers.getStaticObjectField(xmsManager, "Companion");

            Class<?> xmsManagerExtKt = XposedHelpers.findClass("com.xms.wearable.export.XmsManagerExtKt", classLoader);
            Object instance = XposedHelpers.callStaticMethod(xmsManagerExtKt, "getInstance", new Class<?>[]{XposedHelpers.findClass("com.xms.wearable.export.XmsManager$Companion", classLoader)}, companionObj);

            XposedHelpers.callMethod(instance, "gotoDebugPage", new Class<?>[]{Activity.class}, activity);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static Object unInstall(ClassLoader classLoader, Object thisObj) throws InvocationTargetException, IllegalAccessException {
        if (Save.sign == null) {
            return true;
        }
        Class<?> deviceManager = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManager", classLoader);
        Object companion = XposedHelpers.getStaticObjectField(deviceManager, "Companion");
        Class<?> deviceManagerExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManagerExtKt", classLoader);
        Object instance = XposedHelpers.callStaticMethod(deviceManagerExtKt, "getInstance", new Class<?>[]{XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceManager$Companion", classLoader)}, companion);
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
        return true;
    }


    @SuppressLint("DiscouragedApi")
    private static void loadHook(ClassLoader classLoader) throws ClassNotFoundException {
        // 使用关于页的 Activity 初始化 EzXHelper 的 context
        Class<?> clazzAboutActivity = ClassUtils.loadClass("com.xiaomi.fitness.about.AboutActivity", null);
        Method methodOnCreate = MethodFinder.fromClass(clazzAboutActivity).filterByName("onCreate").first();
        HookFactory.createMethodHook(methodOnCreate, hookFactory -> hookFactory.before(param -> EzXHelper.initAppContext((Activity) param.thisObject, false)));

        // 利用 WebViewUtilKt 这个未被混淆的工具类捕获启动用户协议的事件
        Class<?> clazzWebViewUtilKt = ClassUtils.loadClass("com.xiaomi.fitness.webview.WebViewUtilKt", null);
        Method methodStartWebView = MethodFinder.fromClass(clazzWebViewUtilKt).filterByName("startWebView").filterByAssignableParamTypes(String.class, String.class, boolean.class, boolean.class, Integer.class).first();
        HookFactory.createMethodHook(methodStartWebView, hookFactory -> hookFactory.before(param -> {
            // 获取用户协议字符串
            Context appContext = EzXHelper.getAppContext();
            Resources appResources = appContext.getResources();
            int identifierAboutPrivacyLicenseAgreement = appResources.getIdentifier("about_privacy_license_agreement", "string", EzXHelper.hostPackageName);
            String stringAboutPrivacyLicenseAgreement = appResources.getString(identifierAboutPrivacyLicenseAgreement);

            // 若匹配，则拦截跳转
            if (!stringAboutPrivacyLicenseAgreement.equals((String) param.args[1])) return;
            gotoDebugPage(EzXHelper.getSafeClassLoader(), appContext);
            param.setResult(null);
        }));

        Class<?> thirdAppDebugFragment = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader);
        if (thirdAppDebugFragment == null) return;

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
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws ClassNotFoundException {
        String packageName = loadPackageParam.packageName;
        if (!packageName.equals("com.xiaomi.wearable") && !packageName.equals("com.mi.health")) return;
        EzXHelper.initHandleLoadPackage(loadPackageParam);
        EzXHelper.setLogTag("WearableDebug");
        EzXHelper.setToastTag("WearableDebug");
        DexKit.INSTANCE.initDexKit(loadPackageParam);
        loadHook(loadPackageParam.classLoader);
        DexKit.INSTANCE.closeDexKit();
    }
}
