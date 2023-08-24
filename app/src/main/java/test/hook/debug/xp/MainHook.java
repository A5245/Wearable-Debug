package test.hook.debug.xp;

import android.app.Activity;
import android.view.View;

import com.github.kyuubiran.ezxhelper.EzXHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import test.hook.debug.xp.utils.DexKit;
import test.hook.debug.xp.utils.Save;
import test.hook.debug.xp.utils.V2;

/**
 * Only tested on Mi Fitness 3.19.0i
 *
 * @author user
 */
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

    private static Object unInstall(ClassLoader classLoader, Object thisObj) {
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

        XposedHelpers.callStaticMethod(deviceModelExtKt, "uninstallApp", new Class<?>[]{XposedHelpers.findClass("com.xiaomi.fitness.device.manager.export.DeviceModel", classLoader), String.class, byte[].class, XposedHelpers.findClass("xi3", classLoader)}, deviceModel, pkgName, Save.sign, callbackObj);
        return true;
    }

    private static byte[] sha1(byte[] data) {
        try {
            MessageDigest instance = MessageDigest.getInstance("SHA1");
            return instance.digest(data);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] generateSign(File file) throws Exception {
        byte[] sign;
        try (V2 v2 = new V2(file)) {
            v2.skipZipEntry();
            sign = v2.parseSign();
        }
        if (sign == null) {
            return null;
        }
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(sign));
        return sha1(certificate.getPublicKey().getEncoded());
    }

    private static void loadHook(ClassLoader classLoader) {
        Class<?> targetClass = XposedHelpers.findClass("ml", classLoader);
        if (targetClass == null) {
            return;
        }
        if (XposedHelpers.findMethodExactIfExists(targetClass, "onClick", View.class) == null) {
            return;
        }
        XposedHelpers.findAndHookMethod(targetClass, "onClick", View.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                Field a = XposedHelpers.findFieldIfExists(targetClass, "a");
                if (a == null) {
                    return null;
                }
                a.setAccessible(true);
                Object activity = a.get(methodHookParam.thisObject);
                gotoDebugPage(classLoader, activity);
                return null;
            }
        });
        Class<?> thirdAppDebugFragment = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader);
        if (thirdAppDebugFragment == null) {
            return;
        }

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
                Save.sign = generateSign((File) param.args[0]);
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
