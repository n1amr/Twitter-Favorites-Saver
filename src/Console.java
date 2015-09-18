import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Scanner;
import java.util.TreeSet;

import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public class Console {
    static Scanner scanner;

    static Twitter twitter;
    static TwitterApp twitterApp;

    static void login() throws JSONException, TwitterException {
        twitterApp = new TwitterApp(
                JSONHelper.loadJSONArray(TwitterApp.USERS_LOGIN_DATA_FILE)
                        .getJSONObject(0));
        twitter = twitterApp.getTwitter();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Start");
        System.out.println("Logging in...");
        scanner = new Scanner(System.in);
        TwitterApp.setScanner(scanner);

        login();

        int response;
        do {
            System.out.println("1- Save favorites online from twitter");
            System.out.println(
                    "2- Save favorites offline from html files in \"savedHTML\" folder");
            System.out.println("3- Check if a tweet is saved");
            System.out.println("4- Add a tweet by ID");
            System.out.println("5- Remove a saved tweet");
            System.out.println("6- Retry failed");
            System.out.println("7- Save offline images");
            System.out.println("0- Exit");

            response = scanner.nextInt();
            scanner.nextLine();

            switch (response) {
                case 1: {
                    System.out.println("Is this the first time?");
                    String res = scanner.nextLine().toUpperCase();
                    if (res.contains("Y"))
                        SaveFavorites.saveFavoritesOnline();
                    else
                        SaveFavorites.updateOnline();
                    MediaCaching.redirectAllToLocal();
                    break;
                }
                case 2: {
                    System.out.println("Refresh files? (Y/N)");
                    String res = scanner.nextLine().toUpperCase();
                    if (res.contains("Y"))
                        FileHelper.collectIdsFromHTMLFolder(
                                FileHelper.htmlFolder);
                    SaveFavorites.saveFavoritesFromSavedHTML();
                    MediaCaching.redirectAllToLocal();
                    break;
                }
                case 3: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println(FileHelper.loadSavedTweet(id));
                    break;
                }
                case 4: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println("Adding ...");
                    System.out.println(TweetsHelper.getTweet(id).get("text"));
                    TweetsHelper.saveTweet(id);
                    MediaCaching.redirectAllToLocal();
                    break;
                }
                case 5: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println("Deleteing ...");
                    System.out.println(
                            TweetsHelper.fastLoadSavedTweet(id).get("text"));
                    // TODO MediaCaching.deleteMediaForTweet(id);
                    TweetsHelper.deleteTweet(id);
                    break;
                }
                case 6: {
                    File deletedIdsFile = new File(FileHelper.failedIdsFolder,
                            "deleted ids.txt");
                    ArrayList<Long> ids = FileHelper
                            .readIDsfromFile(deletedIdsFile);

                    TreeSet<Long> uids = new TreeSet<>(ids);
                    ids = new ArrayList<>(uids);
                    Collections.reverse(ids);

                    ArrayList<Long> toRemoveFromFile = new ArrayList<>();

                    for (int i = 0; i < ids.size(); i++) {
                        Long id = ids.get(i);
                        System.out.println(i + ": id=" + id);
                        try {
                            TweetsHelper.saveTweet(id);

                            JSONObject tweet = FileHelper.loadSavedTweet(id);
                            System.out.println("Successfully saved " + id);
                            System.out.println(tweet.get("text"));

                            toRemoveFromFile.add(id);
                        } catch (TwitterException e) {
                            if (e.getErrorCode() == 179
                                    || e.getErrorCode() == 63) {
                                SaveFavorites.markDeleted(id);
                                toRemoveFromFile.add(id);
                            } else if (e.getErrorCode() == 88)
                                rateLimitWait(e);
                            else
                                e.printStackTrace();
                        }
                    }

                    for (Long id : toRemoveFromFile)
                        ids.remove(id);
                    MediaCaching.redirectAllToLocal();
                    break;
                }
                case 7: {
                    MediaCaching.redirectAllToLocal();
                    break;
                }
            }
        } while (response != 0);
        System.out.println("End");
    }

    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
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
}