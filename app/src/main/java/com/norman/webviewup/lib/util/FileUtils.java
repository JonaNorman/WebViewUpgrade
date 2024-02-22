package com.norman.webviewup.lib.util;

import java.io.File;

public class FileUtils {


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
}
