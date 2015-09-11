import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Scanner;

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

    static File getTweetsFile(int month, int year) {
        return new File(homeDir.getAbsolutePath() + File.separator + "tweets"
                + File.separator + year + "_"
                + ((month < 10) ? "0" + month : month) + ".js");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Start");
        scanner = new Scanner(System.in);
        TwitterApp.setScanner(scanner);

        login();

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
                        progress = new JSONObject();
                        progress.put("page", page);
                        progress.put("i", i);
                        progress.put("month", m);
                        progress.put("year", y);
                        JSONHelper.saveJSONObject(progressFile, progress);

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

    public static void saveTweet(JSONObject jsonTweet)
            throws IOException, JSONException {
        String createdAt = jsonTweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

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

        // Add tweet and pevent duplicates
        if (array.length() == 0 || !tweetExists(jsonTweet, array))
            array.put(jsonTweet);
        else
            System.out.println("Skipped");

        sortTweets(array, tweetsComparator);

        fileText = header + unicodeEscape(array.toString());
        JSONHelper.writeDataIntoFile(tweetsFile, fileText);

        // Add to tweet_index.js
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

    static void login() throws JSONException, TwitterException {
        twitterApp = new TwitterApp(
                JSONHelper.loadJSONArray(TwitterApp.USERS_LOGIN_DATA_FILE)
                        .getJSONObject(0));
        twitter = twitterApp.getTwitter();
    }

    public static void sortTweets(JSONArray tweets,
            Comparator<JSONObject> comparator) {
        // Copy to array list
        ArrayList<JSONObject> list = JSONHelper
                .jsonArrayToJSONObjectsList(tweets);

        Collections.sort(list, tweetsComparator);
        // Copy back
        tweets = new JSONArray();
        for (JSONObject obj : list)
            tweets.put(obj);
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
            if (c > 1024) {
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
        return Integer.valueOf(createdAt.substring(24, 28));
    }

    public static int getMonth(String createdAt) {
        String m = createdAt.substring(2, 5);
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
        else
            return 12;
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