package com.xtjun.xpForwardSms.xp.hook.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;

import com.github.xtjun.xposed.forwardSms.BuildConfig;
import com.xtjun.xpForwardSms.common.utils.XLog;
import com.xtjun.xpForwardSms.xp.helper.XposedWrapper;
import com.xtjun.xpForwardSms.xp.hook.BaseHook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
public class SmsHandlerHook extends BaseHook {

    public static final String ANDROID_PHONE_PACKAGE = "com.android.phone";

    private static final String TELEPHONY_PACKAGE = "com.android.internal.telephony";
    private static final String SMS_HANDLER_CLASS = TELEPHONY_PACKAGE + ".InboundSmsHandler";

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (ANDROID_PHONE_PACKAGE.equals(lpparam.packageName)) {
            XLog.i("SmsCode initializing");
            printDeviceInfo();
            try {
                hookSmsHandler(lpparam.classLoader);
            } catch (Throwable e) {
                XLog.e("Failed to hook SmsHandler", e);
            }
            XLog.i("SmsCode initialize completely");
        }
    }

    @SuppressWarnings("deprecation")
    private static void printDeviceInfo() {
        XLog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        XLog.i("Phone model: %s", Build.MODEL);
        XLog.i("Android version: %s", Build.VERSION.RELEASE);
        int xposedVersion;
        try {
            xposedVersion = XposedBridge.getXposedVersion();
        } catch (Throwable e) {
            xposedVersion = XposedBridge.XPOSED_BRIDGE_VERSION;
        }
        XLog.i("Xposed bridge version: %d", xposedVersion);
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    private void hookSmsHandler(ClassLoader classloader) {
        hookDispatchIntent(classloader);
    }

    private void hookDispatchIntent(ClassLoader classloader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (api >= 29)
            hookDispatchIntent29(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 ~ 9.0 (api 23 - 28)
            hookDispatchIntent23(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 ~ 5.1 (api 21 - 22)
            hookDispatchIntent21(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4 (api 19 - 20)
            hookDispatchIntent19(classloader);
        }
    }

    // Android K
    private void hookDispatchIntent19(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v19+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /* resultReceiver */ BroadcastReceiver.class,
                new DispatchIntentHook(3));
    }

    // Android L+
    private void hookDispatchIntent21(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v21+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /* resultReceiver */ BroadcastReceiver.class,
                /*           user */ UserHandle.class,
                new DispatchIntentHook(3));
    }

    // Android M+
    private void hookDispatchIntent23(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v23+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /*           opts */ Bundle.class,
                /* resultReceiver */ BroadcastReceiver.class,
                /*           user */ UserHandle.class,
                new DispatchIntentHook(4));
    }

    // Android 10+
    private void hookDispatchIntent29(ClassLoader classLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+");
        // 实际上这是一个通用的方式，不再使用精确匹配来找到对应的 Method，而使用模糊搜索的方式
        // 但是之前分 API 匹配的逻辑在以往 Android 版本的系统之中已经验证通过，故而保留原有逻辑

        Class<?> inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader);
        if (inboundSmsHandlerClass == null) {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS);
            return;
        }

        Method[] methods = inboundSmsHandlerClass.getDeclaredMethods();
        Method exactMethod = null;
        final String DISPATCH_INTENT = "dispatchIntent";
        int receiverIndex = 0;
        for (Method method : methods) {
            String methodName = method.getName();
            if (DISPATCH_INTENT.equals(methodName)) {
                exactMethod = method;

                Class[] parameterTypes = method.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    if (parameterType == BroadcastReceiver.class) {
                        receiverIndex = i;
                    }
                }

                break;
            }
        }

        if (exactMethod == null) {
            XLog.e("Method %s for Class %s cannot found", DISPATCH_INTENT, SMS_HANDLER_CLASS);
            return;
        }

        XposedWrapper.hookMethod(exactMethod, new DispatchIntentHook(receiverIndex));
    }

    private class DispatchIntentHook extends XC_MethodHook {
        private final int mReceiverIndex;

        DispatchIntentHook(int receiverIndex) {
            mReceiverIndex = receiverIndex;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex);
            } catch (Throwable e) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e);
                throw e;
            }
        }
    }

    private void beforeDispatchIntentHandler(XC_MethodHook.MethodHookParam param, int receiverIndex) {
        Intent intent = (Intent) param.args[0];
        String action = intent.getAction();

        // We only care about the initial SMS_DELIVER intent,
        // the rest are irrelevant
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action)) {
            return;
        }

        // 设置卡槽信息
        putPhoneIdAndSubIdExtra(param.thisObject, intent);

        // 转发信息
        String result = new ForwardSmsWorker(intent).parse();

        XLog.i("ForwardSmsWorker result: %s", result);
    }

    private void putPhoneIdAndSubIdExtra(Object inboundSmsHandler, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                Object phone = XposedHelpers.getObjectField(inboundSmsHandler, "mPhone");
                int phoneId = (Integer)XposedHelpers.callMethod(phone, "getPhoneId");
                XposedHelpers.callStaticMethod(SubscriptionManager.class, "putPhoneIdAndSubIdExtra", intent, phoneId);
            } catch (Exception e) {
                XLog.e("Could not update intent with subscription id", e);
            }
        }
    }

    private static Object callDeclaredMethod(String className, Object obj, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException {
        // XposedHelpers#callMethod() 方法，不能反射调用 private 的方法
        // 而本方法可以反射调用指定类的 private 方法
        Class<?> clz = XposedHelpers.findClass(className, obj.getClass().getClassLoader());
        Method method = XposedHelpers.findMethodBestMatch(clz, methodName, args);
        return method.invoke(obj, args);
    }

}
