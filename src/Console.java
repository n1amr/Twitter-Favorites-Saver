import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Scanner;
import java.util.TreeSet;

import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

public class Console {
    static Scanner scanner;

    static Twitter twitter;
    static TwitterApp twitterApp;

    static void initialize() throws Exception {
        System.out.println("Start");
        scanner = new Scanner(System.in);
        TwitterApp.setScanner(scanner);

        FileHelper.assureFolderExists(FileHelper.htmlFolder);
        FileHelper.assureFolderExists(FileHelper.failedIdsFolder);

        login();
    }

    static void login() throws JSONException, TwitterException,
            IllegalStateException, IOException {
        System.out.println("Choose account:");
        twitterApp = TwitterApp.signIn();
        twitter = twitterApp.getTwitter();

        User currentUser = twitter.showUser(twitter.getId());
        UserData.load(currentUser);

        System.out.println("Logged in as: " + currentUser.getScreenName());
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

    static boolean askBoolean(String q) {
        System.out.println(q + " (Y/N)");
        String res = scanner.nextLine().toUpperCase();
        if (res.contains("Y"))
            return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        initialize();

        int response = -1;
        while (response != 0) {
            System.out.println("*********************************************");
            System.out.println("1- Save favorites online from twitter");
            System.out.println(
                    "2- Save favorites offline from html files in \"savedHTML\" folder");
            System.out.println("3- Check if a tweet is saved");
            System.out.println("4- Add a tweet by ID");
            System.out.println("5- Remove a saved tweet");
            System.out.println("6- Retry failed");
            System.out.println("7- Save offline images");
            System.out.println("8- Change account");
            System.out.println("9- Show the most recent saved tweet");
            System.out.println("0- Exit");

            response = scanner.nextInt();
            scanner.nextLine();

            switch (response) {
                case 1: {
                    if (askBoolean("Is this the first time?")) {
                        if (askBoolean("Start over?"))
                            SaveFavorites.resetProgress();

                        SaveFavorites.saveFavoritesOnline();
                    } else
                        SaveFavorites.updateOnline();
                    break;
                }
                case 2: {
                    if (askBoolean("Refresh files?"))
                        FileHelper.collectIdsFromHTMLFolder(
                                FileHelper.htmlFolder);
                    SaveFavorites.saveFavoritesFromSavedHTML();
                    break;
                }
                case 3: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    JSONObject tweet = FileHelper.loadSavedTweet(id);
                    if (tweet != null)
                        TweetsHelper.printTweet(tweet);
                    else
                        System.out.println("This tweet is not saved");
                    break;
                }
                case 4: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();
                    System.out.println("Loading tweet...");
                    TweetsHelper.printTweet(TweetsHelper.getTweet(id));
                    TweetsHelper.saveTweet(id);
                    break;
                }
                case 5: {
                    System.out.print("Enter id:");
                    long id = scanner.nextLong();
                    scanner.nextLine();

                    JSONObject tweet = TweetsHelper.fastLoadSavedTweet(id);

                    System.out.println("Deleteing ...");
                    TweetsHelper.printTweet(tweet);

                    TweetsHelper.deleteTweetProfileImage(tweet);
                    TweetsHelper.deleteTweetImage(tweet);
                    TweetsHelper.deleteTweet(tweet);
                    break;
                }
                case 6: {
                    File deletedIdsFile = new File(FileHelper.failedIdsFolder,
                            "deleted ids.txt");
                    FileHelper.assureFileExists(deletedIdsFile);
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
                            TweetsHelper.printTweet(tweet);

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
                    break;
                }
                case 7: {
                    MediaSaving.redirectAllTweetsMediaToLocal();
                    break;
                }
                case 8: {
                    login();
                    break;
                }
                case 9: {
                    TweetsHelper.updateAllTweetsList();
                    Collections.sort(TweetsHelper.allTweets,
                            TweetsHelper.tweetsComparator);

                    if (TweetsHelper.allTweets.size() > 0)
                        TweetsHelper.printTweet(TweetsHelper.allTweets.get(0));
                    else
                        System.out.println("No saved tweets.");
                    break;
                }
            }
            System.out.println("Press ENTER to continue...");
            scanner.nextLine();
        }
        System.out.println("End");
    }
}