package com.liufeismart.test;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Created by humax on 18/9/6
 */
public class NetworkUtil {
    public static boolean canOnline1() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("ping https://www.google.com");
            int exitValue = ipProcess.waitFor();
            if(exitValue == 0) {
                Log.v("NetDebug", "1可上网");
                return true;
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        Log.v("NetDebug", "1不可上网");
        return false;
    }

    public static boolean canOnline2() {
        URL url;
        try {
            url = new URL("https://www.google.com");
            Log.v("NetDebug", "开始测试..");
            InputStream in = url.openStream();
            Log.v("NetDebug", "2可上网");
            return true;
        } catch (Exception e1) {
            Log.v("NetDebug", "2不可上网");;
            url = null;
            return false;
        }
    }


}
