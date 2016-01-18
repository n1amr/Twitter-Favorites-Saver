import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

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
        deleteTweet(deletedTweet);
    }

    public static void deleteTweet(JSONObject deletedTweet) throws Exception {
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

        if (newTweets.length() > 0)
            FileHelper.writeDataIntoFile(tweetsFile,
                    header + unicodeEscape(newTweets.toString()));
        else if (tweetsFile.exists())
            tweetsFile.delete();

        isAllTweetsListUpdated = false;

        // Add to tweet_index.js
        FileHelper.updateEntryInIndexFile(newTweets, month, year);
    }

    public static JSONObject fastLoadSavedTweet(long tweetID)
            throws JSONException, IOException {
        if (!isAllTweetsListUpdated)
            updateAllTweetsList();

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

    public static void saveTweet(long id, boolean override) throws Exception {
        saveTweet(getTweet(id), override);
    }

    public static void saveTweet(long id) throws Exception {
        saveTweet(id, false);
    }

    public static void saveTweet(JSONObject tweet) throws Exception {
        saveTweet(tweet, false);
    }

    public static void saveTweet(JSONObject tweet, boolean override)
            throws Exception {
        String createdAt = tweet.getString("created_at");
        int month = getMonth(createdAt);
        int year = getYear(createdAt);
        String year_month = year + "_" + (month < 10 ? "0" + month : month);

        // Escape new lines in text
        // tweet = fixNewLines(tweet); //TODO

        // Save media locally
        MediaSaving.saveProfileImage(tweet, false);
        tweet = MediaSaving.redirectToLocalProfileImage(tweet);

        MediaSaving.saveMediaImage(tweet, false);
        tweet = MediaSaving.redirectToLocalImage(tweet);

        // Load tweets in the file
        File tweetsFile = FileHelper.getTweetsFile(month, year);
        String header = "Grailbird.data.tweets_" + year_month + " = ";
        JSONArray array = FileHelper.loadTweetsOfMonth(month, year);

        // Add tweet and prevent duplicates
        JSONArray newTweets = new JSONArray();
        JSONObject oldTweet = null;
        String id = tweet.getString("id_str");

        // Put other tweets and save the old one if it exists
        for (int i = 0; i < array.length(); i++) {
            JSONObject tweet2 = array.getJSONObject(i);
            String id2 = tweet2.getString("id_str");
            if (id.equals(id2))
                oldTweet = tweet2;
            else
                newTweets.put(tweet2);
        }

        if (oldTweet == null || override)
            newTweets.put(tweet);
        else {
            newTweets.put(oldTweet);
            System.out.println(
                    "Skipped; This tweet has already been saved #" + id);
        }

        isAllTweetsListUpdated = false;

        newTweets = sortTweets(newTweets, tweetsComparator);

        FileHelper.writeDataIntoFile(tweetsFile,
                header + unicodeEscape(newTweets.toString()));

        // Add to tweet_index.js
        FileHelper.updateEntryInIndexFile(newTweets, month, year);
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

    static SimpleDateFormat archiveDateFormat = new SimpleDateFormat(
            "E MMM dd hh:mm:ss +SSSS yyyy");
    static SimpleDateFormat myDateFormat = new SimpleDateFormat(
            "MMM dd, yyyy - hh:mm");
    static ArrayList<JSONObject> allTweets = null;

    static boolean isAllTweetsListUpdated = false;

    static void updateAllTweetsList() throws JSONException, IOException {
        allTweets = FileHelper.loadAllTweets();
        Collections.sort(allTweets, tweetsComparator);
        isAllTweetsListUpdated = true;
    }

    static void deleteTweetMedia(long id) throws JSONException, IOException {
        JSONObject tweet = FileHelper.loadSavedTweet(id);
        deleteTweetImage(tweet);
    }

    static void deleteTweetImage(JSONObject tweet)
            throws JSONException, IOException {
        if (!tweet.getJSONObject("entities").has("media"))
            return;
        JSONArray mediaArray = tweet.getJSONObject("entities")
                .getJSONArray("media");
        for (int i = 0; i < mediaArray.length(); i++) {
            JSONObject media = mediaArray.getJSONObject(i);

            if (!(media.getString("type").equals("photo")
                    && !media.getString("media_url").startsWith("http")))
                continue;

            String url = media.getString("media_url");

            File file = new File(FileHelper.mediaFolder,
                    media.getString("media_url")
                            .substring(Math.max(url.lastIndexOf("/"),
                                    url.lastIndexOf("\\")) + 1));
            if (file.exists()) {
                System.out.println("Deleting " + file.getAbsolutePath());
                FileHelper.copyFile(file, new File(
                        FileHelper.recycledMediaFolder, file.getName()));
                file.delete();
            }
        }
    }

    static void deleteTweetProfileImage(JSONObject tweet)
            throws JSONException, IOException {
        String url = tweet.getJSONObject("user")
                .getString("profile_image_url_https");

        if (url.startsWith("http"))
            return;

        File file = new File(FileHelper.avatarsFolder, url.substring(
                Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));

        if (file.exists()) {
            // Cancel deletion if another tweet is using the same file
            if (!isAllTweetsListUpdated)
                updateAllTweetsList();

            for (JSONObject tweet2 : allTweets) {
                String url2 = tweet2.getJSONObject("user")
                        .getString("profile_image_url_https");
                String filename2 = url2.substring(
                        Math.max(url2.lastIndexOf("/"), url2.lastIndexOf("\\"))
                                + 1);
                if (filename2.equals(file.getName()) && !tweet
                        .getString("id_str").equals(tweet2.getString("id_str")))
                    return;
            }

            System.out.println("Recycling " + file.getAbsolutePath());
            FileHelper.copyFile(file,
                    new File(FileHelper.recycledMediaFolder, file.getName()));
            file.delete();
        }

    }

    public static void printTweet(JSONObject tweet) throws JSONException {
        System.out.println("******************************************");
        String createdAt = tweet.getString("created_at");
        try {
            Date date = archiveDateFormat.parse(createdAt);
            createdAt = myDateFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.print(tweet.getJSONObject("user").getString("name") + " (@"
                + tweet.getJSONObject("user").getString("screen_name")
                + ") || ");
        System.out.print(createdAt);
        System.out.print(" || #" + tweet.getString("id_str"));
        System.out.println();
        System.out.println("------------------------------------------");
        System.out.println(tweet.getString("text").replace("<br>", "\r\n"));
        System.out.println("******************************************");
    }

    static JSONArray fixEntity(JSONArray entity,
            ArrayList<Integer> replacesIndices) throws JSONException {
        JSONArray new_entity = new JSONArray();
        for (int i = 0; i < entity.length(); i++) {
            JSONObject entity_unit = entity.getJSONObject(i);
            JSONArray indices = entity_unit.getJSONArray("indices");

            int p1 = indices.getInt(0);
            int p2 = indices.getInt(1);

            for (int p : replacesIndices)
                if (p < p1) {
                    p1 += 3;
                    p2 += 3;
                }
            indices = new JSONArray();
            indices.put(p1);
            indices.put(p2);

            entity_unit.put("indices", indices);
            new_entity.put(entity_unit);
        }
        return new_entity;
    }

    static JSONObject fixNewLines(JSONObject tweet) throws Exception {
        JSONObject new_tweet = new JSONObject(tweet.toString());

        String text = tweet.getString("text");
        JSONObject entities = tweet.getJSONObject("entities");

        if (text.contains("\n")) {
            // Replace every \n with a <br> and save the index to list to fix
            // entities later
            int p = 0;
            ArrayList<Integer> replacesIndices = new ArrayList<>();
            while (true) {
                p = text.indexOf("\n");
                if (p < text.length() && p > 0) {
                    replacesIndices.add(p);
                    text = text.replaceFirst("\n", "<br>");
                } else
                    break;
            }

            // fix entities
            entities.put("hashtags", fixEntity(
                    entities.getJSONArray("hashtags"), replacesIndices));
            entities.put("symbols", fixEntity(entities.getJSONArray("symbols"),
                    replacesIndices));
            entities.put("urls",
                    fixEntity(entities.getJSONArray("urls"), replacesIndices));
            entities.put("user_mentions", fixEntity(
                    entities.getJSONArray("user_mentions"), replacesIndices));

            // Apply changes
            new_tweet.put("entities", entities);
            new_tweet.put("text", text);

            return new_tweet;
        }
        return tweet;
    }

    static JSONArray undoFixEntity(JSONArray entity,
            ArrayList<Integer> replacesIndices) throws JSONException {
        JSONArray new_entity = new JSONArray();
        for (int i = 0; i < entity.length(); i++) {
            JSONObject entity_unit = entity.getJSONObject(i);
            JSONArray indices = entity_unit.getJSONArray("indices");

            int p1 = indices.getInt(0);
            int p2 = indices.getInt(1);

            for (int j = replacesIndices.size() - 1; j >= 0; j--)
                if (replacesIndices.get(j) < p1) {
                    p1 -= 3;
                    p2 -= 3;
                }
            indices = new JSONArray();
            indices.put(p1);
            indices.put(p2);

            entity_unit.put("indices", indices);
            new_entity.put(entity_unit);
        }
        return new_entity;
    }

    static JSONObject undoFixNewLines(JSONObject tweet) throws Exception {
        JSONObject new_tweet = new JSONObject(tweet.toString());

        String text = tweet.getString("text");
        JSONObject entities = tweet.getJSONObject("entities");

        if (text.contains("<br>")) {
            // Replace every <br> with a \n and save the index to list to fix
            // entities later
            int p = 0;
            ArrayList<Integer> replacesIndices = new ArrayList<>();
            while (true) {
                p = text.indexOf("<br>");
                if (p < text.length() && p > 0) {
                    replacesIndices.add(p);
                    text = text.replaceFirst("<br>", "\n");
                } else
                    break;
            }

            // fix entities
            entities.put("hashtags", undoFixEntity(
                    entities.getJSONArray("hashtags"), replacesIndices));
            entities.put("symbols", undoFixEntity(
                    entities.getJSONArray("symbols"), replacesIndices));
            entities.put("urls", undoFixEntity(entities.getJSONArray("urls"),
                    replacesIndices));
            entities.put("user_mentions", undoFixEntity(
                    entities.getJSONArray("user_mentions"), replacesIndices));

            // Apply changes
            new_tweet.put("entities", entities);
            new_tweet.put("text", text);

            return new_tweet;
        }
        return tweet;
    }
}
