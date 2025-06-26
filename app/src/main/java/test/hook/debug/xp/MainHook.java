package test.hook.debug.xp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Toast;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.EzXHelper;
import com.github.kyuubiran.ezxhelper.HookFactory;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import test.hook.debug.xp.ui.DialogView;
import test.hook.debug.xp.utils.DexKit;
import test.hook.debug.xp.utils.Save;
import test.hook.debug.xp.utils.SignUtils;

public class MainHook implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
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

    private static AlertDialog.Builder createWarningDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(Res.firmware_warning_title));
        builder.setMessage(context.getString(Res.firmware_warning));
        builder.setCancelable(false);
        return builder;
    }

    private static Dialog createSelectDialog(ClassLoader loader, Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        DialogView view = DialogView.create(context);

        builder.setView(view.getView());
        AlertDialog result = builder.create();

        view.addNode(Save.Type.APP.getText(), v -> {
            Save.status = Save.Type.APP;
            gotoDebugPage(loader, context);
            result.dismiss();
        });

        view.addNode(Save.Type.WATCHFACE.getText(), v -> {
            Save.status = Save.Type.WATCHFACE;
            gotoDebugPage(loader, context);
            result.dismiss();
        });

        view.addNode(Save.Type.FIRMWARE.getText(), v -> {
            AlertDialog.Builder warningDialog = createWarningDialog(context);
            warningDialog.setPositiveButton("OK", (dialog, which) -> {
                Save.status = Save.Type.FIRMWARE;
                gotoDebugPage(loader, context);
            });
            warningDialog.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            warningDialog.show();
            result.dismiss();
        });

        view.addNode(Save.Type.PULL_LOG.getText(), v -> {
            DeviceLog.pullLog(loader, new Callback<String>() {
                @Override
                public void onError(String msg, @Nullable Throwable e) {
                    Toast.makeText(context, String.format(Locale.getDefault(), "%s: %s\n%s",
                                    context.getString(Res.fail_log), msg, android.util.Log.getStackTraceString(e)),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(String path) {
                    Toast.makeText(context, String.format(Locale.getDefault(), "%s: %s", context.getString(Res.success_log), path),
                            Toast.LENGTH_LONG).show();
                }
            });
            result.dismiss();
        });

        view.addNode(Save.Type.ENCRYPT_KEY.getText(), v -> {
            EncryptKey.showEncryptKey(loader, new Callback<Map<String, String[]>>() {
                @Override
                public void onError(String msg, @Nullable Throwable e) {
                    Toast.makeText(context, String.format(Locale.getDefault(), "%s: %s\n%s",
                                    context.getString(Res.fail_log), msg, android.util.Log.getStackTraceString(e)),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(Map<String, String[]> obj) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    EditText text = new EditText(context);
                    text.setTextColor(context.getColor(android.R.color.primary_text_light));
                    text.setBackground(context.getDrawable(android.R.drawable.edit_text));
                    text.setHintTextColor(context.getColor(android.R.color.darker_gray));

                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String[]> entry : obj.entrySet()) {
                        sb.append(entry.getKey()).append(": ").append(Arrays.toString(entry.getValue())).append("\n");
                    }
                    text.setText(sb.toString());
                    builder.setView(text);
                    builder.show();
                }
            });
            result.dismiss();
        });

        return result;
    }

    /**
     * 处理应用安装
     */
    private static void onHandleApp(Object thisObj, Intent intent) {
        XposedHelpers.callMethod(thisObj, "prepareInstall",
                new Class<?>[]{String.class, Intent.class}, "thirdapp.rpk", intent);
    }

    /**
     * 处理表盘安装
     */
    private static boolean onHandleWatchFace(ClassLoader loader, Context context, Uri data) throws Throwable {
        File tmpFace = Install.saveTmpFile(context, data);
        if (tmpFace == null) {
            return false;
        }
        Install.installWatchFace(loader, tmpFace, context);
        return true;
    }

    /**
     * 处理固件安装
     */
    private static boolean onHandleFirmware(ClassLoader loader, Context context, Uri data) throws Throwable {
        File tmpFace = Install.saveTmpFile(context, data);
        if (tmpFace == null) {
            return false;
        }
        Install.invokeUpdate(loader, context, tmpFace.getAbsolutePath());
        return true;
    }

    /**
     * 国际版3.33.6i出现Banner广告，拦截广告加载
     */
    private static void interceptAd(ClassLoader classLoader) {
        try {
            Class<?> impl = ClassUtils.loadClass("com.fitness.banner.export.BannerImpl", classLoader);
            for (Method method : impl.getDeclaredMethods()) {
                if (method.getName().startsWith("getBannerListAsync")) {
                    HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> null));
                } else if (method.getName().startsWith("getBannerList")) {
                    HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> Collections.emptyList()));
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void disableReport(ClassLoader classLoader) {
        try {
            Class<?> reportImpl = ClassUtils.loadClass("com.xiaomi.fitness.statistics.OnetrackImpl", classLoader);
            for (Method method : reportImpl.getDeclaredMethods()) {
                if (!"reportData".equals(method.getName())) {
                    continue;
                }
                HookFactory.createMethodHook(method, hookFactory -> hookFactory.replace(methodHookParam -> null));
            }

        } catch (ClassNotFoundException ignore) {
        }
    }

    @SuppressLint("DiscouragedApi")
    private static void loadHook(ClassLoader classLoader) throws ClassNotFoundException {
        // 使用关于页的 Activity 初始化 EzXHelper 的 context
        Class<?> clazzAboutActivity = ClassUtils.loadClass("com.xiaomi.fitness.about.AboutActivity", null);
        Method methodOnCreate = MethodFinder.fromClass(clazzAboutActivity).filterByName("onCreate").first();
        HookFactory.createMethodHook(methodOnCreate, hookFactory -> hookFactory.before(param -> EzXHelper.initAppContext((Activity) param.thisObject, false)));

        Class<?> thirdAppDebugFragment = XposedHelpers.findClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment", classLoader);
        if (thirdAppDebugFragment == null) {
            Log.e("ThirdAppDebugFragment not found", null);
            return;
        }

        Method methodStartWebView = EntryPoint.findEntryPoint();
        if (methodStartWebView == null) {
            Log.e("Current version is not supported", null);
            return;
        }

        Log.i("Entry point " + methodStartWebView.toString(), null);

        HookFactory.createMethodHook(methodStartWebView, hookFactory -> hookFactory.before(param -> {
            // 获取用户协议字符串
            Context appContext = EzXHelper.getAppContext();
            Resources appResources = appContext.getResources();
            int identifierAboutPrivacyLicenseAgreement = appResources.getIdentifier("about_privacy_license_agreement", "string", EzXHelper.hostPackageName);
            String stringAboutPrivacyLicenseAgreement = appResources.getString(identifierAboutPrivacyLicenseAgreement);

            // 若匹配，则拦截跳转
            if (!stringAboutPrivacyLicenseAgreement.equals(param.args[1])) {
                return;
            }

            ClassLoader loader = EzXHelper.getSafeClassLoader();

            // 弹出选择当前安装模式
            Dialog dialog = createSelectDialog(loader, appContext);
            dialog.show();

            // 设置当前模式显示
            Method bindView = MethodFinder.fromClass(thirdAppDebugFragment).filterByName("bindView").firstOrNull();
            if (bindView != null) {
                HookFactory.createMethodHook(bindView, hookFactory1 -> hookFactory1.after(methodHookParam ->
                        XposedHelpers.callMethod(methodHookParam.thisObject, "setTitle",
                                new Class[]{String.class}, Save.status.getText())));
            }


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

                    Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getMActivity");

                    switch (Save.status) {
                        case APP:
                            onHandleApp(param.thisObject, arg);
                            break;
                        case WATCHFACE:
                            if (!onHandleWatchFace(loader, context, data)) {
                                Toast.makeText(context, appResources.getString(Res.fail_watchface), Toast.LENGTH_LONG).show();
                            }
                            break;
                        case FIRMWARE:
                            if (!onHandleFirmware(loader, context, data)) {
                                Toast.makeText(context, appResources.getString(Res.fail_firmware), Toast.LENGTH_LONG).show();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + Save.status);
                    }

                    return null;
                }
            });
            param.setResult(null);
        }));

        XposedHelpers.findAndHookMethod(thirdAppDebugFragment, "unInstallApp", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                return Install.unInstall(classLoader, methodHookParam.thisObject);
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
                if (Save.status == Save.Type.APP) {
                    return;
                }
                param.setResult(true);
            }
        });
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws ClassNotFoundException {
        String packageName = loadPackageParam.packageName;
        if (!"com.xiaomi.wearable".equals(packageName) && !"com.mi.health".equals(packageName)) {
            return;
        }
        EzXHelper.initHandleLoadPackage(loadPackageParam);
        EzXHelper.setLogTag("WearableDebug");
        EzXHelper.setToastTag("WearableDebug");
        DexKit.INSTANCE.initDexKit(loadPackageParam);
        interceptAd(loadPackageParam.classLoader);
        disableReport(loadPackageParam.classLoader);
        loadHook(loadPackageParam.classLoader);
        DexKit.INSTANCE.closeDexKit();
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        String packageName = resparam.packageName;
        if (!"com.xiaomi.wearable".equals(packageName) && !"com.mi.health".equals(packageName)) {
            return;
        }
        Res.init(resparam);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        EzXHelper.initZygote(startupParam);
    }
}
