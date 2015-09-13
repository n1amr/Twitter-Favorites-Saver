import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Scanner;
import java.util.TreeSet;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;
import twitter4j.api.FavoritesResources;

public class Console {
    static Scanner scanner;
    static TwitterApp twitterApp;
    static Twitter twitter;

    static final File progressFile = new File("progress.json");
    static final File homeDir = new File("Twitter Archive/data/js");
    static final File indexFile = new File(
            homeDir.getAbsolutePath() + File.separator + "tweet_index.js");
    static final File htmlFolder = new File("savedHTML/");

    static File getTweetsFile(int month, int year) {
        return new File(homeDir.getAbsolutePath() + File.separator + "tweets"
                + File.separator + year + "_"
                + (month < 10 ? "0" + month : month) + ".js");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Start");
        System.out.println("Logging in...");
        scanner = new Scanner(System.in);
        TwitterApp.setScanner(scanner);

        login();

        initializeChecked();
        System.out.println(checkedIds.size());
        System.out.println(loadAllTweet().size());

        int response;
        do {
            System.out.println("1- Save favorites online from twitter");
            System.out.println(
                    "2- Save favorites offline from html files in \"savedHTML\" folder");
            System.out.println("3- Check if a tweet is saved");
            System.out.println("4- Delete a saved tweet");
            System.out.println("0- Exit");

            response = scanner.nextInt();
            scanner.nextLine();

            switch (response) {
                case 1: {
                    System.out.println("Is this the first time?");
                    String res = scanner.nextLine().toUpperCase();
                    if (res.contains("Y"))
                        saveFavoritesOnline();
                    else
                        updateOnline();
                    break;
                }
                case 2: {
                    System.out.println("Refresh files? (Y/N)");
                    String res = scanner.nextLine().toUpperCase();
                    if (res.contains("Y"))
                        collectFromHTMLFolder(htmlFolder);
                    saveFavoritesFromSavedHTML();
                    break;
                }
                case 3: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println(loadSavedTweet(id));
                    break;
                }
                case 4: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println("Deleteing ...");
                    System.out.println(fastLoadSavedTweet(id).get("text"));
                    deleteTweet(id);
                    break;
                }
            }
        } while (response != 0);
        System.out.println("End");
    }

    static void markFailed(long id) throws FileNotFoundException {
        File deletedIdsFile = new File("deleted ids.txt");
        ArrayList<Long> ids = readIDsfromFile(deletedIdsFile);

        ids.add(id);
        TreeSet<Long> uids = new TreeSet<>(ids);
        ids = new ArrayList<>(uids);
        Collections.reverse(ids);

        writeIDsintoFile(ids, deletedIdsFile);
    }

    private static void collectFromHTMLFolder(File sourceFolder)
            throws JSONException, IOException {

        ArrayList<Long> allids = new ArrayList<>();
        for (File file : sourceFolder.listFiles()) {
            ArrayList<Long> ids = getIDsfromHTML(file);
            System.out.println(
                    "Collected " + ids.size() + " from" + file.getName());
            allids.addAll(ids);
        }
        TreeSet<Long> uids = new TreeSet<>(allids);
        allids = new ArrayList<>(uids);
        Collections.reverse(allids);

        saveProgress(0, loadProgress().getInt("page"),
                loadProgress().getLong("id"), loadProgress().getInt("month"),
                loadProgress().getInt("year"));
        writeIDsintoFile(allids, new File("ids.txt"));
    }

    static void writeIDsintoFile(ArrayList<Long> ids, File file)
            throws FileNotFoundException {
        PrintWriter printWriter = new PrintWriter(new FileOutputStream(file));
        for (long id : ids)
            printWriter.println(id);
        printWriter.flush();
        printWriter.close();
    }

    private static void saveFavoritesFromSavedHTML()
            throws JSONException, IOException {
        // Load ids
        File idsFile = new File("ids.txt");
        ArrayList<Long> ids = readIDsfromFile(idsFile);
        System.out.println("Found " + ids.size() + " ids");

        // Load progress
        JSONObject progress = JSONHelper.loadJSONObject(progressFile);
        int start_i = progress.getInt("i");

        for (int i = start_i; i < ids.size();) {
            long id = ids.get(i);
            double percentage = (int) (10000.0 * i / ids.size()) / 100.0;
            System.out.println(
                    "i = " + i + " (" + percentage + "%), id = " + id + ":");

            progress = new JSONObject();

            JSONObject jsonTweet = fastLoadSavedTweet(id);
            // if not saved before
            if (jsonTweet == null)
                // Try to get tweet and save it
                try {
                    if (!isChecked(id)) {
                        Status status = twitter.showStatus(id);
                        String json = TwitterObjectFactory.getRawJSON(status);
                        jsonTweet = new JSONObject(json);
                        System.out.println(jsonTweet.getString("text"));
                        System.out.println(jsonTweet.getString("created_at"));

                        saveTweet(jsonTweet);

                        String createdAt = jsonTweet.getString("created_at");
                        int m = getMonth(createdAt);
                        int y = getYear(createdAt);

                        // Save progress
                        saveProgress(loadProgress().getInt("i"),
                                loadProgress().getInt("page"), id, m, y);

                        // Slow down for rate limit
                        // TODO pause(2000);
                    }
                } catch (Exception e) {
                    TwitterException twitterErr = (TwitterException) e;
                    int errCode = twitterErr.getErrorCode();
                    ArrayList<Integer> skipCodes = new ArrayList<>(
                            Arrays.asList(new Integer[] { 144, 179, 63, 34 }));
                    if (errCode == 88)
                        rateLimitWait(twitterErr);
                    else if (skipCodes.contains(errCode)) {
                        markFailed(id);
                        System.out.println("Failed to fetch tweet #" + id);
                        i++; // go on
                    } else {
                        e.printStackTrace();
                        // Retry after 10 seconds
                        pause(10000);
                    }
                }
            else { // was saved before
                System.out.println(jsonTweet.getString("text"));
                System.out.println(jsonTweet.getString("created_at"));
                System.out.println("SKIPPED");
            }

            // Save progress
            saveProgress(i, loadProgress().getInt("page"),
                    loadProgress().getLong("id"),
                    loadProgress().getInt("month"),
                    loadProgress().getInt("year"));

            System.out.println("------------------------------------");
            i++; // go on if successful
        }
    }

    private static void updateOnline() throws JSONException, IOException {
        ArrayList<JSONObject> list = loadAllTweet();
        JSONArray allTweets = JSONHelper.jsonObjectsToJSONArray(list);

        FavoritesResources favs = twitter.favorites();
        int page = 1;

        int n_saved = 0;

        while (true)
            try {
                // Load page of favorites
                Paging paging = new Paging(page, 100);
                ResponseList<Status> statuses = favs.getFavorites(paging);

                for (int i = 0; i < statuses.size();) {
                    System.out.println("i=" + i + " page = " + page + ":");
                    try {
                        Status status = statuses.get(i);
                        String json = TwitterObjectFactory.getRawJSON(status);
                        JSONObject jsonTweet = new JSONObject(json);

                        System.out.println(jsonTweet.getString("text"));
                        System.out.println(jsonTweet.getString("created_at"));

                        // Stop if 20 consecutive tweets are saved before
                        if (tweetExists(jsonTweet, allTweets)) {
                            System.out.println("SKIPPED");
                            n_saved++;
                            if (n_saved >= 20)
                                return;
                        } else
                            n_saved = 0; // reset
                        saveTweet(jsonTweet);

                        System.out.println(
                                "------------------------------------");
                        i++;
                    } catch (Exception e) {
                        if (e instanceof TwitterException
                                && ((TwitterException) e).getErrorCode() == 88)
                            rateLimitWait((TwitterException) e);
                        else {
                            e.printStackTrace();
                            // Retry after 10 seconds
                            pause(10000);
                        }
                    }
                    pause(2000);
                }
                page++;
            } catch (Exception e) {
                if (e instanceof TwitterException
                        && ((TwitterException) e).getErrorCode() == 88)
                    rateLimitWait((TwitterException) e);
                else {
                    e.printStackTrace();
                    // Retry after 10 seconds
                    pause(10000);
                }
            }
    }

    private static ArrayList<Long> readIDsfromFile(File sourceFile)
            throws FileNotFoundException {
        ArrayList<Long> ids = new ArrayList<>();

        Scanner scanner = new Scanner(new FileInputStream(sourceFile));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Long id = Long.valueOf(line);
            ids.add(id);
        }
        scanner.close();

        return ids;
    }

    private static ArrayList<Long> getIDsfromHTML(File sourceFile)
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

    private static void saveFavoritesOnline() throws JSONException {
        // Load progress
        JSONObject progress = JSONHelper.loadJSONObject(progressFile);
        int page = progress.getInt("page");
        int start_i = progress.getInt("i");

        FavoritesResources favs = twitter.favorites();

        boolean start = true;
        while (true)
            try {
                // Load page of favorites
                Paging paging = new Paging(page, 10);
                ResponseList<Status> statuses = favs.getFavorites(paging);

                int i = 0;
                if (start) {
                    i = start_i;
                    start = false;
                }

                for (; i < statuses.size();) {
                    System.out.println("i=" + i + " page = " + page + ":");
                    try {
                        Status status = statuses.get(i);
                        String json = TwitterObjectFactory.getRawJSON(status);
                        JSONObject jsonTweet = new JSONObject(json);

                        System.out.println(jsonTweet.getString("text"));
                        System.out.println(jsonTweet.getString("created_at"));

                        saveTweet(jsonTweet);

                        String createdAt = jsonTweet.getString("created_at");
                        int m = getMonth(createdAt);
                        int y = getYear(createdAt);

                        // Save progress
                        saveProgress(i, page, loadProgress().getLong("id"), m,
                                y);

                        System.out.println(
                                "------------------------------------");
                        i++;
                    } catch (Exception e) {
                        if (e instanceof TwitterException
                                && ((TwitterException) e).getErrorCode() == 88)
                            rateLimitWait((TwitterException) e);
                        else {
                            e.printStackTrace();
                            // Retry after 10 seconds
                            pause(10000);
                        }
                    }
                    pause(2000);
                }
                page++;
            } catch (Exception e) {
                if (e instanceof TwitterException
                        && ((TwitterException) e).getErrorCode() == 88)
                    rateLimitWait((TwitterException) e);
                else {
                    e.printStackTrace();
                    // Retry after 10 seconds
                    pause(10000);
                }
            }
    }

    private static void saveProgress(int i, int page, long id, int month,
            int year) throws JSONException, IOException {
        JSONObject progress = new JSONObject();
        progress.put("page", page);
        progress.put("i", i);
        progress.put("id", id);
        progress.put("month", month);
        progress.put("year", year);
        JSONHelper.saveJSONObject(progressFile, progress);

    }

    private static JSONObject loadProgress() {
        return JSONHelper.loadJSONObject(progressFile);
    }

    static ArrayList<JSONObject> allTweets = null;

    public static JSONObject fastLoadSavedTweet(long tweetID)
            throws JSONException, IOException {
        if (allTweets == null) {
            allTweets = loadAllTweet();
        }

        String id = Long.toString(tweetID);

        for (int i = 0; i < allTweets.size(); i++) {
            JSONObject tweet = allTweets.get(i);
            String id2 = tweet.getString("id_str");
            if (id.equals(id2))
                return tweet;
        }
        return null;
    }

    public static ArrayList<JSONObject> loadAllTweet()
            throws JSONException, IOException {
        ArrayList<JSONObject> tweets = new ArrayList<>();

        File tweetsFolder = new File(
                homeDir.getAbsolutePath() + File.separator + "tweets");

        for (File tweetsFile : tweetsFolder.listFiles()) {
            JSONArray array = new JSONArray(
                    JSONHelper.readDataFromFile(tweetsFile).substring(32));

            for (int i = 0; i < array.length(); i++) {
                JSONObject tweet = array.getJSONObject(i);
                tweets.add(tweet);
            }
        }
        return tweets;
    }

    public static JSONObject loadSavedTweet(long tweetID)
            throws JSONException, IOException {
        String id = Long.toString(tweetID);

        File tweetsFolder = new File(
                homeDir.getAbsolutePath() + File.separator + "tweets");

        for (File tweetsFile : tweetsFolder.listFiles()) {
            JSONArray array = new JSONArray(
                    JSONHelper.readDataFromFile(tweetsFile).substring(32));

            for (int i = 0; i < array.length(); i++) {
                JSONObject tweet = array.getJSONObject(i);
                String id2 = tweet.getString("id_str");
                if (id.equals(id2))
                    return tweet;
            }
        }
        return null;
    }

    public static void saveTweet(JSONObject jsonTweet) throws Exception {
        String createdAt = jsonTweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        JSONArray array = loadTweetsOfMonth(month, year);

        // Add tweet and pevent duplicates
        if (array.length() == 0 || !tweetExists(jsonTweet, array))
            array.put(jsonTweet);
        else
            System.out.println("Skipped");

        array = sortTweets(array, tweetsComparator);

        JSONHelper.writeDataIntoFile(tweetsFile,
                header + unicodeEscape(array.toString()));

        // Add to tweet_index.js
        updateEntryInIndexFile(array, month, year);
    }

    public static void deleteTweet(long id) throws Exception {
        JSONObject deletedTweet = fastLoadSavedTweet(id);
        String id_str = deletedTweet.getString("id_str");

        String createdAt = deletedTweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";

        JSONArray tweets = loadTweetsOfMonth(month, year);
        JSONArray newTweets = new JSONArray();

        for (int i = 0; i < tweets.length(); i++) {
            JSONObject tweet2 = tweets.getJSONObject(i);
            String id_str2 = tweet2.getString("id_str");
            if (!id_str.equals(id_str2)) {
                newTweets.put(tweet2);
            }
        }

        newTweets = sortTweets(newTweets, tweetsComparator);

        JSONHelper.writeDataIntoFile(tweetsFile,
                header + unicodeEscape(newTweets.toString()));

        // Add to tweet_index.js
        updateEntryInIndexFile(tweets, month, year);
    }

    static void login() throws JSONException, TwitterException {
        twitterApp = new TwitterApp(
                JSONHelper.loadJSONArray(TwitterApp.USERS_LOGIN_DATA_FILE)
                        .getJSONObject(0));
        twitter = twitterApp.getTwitter();
    }

    public static void formatFile(int month, int year) throws Exception {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        String fileText;
        if (tweetsFile.exists())
            fileText = JSONHelper.readDataFromFile(tweetsFile)
                    .substring(header.length());
        else {
            tweetsFile.createNewFile();
            fileText = "[]";
        }
        JSONArray array = new JSONArray(fileText);

        array = sortTweets(array, tweetsComparator);

        fileText = header + unicodeEscape(array.toString());
        JSONHelper.writeDataIntoFile(tweetsFile, fileText);

        // Add to tweet_index.js
        updateEntryInIndexFile(array, month, year);
    }

    public static JSONArray loadTweetsOfMonth(int month, int year)
            throws Exception {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        String fileText;
        if (tweetsFile.exists())
            fileText = JSONHelper.readDataFromFile(tweetsFile)
                    .substring(header.length());
        else {
            tweetsFile.createNewFile();
            fileText = "[]";
        }

        JSONArray array = new JSONArray(fileText);
        array = sortTweets(array, tweetsComparator);
        return array;
    }

    private static void updateEntryInIndexFile(JSONArray array, int month,
            int year) throws IOException {
        String year_month = year + "_" + (month < 10 ? "0" + month : month);
        String tweetsIndexText = JSONHelper.readDataFromFile(indexFile);
        String tweetsIndexHeader = "var tweet_index =  ";
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
            entry.put("tweet_count", array.length());
            entry.put("month", month);
            arrayList.add(entry);

            Collections.sort(arrayList, filesComparator);

            // Copy back
            jsonArray = new JSONArray();
            for (JSONObject obj : arrayList)
                jsonArray.put(obj);

            // Save to file
            tweetsIndexText = tweetsIndexHeader + jsonArray.toString();
            JSONHelper.writeDataIntoFile(indexFile, tweetsIndexText);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static HashSet<Long> checkedIds = null;

    static void initializeChecked() throws JSONException, IOException {
        checkedIds = new HashSet<>();
        File deletedIdsFile = new File("deleted ids.txt");
        Scanner scanner = new Scanner(deletedIdsFile);
        while (scanner.hasNextLong()) {
            checkId(scanner.nextLong());
        }
        scanner.close();

        for (JSONObject tweet : loadAllTweet()) {
            String id_str = tweet.getString("id_str");
            long id = Long.valueOf(id_str);
            checkId(id);
        }
    }

    static boolean checkId(long id) {
        return checkedIds.add(id);
    }

    static boolean isChecked(long id) {
        return checkedIds.contains(id);
    }

    public static JSONArray sortTweets(JSONArray tweets,
            Comparator<JSONObject> comparator) {
        JSONArray jsonArray = new JSONArray();

        // Copy to array list
        ArrayList<JSONObject> list = JSONHelper
                .jsonArrayToJSONObjectsList(tweets);

        Collections.sort(list, tweetsComparator);
        // Copy back
        for (JSONObject obj : list)
            jsonArray.put(obj);

        return jsonArray;
    }

    static void rateLimitWait(TwitterException e) {
        System.err.println("Rate limit exceeded");
        int seconds = e.getRateLimitStatus().getSecondsUntilReset();
        System.out.println("Waiting for " + seconds + " seconds.");
        Date date = new Date(new Date().getTime() + seconds * 1000 + 3000);
        System.out.println("Retry on " + date.toString());
        try {
            Thread.sleep(1000 * seconds + 3000);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static String unicodeEscape(String s) {
        StringBuffer buffer = new StringBuffer();

        for (char c : s.toCharArray())
            // TODO check 1024
            if (c > 0xFF) {
                int code = c;
                String num = Integer.toHexString(code).toUpperCase();
                while (num.length() < 4)
                    num = '0' + num;
                buffer.append("\\u" + num);
            } else
                buffer.append(c);
        return buffer.toString();
    }

    public static int getYear(String createdAt) {
        return Integer.valueOf(createdAt.substring(26, 30));
    }

    public static int getMonth(String createdAt) throws Exception {
        String m = createdAt.substring(4, 7);
        if (m.equals("Jan"))
            return 1;
        else if (m.equals("Feb"))
            return 2;
        else if (m.equals("Mar"))
            return 3;
        else if (m.equals("Apr"))
            return 4;
        else if (m.equals("May"))
            return 5;
        else if (m.equals("Jun"))
            return 6;
        else if (m.equals("Jul"))
            return 7;
        else if (m.equals("Aug"))
            return 8;
        else if (m.equals("Sep"))
            return 9;
        else if (m.equals("Oct"))
            return 10;
        else if (m.equals("Nov"))
            return 11;
        else if (m.equals("Dec"))
            return 12;
        else
            throw new Exception("Error: " + m);
    }

    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    static Comparator<JSONObject> tweetsComparator = new Comparator<JSONObject>() {
        @Override
        public int compare(JSONObject o1, JSONObject o2) {
            try {
                Long id1 = Long.valueOf(o1.getString("id_str"));
                Long id2 = Long.valueOf(o2.getString("id_str"));

                if (id1 > id2)
                    return -1;
                else if (id1 < id2)
                    return 1;
                else
                    return 0;

            } catch (NumberFormatException | JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

    };
    static Comparator<JSONObject> filesComparator = new Comparator<JSONObject>() {
        @Override
        public int compare(JSONObject o1, JSONObject o2) {
            try {
                int m1 = o1.getInt("month");
                int y1 = o1.getInt("year");
                int m2 = o2.getInt("month");
                int y2 = o2.getInt("year");

                if (y1 == y2 && m1 == m2)
                    return 0;
                if (y1 > y2 || y1 == y2 && m1 > m2)
                    return -1;
                if (y1 < y2 || y1 == y2 && m1 < m2)
                    return 1;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    };

    public static boolean tweetExists(JSONObject tweet, JSONArray tweets)
            throws JSONException {
        String id = tweet.getString("id_str");
        for (int i = 0; i < tweets.length(); i++) {
            JSONObject tweet2 = tweets.getJSONObject(i);
            String id2 = tweet2.getString("id_str");
            if (id.equals(id2))
                return true;
        }
        return false;
    }

    static SimpleDateFormat dateFormat = new SimpleDateFormat(
            "E MMM dd hh:mm:ss +SSSS YYYY");

}