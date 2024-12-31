package test.hook.debug.xp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

/**
 * @author user
 */
public class EncryptKey {
    /**
     * 获取当前保存的EncryptKey信息
     * 返回 did -> [设备名称, EncryptKey]
     *
     * @param classLoader 当前类加载器
     * @param cb          数据回调
     */
    public static void showEncryptKey(ClassLoader classLoader, Callback<Map<String, String[]>> cb) {
        try {
            Object deviceManager = Install.getDeviceManager(classLoader);
            if (deviceManager == null) {
                cb.onError("Failed to getDeviceManager", null);
                return;
            }

            List<?> infoList = (List<?>) XposedHelpers.callMethod(deviceManager, "getDeviceList");

            Map<String, String[]> result = new HashMap<>();

            for (Object o : infoList) {
                String did = (String) XposedHelpers.callMethod(o, "getDid");
                if (did == null) {
                    continue;
                }
                String name = (String) XposedHelpers.callMethod(o, "getName");

                Object detail = XposedHelpers.callMethod(o, "getDetail");
                if (detail == null) {
                    continue;
                }
                String encryptKey = (String) XposedHelpers.callMethod(detail, "getEncryptKey");
                result.put(did, new String[]{name, encryptKey});
            }
            cb.onSuccess(result);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            cb.onError(e.getMessage(), e);
        }
    }
}
