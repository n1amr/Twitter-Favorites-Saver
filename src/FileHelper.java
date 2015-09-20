import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.TreeSet;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;

public class FileHelper {

    // static final File archiveDir = new File(
    // "D:\\Google Drive\\Backups\\Internet\\Twitter Favorites Archive");
    static File archiveDir = new File("Twitter Favorites Archive");
    static File tweetsdataDir = new File(archiveDir, "data/js");

    static File htmlFolder = new File("data/savedHTML/");
    static File failedIdsFolder = new File("data/ids/failedIds/");

    static File indexFile = new File(tweetsdataDir.getAbsolutePath()
            + File.separator + "tweet_index.js");

    static File avatarsFolder = new File(
            archiveDir.getAbsolutePath() + File.separator + "profile_images");
    static File mediaFolder = new File(
            archiveDir.getAbsolutePath() + File.separator + "media");
    static File recycledMediaFolder = new File(
            archiveDir.getAbsolutePath() + File.separator + "recycled media");

    static File progressFile = new File("data/progress/progress.json");

    public static void formatFile(int month, int year) throws Exception {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        String fileText;
        if (tweetsFile.exists())
            fileText = readDataFromFile(tweetsFile).substring(header.length());
        else {
            tweetsFile.createNewFile();
            fileText = "[]";
        }
        JSONArray array = new JSONArray(fileText);

        array = TweetsHelper.sortTweets(array, TweetsHelper.tweetsComparator);

        fileText = header + TweetsHelper.unicodeEscape(array.toString());
        writeDataIntoFile(tweetsFile, fileText);

        // Add to tweet_index.js
        updateEntryInIndexFile(array, month, year);
    }

    static ArrayList<Long> getIDsfromHTML(File sourceFile)
            throws FileNotFoundException {
        ArrayList<Long> ids = new ArrayList<>();

        Scanner scanner = new Scanner(new FileInputStream(sourceFile));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains("data-tweet-id")) {
                int p = line.indexOf("data-tweet-id") + 15;
                int p2 = line.indexOf("\"", p);
                String num = line.substring(p, p2);
                Long id = Long.valueOf(num);
                ids.add(id);
            }
        }
        scanner.close();

        return ids;
    }

    static File getTweetsFile(int month, int year) {
        return new File(tweetsdataDir.getAbsolutePath() + File.separator
                + "tweets" + File.separator + year + "_"
                + (month < 10 ? "0" + month : month) + ".js");
    }

    public static ArrayList<JSONObject> loadAllTweets()
            throws JSONException, IOException {
        ArrayList<JSONObject> tweets = new ArrayList<>();

        File tweetsFolder = new File(
                tweetsdataDir.getAbsolutePath() + File.separator + "tweets");

        for (File tweetsFile : tweetsFolder.listFiles()) {
            // System.out.println("Loading tweets on " + tweetsFile.getName());
            JSONArray array = new JSONArray(
                    readDataFromFile(tweetsFile).substring(32));

            for (int i = 0; i < array.length(); i++) {
                JSONObject tweet = array.getJSONObject(i);
                tweets.add(tweet);
            }
        }
        return tweets;
    }

    static JSONObject loadProgress() {
        return JSONHelper.loadJSONObject(progressFile);
    }

    public static JSONObject loadSavedTweet(long tweetID)
            throws JSONException, IOException {
        String id = Long.toString(tweetID);

        File tweetsFolder = new File(
                tweetsdataDir.getAbsolutePath() + File.separator + "tweets");

        for (File tweetsFile : tweetsFolder.listFiles()) {
            JSONArray array = new JSONArray(
                    readDataFromFile(tweetsFile).substring(32));

            for (int i = 0; i < array.length(); i++) {
                JSONObject tweet = array.getJSONObject(i);
                String id2 = tweet.getString("id_str");
                if (id.equals(id2))
                    return tweet;
            }
        }
        return null;
    }

    public static JSONArray loadTweetsOfMonth(int month, int year)
            throws Exception {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        String fileText;
        if (tweetsFile.exists())
            fileText = readDataFromFile(tweetsFile).substring(header.length());
        else {
            tweetsFile.createNewFile();
            fileText = "[]";
        }

        JSONArray array = new JSONArray(fileText);
        array = TweetsHelper.sortTweets(array, TweetsHelper.tweetsComparator);
        return array;
    }

    public static String readDataFromFile(File file) throws IOException {
        BufferedReader reader = null;

        reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)));

        StringBuffer text = new StringBuffer();
        String line;

        while ((line = reader.readLine()) != null)
            text.append(line + "\n");

        try {
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    static ArrayList<Long> readIDsfromFile(File file) {
        ArrayList<Long> ids = new ArrayList<>();

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            try {
                file.createNewFile();
                inputStream = new FileInputStream(file);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        Scanner scanner = new Scanner(inputStream);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Long id = Long.valueOf(line);
            ids.add(id);
        }
        scanner.close();

        return ids;
    }

    static void saveProgress(int i, int page, long id, int month, int year)
            throws JSONException, IOException {
        JSONObject progress = new JSONObject();
        progress.put("page", page);
        progress.put("i", i);
        progress.put("id", id);
        progress.put("month", month);
        progress.put("year", year);
        JSONHelper.saveJSONObject(progressFile, progress);

    }

    static void updateEntryInIndexFile(JSONArray tweetsArray, int month,
            int year) throws IOException {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);
        String tweetsIndexText = readDataFromFile(indexFile);
        String tweetsIndexHeader = "var tweet_index = ";
        try {

            JSONArray jsonArray = new JSONArray(
                    tweetsIndexText.substring(tweetsIndexHeader.length()));
            ArrayList<JSONObject> arrayList = new ArrayList<>();
            // Copy old
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entry = jsonArray.getJSONObject(i);

                int m = entry.getInt("month");
                int y = entry.getInt("year");

                if (!(month == m && year == y))
                    arrayList.add(entry);
            }

            // Add new
            JSONObject entry = new JSONObject();
            entry.put("file_name", "data/js/tweets/" + year_month + ".js");
            entry.put("year", year);
            entry.put("var_name", "tweets_" + year_month);
            entry.put("tweet_count", tweetsArray.length());
            entry.put("month", month);
            arrayList.add(entry);

            Collections.sort(arrayList, TweetsHelper.filesComparator);

            // Copy back
            jsonArray = new JSONArray();
            for (JSONObject obj : arrayList)
                jsonArray.put(obj);

            // Save to file
            tweetsIndexText = tweetsIndexHeader + jsonArray.toString();
            writeDataIntoFile(indexFile, tweetsIndexText);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void writeDataIntoFile(File file, String data)
            throws IOException {
        Writer writer = null;
        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file)));

            writer.write(data);
            writer.flush();
            writer.close();

        } catch (IOException e) {
            try {
                writer.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            throw e;
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    static void writeIDsintoFile(ArrayList<Long> ids, File file) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            try {
                file.createNewFile();
                outputStream = new FileOutputStream(file);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        PrintWriter printWriter = new PrintWriter(outputStream);
        for (long id : ids)
            printWriter.println(id);
        printWriter.flush();
        printWriter.close();
    }

    static void collectIdsFromHTMLFolder(File htmlFolder)
            throws JSONException, IOException {

        ArrayList<Long> allids = new ArrayList<>();
        for (File file : htmlFolder.listFiles()) {
            ArrayList<Long> ids = getIDsfromHTML(file);
            System.out.println(
                    "Collected " + ids.size() + " from" + file.getName());
            allids.addAll(ids);
        }
        TreeSet<Long> uids = new TreeSet<>(allids);
        allids = new ArrayList<>(uids);
        Collections.reverse(allids);

        // Reset progress
        saveProgress(0, loadProgress().getInt("page"),
                loadProgress().getLong("id"), loadProgress().getInt("month"),
                loadProgress().getInt("year"));
        writeIDsintoFile(allids, new File("ids/ids.txt"));
    }

    static void copyFile(File source, File dest) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0)
                output.write(buf, 0, bytesRead);
        } finally {
            input.close();
            output.close();
        }
    }

}
