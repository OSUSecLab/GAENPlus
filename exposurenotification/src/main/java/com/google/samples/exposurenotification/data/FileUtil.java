package com.google.samples.exposurenotification.data;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileUtil {

    public static String logPath = Environment.getExternalStorageDirectory().getPath() +
            "/Download/log.txt";

    public static void writeToFile(String path, String content, boolean append) {
        try {
            File outFile = new File(path);
            outFile.createNewFile(); // create if not exist
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path, append)));
            out.println(content);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeLog(String s, boolean append) {
        try {
            File outFile = new File(logPath);
            outFile.createNewFile(); // create if not exist
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logPath, append)));
            out.println(s);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeJSONResults(JSONArray jsonArray) {
        String path = Environment.getExternalStorageDirectory().getPath() + "/Download" +
                "/results.json";
        try {
            File outFile = new File(path);
            outFile.createNewFile(); // create if not exist
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
            out.println(jsonArray.toString(4));
            out.close();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }


}
