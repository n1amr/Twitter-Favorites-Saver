import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

public class TweetsHelper {

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

    public static int getYear(String createdAt) {
        return Integer.valueOf(createdAt.substring(26, 30));
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

    public static void deleteTweet(long id) throws Exception {
        JSONObject deletedTweet = fastLoadSavedTweet(id);
        String id_str = deletedTweet.getString("id_str");

        String createdAt = deletedTweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = FileHelper.getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";

        JSONArray tweets = FileHelper.loadTweetsOfMonth(month, year);
        JSONArray newTweets = new JSONArray();

        for (int i = 0; i < tweets.length(); i++) {
            JSONObject tweet2 = tweets.getJSONObject(i);
            String id_str2 = tweet2.getString("id_str");
            if (!id_str.equals(id_str2))
                newTweets.put(tweet2);
        }

        newTweets = sortTweets(newTweets, tweetsComparator);

        FileHelper.writeDataIntoFile(tweetsFile,
                header + unicodeEscape(newTweets.toString()));

        // Add to tweet_index.js
        FileHelper.updateEntryInIndexFile(newTweets, month, year);
    }

    public static JSONObject fastLoadSavedTweet(long tweetID)
            throws JSONException, IOException {
        if (allTweets == null)
            allTweets = FileHelper.loadAllTweets();

        String id = Long.toString(tweetID);

        for (int i = 0; i < allTweets.size(); i++) {
            JSONObject tweet = allTweets.get(i);
            String id2 = tweet.getString("id_str");
            if (id.equals(id2))
                return tweet;
        }
        return null;
    }

    public static JSONObject getTweet(long id)
            throws TwitterException, JSONException {
        Status status = Console.twitter.showStatus(id);
        String json = TwitterObjectFactory.getRawJSON(status);
        JSONObject jsonTweet = new JSONObject(json);
        return jsonTweet;
    }

    public static void saveTweet(long id) throws Exception {
        saveTweet(getTweet(id));
    }

    public static void saveTweet(JSONObject jsonTweet) throws Exception {
        String createdAt = jsonTweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Add to json array
        File tweetsFile = FileHelper.getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        JSONArray array = FileHelper.loadTweetsOfMonth(month, year);

        // Add tweet and pevent duplicates
        if (array.length() == 0 || !tweetExists(jsonTweet, array))
            array.put(jsonTweet);
        else
            System.out.println("Skipped");

        array = sortTweets(array, tweetsComparator);

        FileHelper.writeDataIntoFile(tweetsFile,
                header + unicodeEscape(array.toString()));

        // Add to tweet_index.js
        FileHelper.updateEntryInIndexFile(array, month, year);
    }

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
    static ArrayList<JSONObject> allTweets = null;

}
