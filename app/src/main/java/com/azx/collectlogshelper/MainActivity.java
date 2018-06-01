package com.azx.collectlogshelper;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.yanzhenjie.permission.AndPermission;

import java.io.File;

/**
 * Created by wangzhiguo on 18/5/28.
 */

public class MainActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AndPermission.with(this)
                .permission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .start();

        // 初始化存储log的目录
        AZXFileHelper.getInstance().initLogSaveDirPath(this);
        // 初始化log收集工具
        AZXRecorder mLogRecorder = new AZXRecorder(this);
        // 获取存放系统日志的文件
        File writingFile = AZXFileHelper.getInstance().
                getWritingFile(this, AZXFileHelper.LOG_FILE_TYPE_SYSTEM);
        // 开始收集logcat输出的日志
        mLogRecorder.start(writingFile);

//        // 获取存放程序日志的文件
//        File writingProgramFile = AZXFileHelper.getInstance().
//                getWritingFile(this, AZXFileHelper.LOG_FILE_TYPE_PROGRAM);
//        // 设置到log手机工具中
//        mLogRecorder.setProgramWritingFile(writingProgramFile);
//        // 初始化程序日志收集工具
//        AZXWrite.init(mLogRecorder);

        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 10000;
                while (count > 0) {
                    count--;
                    Log.d("wzg", "这是测试的程序输出log : " + count);
                }
            }
        }).start();
    }
}
