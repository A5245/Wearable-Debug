package test.hook.debug.xp;

import org.jetbrains.annotations.Nullable;

/**
 * @author user
 */
public interface Callback<T> {
    /**
     * 异常消息回调
     *
     * @param msg 错误描述
     */
    void onError(String msg, @Nullable Throwable e);

    /**
     * 成功回调
     *
     * @param obj 参数
     */
    void onSuccess(T obj);
}