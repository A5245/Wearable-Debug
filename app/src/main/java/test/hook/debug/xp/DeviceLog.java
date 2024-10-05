package test.hook.debug.xp;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.finders.MethodFinder;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.XposedHelpers;

public class DeviceLog {
    /**
     * 获取设备日志输出路径
     *
     * @param classLoader 当前类加载器
     * @return 日志输出路径
     */
    private static String getOutputDir(ClassLoader classLoader) {
        Class<?> feedbackFileUtils = XposedHelpers.findClass("com.xiaomi.fitness.feedback.util.FeedbackFileUtils", classLoader);
        Object instance = XposedHelpers.getStaticObjectField(feedbackFileUtils, "INSTANCE");

        return (String) XposedHelpers.callMethod(instance, "getDebiceLogDirPath");
    }

    /**
     * 拉取设备日志
     *
     * @param classLoader 当前类加载器
     * @param cb          事件回调
     */
    public static void pullLog(ClassLoader classLoader, Callback cb) {
        try {
            Object currentDevice = Install.getCurrentDevice(classLoader);

            Class<?> deviceModelExtKt = XposedHelpers.findClass("com.xiaomi.fitness.device.manager.DeviceModelExtKt", classLoader);
            boolean isWear = (boolean) ClassUtils.invokeStaticMethodBestMatch(deviceModelExtKt, "isWearOS", null, currentDevice);
            if (isWear) {
                cb.onError("Not support wearos device");
                return;
            }

            Class<?> getDeviceLog = XposedHelpers.findClass("com.xiaomi.fitness.feedback.bugreport.GetDeviceLog", classLoader);
            Object instance = XposedHelpers.getStaticObjectField(getDeviceLog, "INSTANCE");
            Method method = MethodFinder.fromClass(getDeviceLog).filterByName("syncLogFromBleDevice").first();

            Class<?> callbackClass = method.getParameterTypes()[1];

            Object callback = Proxy.newProxyInstance(classLoader, new Class[]{callbackClass}, (proxy, method1, args) -> {
                String name = method1.getName();
                if ("onError".equals(name)) {
                    String s = (String) args[0];
                    int type = (int) args[1];
                    int code = (int) args[2];
                    cb.onError("syncLogFromBleDevice onError: " + s + " type=" + type + "**code=" + code);
                } else if ("onSuccess".equals(name)) {
                    String s = (String) args[0];
                    int v = (int) args[1];
                    // com.xiaomi.fitness.device.contact.export.SyncResult
                    Object syncResult = args[2];
                    cb.onSuccess(syncResult.toString());
                }
                return null;
            });
            XposedHelpers.callMethod(instance, "syncLogFromBleDevice", currentDevice, callback);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            Log.e(e, "pullLog");
        }
    }

    public interface Callback {
        /**
         * 异常消息回调
         *
         * @param msg 错误描述
         */
        void onError(String msg);

        /**
         * 成功回调
         *
         * @param path 输出路径
         */
        void onSuccess(String path);
    }
}
