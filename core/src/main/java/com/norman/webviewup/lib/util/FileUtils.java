package com.norman.webviewup.lib.util;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    public static void delete(String path) {
        delete(new File(path));
    }


    public static void delete(File file) {
        if (file.isFile()) {
            file.delete();
            return;
        }
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length == 0) {
                file.delete();
                return;
            }
            for (File f : childFile) {
                delete(f);
            }
            file.delete();
        }
    }


    public static void cleanDirectory(String path) {
        cleanDirectory(new File(path));
    }

    public static void cleanDirectory(File file) {
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length == 0) {
                return;
            }
            for (File f : childFile) {
                delete(f);
            }
        }
    }


    public static void makeDirectory(String path) {
        makeDirectory(new File(path));
    }

    public static void makeDirectory(File file) {
        if (file != null && !file.exists()) {
            file.mkdirs();
        }
    }

    public static void moveFile(File srcFile, File outputFile) {
        boolean rename = srcFile.renameTo(outputFile);
        if (!rename) {
            copyFile(srcFile, outputFile);
            delete(srcFile);
        }
    }

    public static void copyFile(File srcFile,
                                File outputFile) {

        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(srcFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        copyFile(fileInputStream, outputFile, true);
    }

    public static void copyFile(String srcPath,
                                String outputPath) {

        copyFile(new File(srcPath), new File(outputPath));
    }

    public static void copyFile(File srcFile,
                                FileOutputStream fileOutputStream, boolean close) {

        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(srcFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        copyFile(fileInputStream, fileOutputStream, true, close);
    }


    public static void copyFile(InputStream inputStream,
                                File outputFile, boolean close) {
        FileUtils.makeDirectory(outputFile.getParentFile());
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        copyFile(inputStream, fileOutputStream, close, true);

    }

    public static void copyFile(InputStream inputStream,
                                OutputStream outputStream,
                                boolean close) {
        copyFile(inputStream, outputStream, close, close);
    }

    public static void copyFile(InputStream inputStream,
                                OutputStream outputStream,
                                boolean inputClose, boolean outputClose) {
        BufferedInputStream bufferedInput = null;
        BufferedOutputStream bufferedOutput = null;
        try {
            byte[] buffer = new byte[8192];
            bufferedInput = new BufferedInputStream(inputStream);
            bufferedOutput = new BufferedOutputStream(outputStream);
            int count;
            while ((count = bufferedInput.read(buffer)) > 0) {
                bufferedOutput.write(buffer, 0, count);
            }
            bufferedOutput.flush();
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        } finally {
            try {
                if (inputClose && bufferedInput != null) {
                    bufferedInput.close();
                }
            } catch (IOException ignore) {

            }
            try {
                if (outputClose && bufferedOutput != null) {
                    bufferedOutput.close();
                }
            } catch (IOException ignore) {

            }

        }
    }

    public static boolean existDirectory(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.isDirectory() && file.exists();
    }

    public static boolean existFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.isFile() && file.exists();
    }

    public static boolean exist(File file) {
        if (file  == null) {
            return false;
        }
        return file.exists();
    }

    public static boolean isNotEmpty(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.isFile() && file.exists() && file.length() != 0;
    }

    public static boolean createFile(File file) {
        if (file != null && !file.exists()) {
            makeDirectory(file.getParentFile());
            try {
                return file.createNewFile();
            } catch (IOException ignore) {
                return false;
            }
        }
        return true;
    }
    public static boolean createFile(String path) {
        return createFile(new File(path));
    }
}
