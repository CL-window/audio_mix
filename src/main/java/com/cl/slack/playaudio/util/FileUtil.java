package com.cl.slack.playaudio.util;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by slack
 * on 17/2/10 下午1:45.
 */

public enum  FileUtil {

    INSTANCE;

    private static final File SDCARD_PATH = new File(Environment.getExternalStorageDirectory(), "slack");

    public File getSdcardFileDir(){
        if (!SDCARD_PATH.exists()) {
            if(!SDCARD_PATH.mkdirs()){
                Log.e("slack","mk dirs error");
            }
        }
        return SDCARD_PATH;
    }
}
