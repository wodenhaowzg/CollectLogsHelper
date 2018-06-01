package com.azx.collectlogshelper;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Created by wangzhiguo on 17/12/4.
 */
public class AZXWrite {

    private static final int TAG_TYPE_NONE = 9;
    private static final int TAG_TYPE_JNI_CALLBACK = 10;
    private static final int TAG_TYPE_FUN_ERROR = 11;

    private static final int LOG_EVENT = 0;
    public static boolean IS_DEBUG = true;
    static final String TAG = "WSTECH";

    // callback log tag
    static final String JNI_CALLBACK = "JNI_CALLBACK";
    static final String FUN_ERROR = "FUN_ERROR";

    private static final char[] T = new char[]{'V', 'D', 'I', 'W', 'E', 'A'};
    private static LogFileHandler mHandler;

    public static void init(AZXRecorder mLogRecorder) {
        mHandler = new LogFileHandler(mLogRecorder);
    }

    public static void i(String msg) {
        if (!IS_DEBUG) {
            return;
        }
        Log.w(TAG, msg);
        sendMessage(T[2], TAG_TYPE_NONE, msg);
    }

    public static void d(String msg) {
        if (!IS_DEBUG) {
            return;
        }
        Log.w(TAG, msg);
        sendMessage(T[1], TAG_TYPE_NONE, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        sendMessage(T[3], TAG_TYPE_NONE, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        sendMessage(T[4], TAG_TYPE_NONE, msg);
    }

    public static void i(String tag, String msg) {
        if (!IS_DEBUG) {
            return;
        }
        Log.w(tag, msg);
        sendMessage(T[2], TAG_TYPE_NONE, msg);
    }

    public static void d(String tag, String msg) {
        if (!IS_DEBUG) {
            return;
        }
        Log.w(tag, msg);
        sendMessage(T[1], TAG_TYPE_NONE, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        sendMessage(T[3], TAG_TYPE_NONE, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        sendMessage(T[4], TAG_TYPE_NONE, msg);
    }

    public static void ecls(String tag, String msg) {
        Log.w(TAG, "Class <" + tag + ">  -> " + msg);
        sendMessage(T[1], TAG_TYPE_NONE, "Class <" + tag + ">  -> " + msg);
    }

    public static void jniCall(String methodName, String content) {
        Log.d(JNI_CALLBACK, " METHOD = " + methodName + " --> " + content);
        sendMessage(T[2], TAG_TYPE_JNI_CALLBACK, " METHOD = " + methodName + " --> " + content);
    }

    public static void funEmptyError(String funName, String varName, String args) {
        w(FUN_ERROR, "Invoke <" + funName + "> error , the var <" + varName + "> " +
                "is null! args : " + args);
        sendMessage(T[2], TAG_TYPE_FUN_ERROR, "Invoke <" + funName + "> error , the var <" + varName + "> " +
                "is null! args : " + args);
    }

    private static void sendMessage(int type, int tagType, String msg) {
        Message obtain = Message.obtain(mHandler, LOG_EVENT);
        obtain.arg1 = type;
        obtain.arg2 = tagType;
        obtain.obj = msg;
        obtain.sendToTarget();
    }

    private static class LogFileHandler extends Handler {

        private AZXRecorder mLogRecorder;

        LogFileHandler(AZXRecorder mLogRecorder) {
            this.mLogRecorder = mLogRecorder;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == LOG_EVENT) {

                int tagType = msg.arg2;
                String tag = TAG;
                if (tagType == TAG_TYPE_JNI_CALLBACK) {
                    tag = JNI_CALLBACK;
                } else if (tagType == TAG_TYPE_FUN_ERROR) {
                    tag = FUN_ERROR;
                } else if (tagType == TAG_TYPE_NONE) {
                    tag = TAG;
                }
                mLogRecorder.print2File(msg.arg1, tag, (String) msg.obj);
            }
        }
    }
}
