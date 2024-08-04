package test.hook.debug.xp.utils;

/**
 * @author user
 */
public class Save {
    public static byte[] sign;
    // 判断当前安装内容
    public static Type status = Type.APP;

    public enum Type {
        APP("App"), WATCHFACE("WatchFace"), FIRMWARE("Firmware");

        private final String text;

        Type(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
