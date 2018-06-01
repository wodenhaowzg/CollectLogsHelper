package com.azx.collectlogshelper;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reference to http://blog.csdn.net/way_ping_li/article/details/8487866
 * and improved some features...
 */
public class AZXRecorder {

    private LogDumper mLogDumper = null;

    static final int EVENT_RESTART_LOG = 1001;

    private RestartHandler mHandler;

    private Context mContext;

    private final Format FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS ", Locale.getDefault());
    private final String LINE_SEP = System.getProperty("line.separator");
    private ExecutorService sExecutor;
    private File mProgramWritingFile;

    public AZXRecorder(Context mContext) {
        this.mContext = mContext;
        mHandler = new RestartHandler(this);
    }

    public void start(File writingFile) {
        // 创建logcat命令行字符串
        String cmdStr = collectLogcatCommand();

        if (mLogDumper != null) {
            mLogDumper.stopDumping();
            mLogDumper = null;
        }

        mLogDumper = new LogDumper(writingFile, AZXFileHelper.LOG_SAVE_FILE_MAX_SIZE, cmdStr, mHandler);
        mLogDumper.start();
    }

    public void stop() {
        if (mLogDumper != null) {
            mLogDumper.stopDumping();
            mLogDumper = null;
        }
    }

    private String collectLogcatCommand() {
        return "logcat -b system -b main -b events -b radio -v time";
    }

    private class LogDumper extends Thread {
        final int logFileLimitation;
        final String logCmd;

        final RestartHandler restartHandler;

        private Process logcatProc;
        private BufferedReader mReader = null;
        private BufferedWriter writer = null;

        private AtomicBoolean mRunning = new AtomicBoolean(true);
        private long currentFileSize;

        LogDumper(File mSaveLogFile,
                  int fileSizeLimitation, String command,
                  RestartHandler handler) {
            logFileLimitation = fileSizeLimitation;
            logCmd = command;
            restartHandler = handler;
            try {
                writer = new BufferedWriter(new FileWriter(mSaveLogFile, true));
            } catch (Exception e) {
                throw new RuntimeException("创建文件输出流的时候报错！");
            }
        }

        void stopDumping() {
            mRunning.set(false);
        }

        @Override
        public void run() {
            try {
                if (logcatProc != null) {
                    logcatProc.destroy();
                    logcatProc = null;
                }

                String clear = "logcat -c";
                Process mClearPro = Runtime.getRuntime().exec(clear);
                processWaitFor(mClearPro);

                log("-------------------------- Start collecting system information again-----------------------");
                logcatProc = Runtime.getRuntime().exec(logCmd);
                mReader = new BufferedReader(new InputStreamReader(
                        logcatProc.getInputStream()), 1024);
                String line;
                String newline = System.getProperty("line.separator");//换行的字符串
                while (mRunning.get() && (line = mReader.readLine()) != null) {
                    if (!mRunning.get()) {
                        break;
                    }

                    if (line.length() == 0) {
                        continue;
                    }
                    if (writer != null && !line.isEmpty()) {
                        writer.write(line);
                        writer.write(newline);//换行
                        byte[] data = (line + "\n").getBytes();
                        if (logFileLimitation != 0) {
                            currentFileSize += data.length;
                            if (currentFileSize > logFileLimitation) {
                                log("The size of the system log file currently being written " +
                                        "has exceeded the limit , needs to be changed : " + currentFileSize);
                                restartHandler.sendEmptyMessage(EVENT_RESTART_LOG);
                                break;
                            }
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (logcatProc != null) {
                    logcatProc.destroy();
                    logcatProc = null;
                }
                if (mReader != null) {
                    try {
                        mReader.close();
                        mReader = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    writer = null;
                }
            }
        }
    }

    private static class RestartHandler extends Handler {
        final AZXRecorder logRecorder;

        RestartHandler(AZXRecorder logRecorder) {
            this.logRecorder = logRecorder;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_RESTART_LOG) {
                logRecorder.stop();
                File systemNewWritingFile = AZXFileHelper.getInstance().getSystemNewWritingFile(logRecorder.mContext);
                logRecorder.start(systemNewWritingFile);
            }
        }
    }

    public void setProgramWritingFile(File mProgramWritingFile) {
        this.mProgramWritingFile = mProgramWritingFile;
    }

    private void processWaitFor(Process process) {
        InputStream stderr = process.getErrorStream();
        InputStreamReader isr = new InputStreamReader(stderr);
        BufferedReader br = new BufferedReader(isr);
        String line;
        try {
            while ((line = br.readLine()) != null)
                System.out.println(line);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void print2File(final int type, final String tag, final String msg) {
        if (mProgramWritingFile == null) {
            return;
        }
        Date now = new Date(System.currentTimeMillis());
        String format = FORMAT.format(now);
        String time = format.substring(6);
        final String content = time +
                String.valueOf(type) +
                "/" +
                tag +
                ": " +
                msg +
                LINE_SEP;
        if (!input2File(content)) {
            Log.e("wzg", "Log to " + mProgramWritingFile.getAbsolutePath() + " failed!");
        }
    }

    private boolean input2File(final String input) {
        if (sExecutor == null) {
            sExecutor = Executors.newSingleThreadExecutor();
        }

        Future<Boolean> submit = sExecutor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                BufferedWriter bw = null;
                try {
                    // 如果文件过大，就新创建文件
                    if (mProgramWritingFile.length() > AZXFileHelper.LOG_SAVE_FILE_MAX_SIZE) {
                        mProgramWritingFile = AZXFileHelper.getInstance().getProgramNewWritingFile(mContext);
                    }
                    bw = new BufferedWriter(new FileWriter(mProgramWritingFile, true));
                    bw.write(input);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                } finally {
                    try {
                        if (bw != null) {
                            bw.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        try {
            return submit.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void log(String msg) {
        Log.i("wzg", msg);
    }
}