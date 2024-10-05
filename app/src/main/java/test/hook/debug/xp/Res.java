package test.hook.debug.xp;

import android.content.res.XModuleResources;

import com.github.kyuubiran.ezxhelper.EzXHelper;

import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import test.hook.debug.R;

public class Res {
    public static int firmware_warning = -1;
    public static int firmware_warning_title = -1;
    public static int fail_watchface = -1;
    public static int fail_firmware = -1;
    public static int fail_log = -1;
    public static int success_log = -1;

    public static void init(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(EzXHelper.getModulePath(), resparam.res);
        firmware_warning = resparam.res.addResource(modRes, R.string.firmware_warning);
        firmware_warning_title = resparam.res.addResource(modRes, R.string.firmware_warning_title);
        fail_watchface = resparam.res.addResource(modRes, R.string.fail_watchface);
        fail_firmware = resparam.res.addResource(modRes, R.string.fail_firmware);
        fail_log = resparam.res.addResource(modRes, R.string.fail_log);
        success_log = resparam.res.addResource(modRes, R.string.success_log);
    }
}
