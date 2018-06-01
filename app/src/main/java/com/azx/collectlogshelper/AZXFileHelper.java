package com.azx.collectlogshelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * author:wangzg
 */
public class AZXFileHelper {

    public static final int LOG_FILE_TYPE_PROGRAM = 0;
    public static final int LOG_FILE_TYPE_SYSTEM = 1;

    /**
     * 保存在手机里面的文件名
     */
    private static final String FILE_NAME = "LogConfig";

    /**
     * SD卡的根目录路径
     */
    private static String SDCARD_DIR_PATH;

    /**
     * 存放Log文件的根目录的名字
     */
    private static String LOG_SAVE_DIR_NAME = "AZXWrite";
    /**
     * 存放Log文件的根目录的路径
     */
    private static String LOG_SAVE_DIR_PATH;

    private final static String LOG_SAVE_DIR_PROGARM_NAME = "Program";
    private static String LOG_SAVE_DIR_PROGARM_PATH;

    private final static String LOG_SAVE_DIR_SYSTEM_NAME = "System";
    private static String LOG_SAVE_DIR_SYSTEM_PATH;

    private final static String LOG_SAVE_FILE_SYSTEM_PREFIX = "SystemPre";
    private final static String LOG_SAVE_FILE_PROGRAM_PREFIX = "ProgramPre";

    private final static String LOG_SP_SAVE_SYSTEM_KEY = "saveSystemLogFile";
    private final static String LOG_SP_SAVE_PROGRAM_KEY = "saveProgramLogFile";

    private static final int LOG_SAVE_FILE_MAX_NUM = 5;
    static final int LOG_SAVE_FILE_MAX_SIZE = 5 * 1024 * 1024;

    private static AZXFileHelper holder;

    private AZXFileHelper() {
    }

    public static AZXFileHelper getInstance() {
        if (holder == null) {
            synchronized (AZXFileHelper.class) {
                if (holder == null) {
                    holder = new AZXFileHelper();
                }
            }
        }
        return holder;
    }

    /**
     * Author: wangzg <br/>
     * Time: 2017-12-7 17:37:01<br/>
     * Description: 创建日志目录WLog.
     *
     * @param mContext the context
     */
    public void initLogSaveDirPath(Context mContext) {
        String mSDCardRootPath;
        boolean sdExist = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        if (!sdExist) {
            // --data/data/AZXWrite
            mSDCardRootPath = mContext.getFilesDir().getParent();
            createLogDirPhoneHardDrive(mSDCardRootPath);
        } else {
            mSDCardRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            File sdRoot = mContext.getExternalFilesDir(null);
            if (sdRoot == null) {
                log("SD Android Dir can't be used!");
                // -- mnt/sdcard/AZXWrite
                boolean isCreated = createSaveDir(mSDCardRootPath);
                if (isCreated) {
                    SDCARD_DIR_PATH = mSDCardRootPath;
                    LOG_SAVE_DIR_PATH = SDCARD_DIR_PATH + File.separator + LOG_SAVE_DIR_NAME;
                    log("Creating a log folder succeeded! SD can be used, save path is ：" + LOG_SAVE_DIR_PATH);
                } else {
                    log("SD can be used, but create log folder failed! change to the phone's own hard drive");
                    // --data/data/AZXWrite
                    mSDCardRootPath = mContext.getFilesDir().getParent();
                    createLogDirPhoneHardDrive(mSDCardRootPath);
                }
            } else {
                // --mnt/sdcart/Android/包名/files/AZXWrite
                mSDCardRootPath = sdRoot.getAbsolutePath();
                boolean isCreated = createSaveDir(mSDCardRootPath);
                if (isCreated) {
                    SDCARD_DIR_PATH = mSDCardRootPath;
                    LOG_SAVE_DIR_PATH = SDCARD_DIR_PATH + File.separator + LOG_SAVE_DIR_NAME;
                    log("Creating a log folder succeeded! SD Android Dir can be used, save path is ：" + LOG_SAVE_DIR_PATH);
                } else {
                    log("SD Android Dir can use , but create dir failed! change to SD memory");
                    // -- mnt/sdcard/AZXWrite
                    mSDCardRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    boolean isCreatedSD = createSaveDir(mSDCardRootPath);
                    if (isCreatedSD) {
                        SDCARD_DIR_PATH = mSDCardRootPath;
                        LOG_SAVE_DIR_PATH = SDCARD_DIR_PATH + File.separator + LOG_SAVE_DIR_NAME;
                        log("Creating a log folder succeeded second! SD Android Dir can be used, save path is ：" + LOG_SAVE_DIR_PATH);
                    } else {
                        log("SD can use , but create dir failed! change to mobile self memory");
                        // --data/data/AZXWrite
                        mSDCardRootPath = mContext.getFilesDir().getParent();
                        createLogDirPhoneHardDrive(mSDCardRootPath);
                    }
                }
            }
        }
    }

    private void deleteMoreFiles(File[] files) {
        int currentFileSize = files.length;
        LongSparseArray<String> tempMap = new LongSparseArray<>();
        long[] tempCache = new long[currentFileSize];
        for (int i = 0; i < currentFileSize; i++) {
            File file = files[i];
            String[] split = file.getName().split("~");
            long time = Long.valueOf(split[2]);
            tempCache[i] = time;
            log("Traverse the current directory : " + file.getAbsolutePath() + " , Time : " + time);
            tempMap.put(time, file.getAbsolutePath());
        }
        sort(tempCache);
        int deleteNum = currentFileSize - LOG_SAVE_FILE_MAX_NUM;
        for (int j = 0; j < deleteNum; j++) {
            String filePath = tempMap.get(tempCache[j]);
            File deleteFile = new File(filePath);
            boolean delete = deleteFile.delete();
            if (!delete) {
                throw new RuntimeException("删除多余的日志文件失败！name : " + deleteFile.getName());
            }
            log("Successfully deleted the log file : " + deleteFile.getAbsolutePath());
        }
    }

    public File getWritingFile(Context mContext, int mLogFileType) {
        File mCurrentWritingFile;
        String initLogFileName;
        String initLogFilePath;
        String mSpSaveTypeName;
        if (mLogFileType == LOG_FILE_TYPE_SYSTEM) {
            initLogFileName = LOG_SAVE_DIR_SYSTEM_NAME;
            mSpSaveTypeName = LOG_SP_SAVE_SYSTEM_KEY;
        } else {
            initLogFileName = LOG_SAVE_DIR_PROGARM_NAME;
            mSpSaveTypeName = LOG_SP_SAVE_PROGRAM_KEY;
        }
        File temp = new File(LOG_SAVE_DIR_PATH, initLogFileName);
        if (!temp.exists()) {
            boolean mkdir = temp.mkdir();
            if (!mkdir) {
                throw new RuntimeException("创建文件夹失败！" + temp.getAbsolutePath());
            }
        }

        if (mLogFileType == LOG_FILE_TYPE_PROGRAM) {
            LOG_SAVE_DIR_PROGARM_PATH = temp.getAbsolutePath();
            initLogFilePath = LOG_SAVE_DIR_PROGARM_PATH;
        } else {
            LOG_SAVE_DIR_SYSTEM_PATH = temp.getAbsolutePath();
            initLogFilePath = LOG_SAVE_DIR_SYSTEM_PATH;
        }

        checkFileSizeAndDelete(mLogFileType);
        String saveLogFile = (String) getParam(mContext, mSpSaveTypeName);
        if (TextUtils.isEmpty(saveLogFile)) {
            String newLogFileName = createNewLogFileName(mLogFileType);
            mCurrentWritingFile = new File(initLogFilePath, newLogFileName);
            try {
                boolean isCreated = mCurrentWritingFile.createNewFile();
                if (!isCreated) {
                    throw new RuntimeException("没有从SP获取到日志文件，但创建新的日志文件也失败！");
                }
            } catch (IOException e) {
                throw new RuntimeException("没有从SP获取到日志文件，但创建新的日志文件也异常！path: "
                        + mCurrentWritingFile.getAbsolutePath() + " | reason : " +
                        e.getLocalizedMessage());
            }
            // 存入SP内
            setParam(mContext, mSpSaveTypeName, newLogFileName);
        } else {
            mCurrentWritingFile = new File(initLogFilePath, saveLogFile);
        }
        log("The path to the log file that is currently written :" +
                " " + mCurrentWritingFile.getAbsolutePath());
        return mCurrentWritingFile;
    }

    File getProgramNewWritingFile(Context mContext) {
        String newLogFileName = createNewLogFileName(LOG_FILE_TYPE_PROGRAM);
        File mCurrentWritingFile = new File(LOG_SAVE_DIR_PROGARM_PATH, newLogFileName);
        try {
            boolean isCreated = mCurrentWritingFile.createNewFile();
            if (!isCreated) {
                throw new RuntimeException("创建新的Program日志文件失败！");
            }
        } catch (IOException e) {
            throw new RuntimeException("创建新的Program日志文件异常！" + e.getLocalizedMessage());
        }
        // 存入SP内
        setParam(mContext, LOG_SP_SAVE_PROGRAM_KEY, newLogFileName);
        // 每新建一个Log文件，都要检查当前目录的文件数量是否大于5个
        checkFileSizeAndDelete(LOG_FILE_TYPE_PROGRAM);
        return mCurrentWritingFile;
    }

    File getSystemNewWritingFile(Context mContext) {
        String newLogFileName = createNewLogFileName(LOG_FILE_TYPE_SYSTEM);
        File mCurrentWritingFile = new File(LOG_SAVE_DIR_SYSTEM_PATH, newLogFileName);
        try {
            boolean isCreated = mCurrentWritingFile.createNewFile();
            if (!isCreated) {
                throw new RuntimeException("创建新的日志文件失败！");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("创建新的日志文件失败！");
        }
        // 存入SP内
        setParam(mContext, LOG_SP_SAVE_SYSTEM_KEY, newLogFileName);
        log("上一个日志文件已写满，创建新的日志文件成功 " + mCurrentWritingFile.getAbsolutePath() + " 检查目录文件数量");
        // 每新建一个Log文件，都要检查当前目录的文件数量是否大于5个
        checkFileSizeAndDelete(LOG_FILE_TYPE_SYSTEM);
        return mCurrentWritingFile;
    }

    private void checkFileSizeAndDelete(int logFileDirType) {
        String mCheckPath;
        if (logFileDirType == LOG_FILE_TYPE_PROGRAM) {
            mCheckPath = LOG_SAVE_DIR_PROGARM_PATH;
        } else {
            mCheckPath = LOG_SAVE_DIR_SYSTEM_PATH;
        }
        // 检测目录下有多少文件，是否超过LOG_SAVE_FILE_MAX_NUM的值
        File temp = new File(mCheckPath);
        int fileSize = temp.listFiles().length;
        log("检查的目录路径-" + temp.getAbsolutePath() + " , 数量-" + fileSize);
        if (fileSize > LOG_SAVE_FILE_MAX_NUM) {
            deleteMoreFiles(temp.listFiles());
        }
    }

    private boolean createSaveDir(String path) {
        // 创建数据文件夹，如果不成功则程序不能正常运行！
        boolean isExistRootDir = createDefSaveDir(path);
        if (isExistRootDir) {
            boolean isExistTestFile = createTestFile(path);
            if (isExistTestFile) {
                return true;
            }
        }
        return false;
    }

    private boolean createDefSaveDir(String path) {
        File target = new File(path, LOG_SAVE_DIR_NAME);
        if (!target.exists()) {
            File temp = new File(path, LOG_SAVE_DIR_NAME + "_" + System.currentTimeMillis());
            boolean isCreateDir = temp.mkdirs();
            if (isCreateDir && temp.exists()) {
                boolean isRename = temp.renameTo(target);
                if (isRename) {
                    return true;
                } else {
                    throw new RuntimeException("创建Log文件夹时，更改名字失败!");
                }
            }
        } else {
            return true;
        }
        return false;
    }

    private boolean createTestFile(String path) {
        String targetPath = path + File.separator + LOG_SAVE_DIR_NAME;
        File temp = new File(targetPath, LOG_SAVE_DIR_NAME + "_" + System.currentTimeMillis());
        try {
            boolean isTestCreate = temp.createNewFile();
            if (isTestCreate && temp.exists()) {
                boolean delete = temp.delete();
                if (delete) {
                    return true;
                } else {
                    throw new RuntimeException("创建Log文件夹目录失败!");
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createLogDirPhoneHardDrive(String mSDCardRootPath) {
        // --data/data/AZXWrite
        boolean isCreated = createSaveDir(mSDCardRootPath);
        if (!isCreated) {
            throw new RuntimeException("Create log folder failed in the phone's own hard drive!");
        } else {
            SDCARD_DIR_PATH = mSDCardRootPath;
            LOG_SAVE_DIR_PATH = SDCARD_DIR_PATH + File.separator + LOG_SAVE_DIR_NAME;
            log("Creating a log folder succeeded! phone's own hard drive, save path is ：" + LOG_SAVE_DIR_PATH);
        }
    }

    private String createNewLogFileName(int mLogFileType) {
        String mFilePrefix;
        if (mLogFileType == LOG_FILE_TYPE_PROGRAM) {
            mFilePrefix = LOG_SAVE_FILE_PROGRAM_PREFIX;
        } else {
            mFilePrefix = LOG_SAVE_FILE_SYSTEM_PREFIX;
        }
        DateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long timeMillis = System.currentTimeMillis();
        String nowDateString = mFormat.format(new Date(timeMillis));
        return mFilePrefix + "~" + nowDateString + "~" + timeMillis;
    }

    /**
     * Author: wangzg <br/>
     * Time: 2017-12-7 18:46:54<br/>
     * Description: 归并排序.
     *
     * @param srcArray the src array
     */
    public static void sort(long[] srcArray) {
        long[] helper = new long[srcArray.length];
        sort(srcArray, 0, srcArray.length - 1, helper);
    }

    private static void sort(long[] srcArray, int lo, int hi, long[] helper) {
        if (lo >= hi)
            return;
        int mid = lo + (hi - lo) / 2;
        sort(srcArray, lo, mid, helper);
        sort(srcArray, mid + 1, hi, helper);
        merge(srcArray, lo, mid, hi, helper);
    }

    private static void merge(long[] srcArray, int lo, int mid, int hi, long[] helper) {
        System.arraycopy(srcArray, lo, helper, lo, hi + 1 - lo);
        int i = lo;
        int j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid)
                srcArray[k] = helper[j++];
            else if (j > hi)
                srcArray[k] = helper[i++];
            else if (helper[i] <= helper[j])
                srcArray[k] = helper[i++];
            else
                srcArray[k] = helper[j++];
        }
    }

    /**
     * 保存数据的方法，我们需要拿到保存数据的具体类型，然后根据类型调用不同的保存方法
     */
    private void setParam(Context context, String key, Object object) {

        String type = object.getClass().getSimpleName();
        SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        if ("String".equals(type)) {
            editor.putString(key, (String) object);
        } else if ("Integer".equals(type)) {
            editor.putInt(key, (Integer) object);
        } else if ("Boolean".equals(type)) {
            editor.putBoolean(key, (Boolean) object);
        } else if ("Float".equals(type)) {
            editor.putFloat(key, (Float) object);
        } else if ("Long".equals(type)) {
            editor.putLong(key, (Long) object);
        }

        editor.apply();
    }


    /**
     * 得到保存数据的方法，我们根据默认值得到保存的数据的具体类型，然后调用相对于的方法获取值
     */
    private Object getParam(Context context, String key) {
        String type = ((Object) "").getClass().getSimpleName();
        SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);

        if ("String".equals(type)) {
            return sp.getString(key, "");
        } else if ("Integer".equals(type)) {
            return sp.getInt(key, (Integer) (Object) "");
        } else if ("Boolean".equals(type)) {
            return sp.getBoolean(key, (Boolean) (Object) "");
        } else if ("Float".equals(type)) {
            return sp.getFloat(key, (Float) (Object) "");
        } else if ("Long".equals(type)) {
            return sp.getLong(key, (Long) (Object) "");
        }

        return null;
    }

    private void log(String msg) {
        Log.w("wzg", msg);
    }
}
