package com.liufeismart.test;

import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.interfaces.AvsResponse;

/**
 * Created by humax on 18/9/5
 */
public interface AvsListenerInterface {
    AsyncCallback<AvsResponse, Exception> getRequestCallback();
}
