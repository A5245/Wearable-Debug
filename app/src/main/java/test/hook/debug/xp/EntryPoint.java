package test.hook.debug.xp;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.EzXHelper;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.base.StringMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;

import test.hook.debug.xp.utils.DexKit;

public class EntryPoint {
    public static Method findEntryPoint() {
        Method method = findInvoker();
        if (method == null) {
            return fallback();
        }
        return method;
    }

    private static Method findInvoker() {
        DexKitBridge bridge = DexKit.INSTANCE.getDexKitBridge();
        ClassData classData = bridge.getClassData("com.xiaomi.fitness.about.AboutActivity");
        if (classData == null) {
            return null;
        }
        MethodDataList method = classData.findMethod(FindMethod.create().matcher(
                MethodMatcher.create().name(StringMatcher.create("onCreate$lambda", StringMatchType.StartsWith))));
        for (int i = 0; i < method.size(); i++) {
            MethodData data = method.get(i);

            MethodDataList invokes = data.getInvokes();
            for (int j = 0; j < invokes.size(); j++) {
                MethodData invoker = invokes.get(j);
                ClassData invokerDeclaredClass = invoker.getDeclaredClass();
                if (invokerDeclaredClass == null) {
                    continue;
                }
                String name = invokerDeclaredClass.getName();
                if ("com.xiaomi.fitness.webview.WebViewUtilKt".equals(name)) {
                    try {
                        return invoker.getMethodInstance(EzXHelper.getClassLoader());
                    } catch (NoSuchMethodException e) {
                        Log.e("Failed to instance entry point", e);
                    }
                }
            }
        }
        return null;
    }

    private static Method fallback() {
        try {
            // 利用 WebViewUtilKt 这个未被混淆的工具类捕获启动用户协议的事件
            Class<?> clazzWebViewUtilKt = ClassUtils.loadClass("com.xiaomi.fitness.webview.WebViewUtilKt", null);
            // 小米运动健康 3.21.0
            MethodFinder startWebViewFinder = MethodFinder.fromClass(clazzWebViewUtilKt).filterByName("startWebView").filterByAssignableParamTypes(String.class, String.class, boolean.class, boolean.class, Integer.class, boolean.class, Boolean.class);
            Method methodStartWebView = startWebViewFinder.firstOrNull();
            if (methodStartWebView == null) {
                // 老版本 Hook 点
                methodStartWebView = MethodFinder.fromClass(clazzWebViewUtilKt).filterByName("startWebView").filterByAssignableParamTypes(String.class, String.class, boolean.class, boolean.class, Integer.class).firstOrNull();
            }
            return methodStartWebView;
        } catch (Exception e) {
            Log.e("EntryPoint fallback failed", e);
        }
        return null;
    }
}
