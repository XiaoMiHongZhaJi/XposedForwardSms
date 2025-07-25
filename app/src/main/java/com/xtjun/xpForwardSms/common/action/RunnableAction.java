package com.xtjun.xpForwardSms.common.action;

import android.content.SharedPreferences;
import com.xtjun.xpForwardSms.common.action.entity.SmsMsg;

/**
 * Runnable + Action + Callable
 */
public abstract class RunnableAction extends CallableAction implements Runnable {

    public RunnableAction(SmsMsg smsMsg) {
        super(smsMsg);
    }

    @Override
    public void run() {
        call();
    }
}
