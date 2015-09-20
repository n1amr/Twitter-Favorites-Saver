import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.TreeSet;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;
import twitter4j.api.FavoritesResources;

public class SaveFavorites {

    static void saveFavoritesFromSavedHTML() throws JSONException, IOException {
        // Load ids
        File idsFile = new File("data/ids/ids.txt");
        FileHelper.assureFileExists(idsFile);
        ArrayList<Long> ids = FileHelper.readIDsfromFile(idsFile);
        System.out.println("Found " + ids.size() + " ids");

        // Load progress
        JSONObject progress = FileHelper.loadProgress();
        int start_i = progress.getInt("i");

        for (int i = start_i; i < ids.size();) {
            long id = ids.get(i);
            double percentage = (int) (10000.0 * i / ids.size()) / 100.0;
            System.out.println(
                    "i = " + i + " (" + percentage + "%), id = " + id + ":");

            progress = new JSONObject();

            JSONObject jsonTweet = TweetsHelper.fastLoadSavedTweet(id);
            // if not saved before
            if (jsonTweet == null)
                // Try to get tweet and save it
                try {
                    if (!isChecked(id)) {
                        jsonTweet = TweetsHelper.getTweet(id);
                        TweetsHelper.printTweet(jsonTweet);

                        TweetsHelper.saveTweet(jsonTweet);

                        String createdAt = jsonTweet.getString("created_at");
                        int m = TweetsHelper.getMonth(createdAt);
                        int y = TweetsHelper.getYear(createdAt);

                        // Save progress
                        FileHelper.saveProgress(
                                FileHelper.loadProgress().getInt("i"),
                                FileHelper.loadProgress().getInt("page"), id, m,
                                y);

                        // Slow down for rate limit
                        // Console.pause(2000);
                    }
                } catch (Exception e) {
                    TwitterException twitterErr = (TwitterException) e;
                    int errCode = twitterErr.getErrorCode();
                    ArrayList<Integer> skipCodes = new ArrayList<>(
                            Arrays.asList(new Integer[] { 144, 179, 63, 34 }));
                    if (errCode == 88)
                        Console.rateLimitWait(twitterErr);
                    else if (skipCodes.contains(errCode)) {
                        markFailed(id);
                        System.out.println("Failed to fetch tweet #" + id);
                        i++; // go on
                    } else {
                        e.printStackTrace();
                        // Retry after 10 seconds
                        Console.pause(10000);
                    }
                }
            else { // was saved before
                TweetsHelper.printTweet(jsonTweet);
                System.out
                        .println("Skipped; This tweet has already been saved #"
                                + jsonTweet.getString("id_str"));
            }

            // Save progress
            FileHelper.saveProgress(i, FileHelper.loadProgress().getInt("page"),
                    FileHelper.loadProgress().getLong("id"),
                    FileHelper.loadProgress().getInt("month"),
                    FileHelper.loadProgress().getInt("year"));

            System.out.println();
            i++; // go on if successful
        }
    }

    static void saveFavoritesOnline() throws JSONException {
        // Load progress
        JSONObject progress = FileHelper.loadProgress();
        int page = progress.getInt("page");
        int start_i = progress.getInt("i");

        FavoritesResources favs = Console.twitter.favorites();

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

                        TweetsHelper.printTweet(jsonTweet);

                        TweetsHelper.saveTweet(jsonTweet);

                        String createdAt = jsonTweet.getString("created_at");
                        int m = TweetsHelper.getMonth(createdAt);
                        int y = TweetsHelper.getYear(createdAt);

                        // Save progress
                        FileHelper.saveProgress(i, page,
                                FileHelper.loadProgress().getLong("id"), m, y);
                        System.out.println();
                        i++;
                    } catch (Exception e) {
                        if (e instanceof TwitterException
                                && ((TwitterException) e).getErrorCode() == 88)
                            Console.rateLimitWait((TwitterException) e);
                        else {
                            e.printStackTrace();
                            // Retry after 10 seconds
                            Console.pause(10000);
                        }
                    }
                    // Console.pause(2000);
                }
                page++;
            } catch (Exception e) {
                if (e instanceof TwitterException
                        && ((TwitterException) e).getErrorCode() == 88)
                    Console.rateLimitWait((TwitterException) e);
                else {
                    e.printStackTrace();
                    // Retry after 10 seconds
                    Console.pause(10000);
                }
            }
    }

    static void updateOnline() throws JSONException, IOException {
        ArrayList<JSONObject> list = FileHelper.loadAllTweets();
        JSONArray allTweets = JSONHelper.jsonObjectsToJSONArray(list);

        FavoritesResources favs = Console.twitter.favorites();
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

                        TweetsHelper.printTweet(jsonTweet);

                        // Stop if 20 consecutive tweets are saved before
                        if (TweetsHelper.tweetExists(jsonTweet, allTweets)) {
                            n_saved++;
                            if (n_saved >= 20)
                                return;
                        } else
                            n_saved = 0; // reset
                        TweetsHelper.saveTweet(jsonTweet);

                        System.out.println();
                        i++;
                    } catch (Exception e) {
                        if (e instanceof TwitterException
                                && ((TwitterException) e).getErrorCode() == 88)
                            Console.rateLimitWait((TwitterException) e);
                        else {
                            e.printStackTrace();
                            // Retry after 10 seconds
                            Console.pause(10000);
                        }
                    }
                    // Console.pause(2000);
                }
                page++;
            } catch (Exception e) {
                if (e instanceof TwitterException
                        && ((TwitterException) e).getErrorCode() == 88)
                    Console.rateLimitWait((TwitterException) e);
                else {
                    e.printStackTrace();
                    // Retry after 10 seconds
                    Console.pause(10000);
                }
            }
    }

    static void initializeCheckedIds() throws JSONException, IOException {
        checkedIds = new HashSet<>();

        for (File file : FileHelper.failedIdsFolder.listFiles()) {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLong())
                checkId(scanner.nextLong());
            scanner.close();
        }

        ArrayList<JSONObject> tweets = FileHelper.loadAllTweets();
        for (JSONObject tweet : tweets) {
            String id_str = tweet.getString("id_str");
            long id = Long.valueOf(id_str);
            checkId(id);
        }
    }

    static boolean isChecked(long id) throws JSONException, IOException {
        if (checkedIds == null)
            initializeCheckedIds();
        return checkedIds.contains(id);
    }

    static boolean checkId(long id) throws JSONException, IOException {
        if (checkedIds == null)
            initializeCheckedIds();
        return checkedIds.add(id);
    }

    static void markDeleted(long id) throws IOException {
        File deletedIdsFile = new File(FileHelper.failedIdsFolder,
                "deleted.txt");
        FileHelper.assureFileExists(deletedIdsFile);

        ArrayList<Long> ids = FileHelper.readIDsfromFile(deletedIdsFile);

        ids.add(id);
        TreeSet<Long> uids = new TreeSet<>(ids);
        ids = new ArrayList<>(uids);
        Collections.reverse(ids);

        FileHelper.writeIDsintoFile(ids, deletedIdsFile);
    }

    static void markFailed(long id) throws IOException {
        File failedIdsFile = new File(FileHelper.failedIdsFolder, "failed.txt");
        FileHelper.assureFileExists(failedIdsFile);
        ArrayList<Long> ids = FileHelper.readIDsfromFile(failedIdsFile);

        ids.add(id);
        TreeSet<Long> uids = new TreeSet<>(ids);
        ids = new ArrayList<>(uids);
        Collections.reverse(ids);

        FileHelper.writeIDsintoFile(ids, failedIdsFile);
    }

    static HashSet<Long> checkedIds = null;

    public static void resetProgress() throws JSONException, IOException {
        FileHelper.saveProgress(0, 1, FileHelper.loadProgress().getLong("id"),
                FileHelper.loadProgress().getInt("month"),
                FileHelper.loadProgress().getInt("year"));
    }

}
