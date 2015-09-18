import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Status;
import twitter4j.TwitterException;

public class MediaCaching {

    public static void downloadFile(String address, File file)
            throws Exception {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        URLConnection urlConnection = null;

        try {
            int ByteRead = 0;
            URL url = new URL(address);

            outputStream = new BufferedOutputStream(new FileOutputStream(file));

            urlConnection = url.openConnection();
            try {
                inputStream = urlConnection.getInputStream();
            } catch (Exception e) {
                System.out.println("Error downloading file");
                return;
            }
            byte[] buf = new byte[1024];
            while ((ByteRead = inputStream.read(buf)) != -1)
                outputStream.write(buf, 0, ByteRead);
        } catch (Exception e) {
            e.printStackTrace();
            file.delete();
            throw e;
        } finally {
            try {
                inputStream.close();
                outputStream.flush();
                outputStream.close();
            } catch (Exception e) {
            }
        }
    }

    public static void cacheProfileImage(JSONObject tweet, boolean update)
            throws JSONException, TwitterException {
        String url = tweet.getJSONObject("user")
                .getString("profile_image_url_https");
        File file = new File(FileHelper.avatarsFolder, url.substring(
                Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));
        if (!file.exists() || update) {
            System.out.println("Caching profile image for: "
                    + tweet.getJSONObject("user").getString("name") + " id="
                    + tweet.getJSONObject("user").getString("id_str"));

            file.delete();
            try {
                if (url.startsWith("http")) {
                    downloadFile(url, file);
                    System.out.println(
                            tweet.getJSONObject("user").getString("name"));
                }
            } catch (Exception e) {
                long id = Long.valueOf(tweet.getString("id_str"));
                Status status = Console.twitter.showStatus(id);
                url = status.getUser().getProfileImageURL();
                file.delete();
                try {
                    downloadFile(url, file);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                e.printStackTrace();
            }
        }
    }

    public static void cacheMediaImage(JSONObject tweet, boolean update)
            throws JSONException, TwitterException {
        if (!tweet.getJSONObject("entities").has("media"))
            return;
        JSONArray mediaArray = tweet.getJSONObject("entities")
                .getJSONArray("media");
        for (int i = 0; i < mediaArray.length(); i++) {
            JSONObject media = mediaArray.getJSONObject(i);
            if (!media.getString("type").equals("photo"))
                continue;
            String url = media.getString("media_url");
            if (!url.startsWith("http"))
                continue;
            File file = new File(FileHelper.mediaFolder, url.substring(
                    Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));
            if (!file.exists() || update) {
                System.out.println("Caching image: " + url);
                System.out.println(tweet.getString("created_at"));

                if (file.exists())
                    file.delete();
                try {
                    downloadFile(url, file);
                } catch (FileNotFoundException e) {
                    try {
                        if (file.exists())
                            file.deleteOnExit();
                    } catch (Exception e2) {
                        System.out.println("Cannot delete");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void redirectProfileImageToCache(JSONObject tweet)
            throws Exception {
        String url = tweet.getJSONObject("user")
                .getString("profile_image_url_https");
        if (url.startsWith("http")) {
            File file = new File(FileHelper.avatarsFolder, url.substring(
                    Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));
            if (file.exists()) {
                String newURL = "profile_images/" + file.getName();
                tweet.getJSONObject("user").put("profile_image_url_https",
                        newURL);

                long id = Long.valueOf(tweet.getString("id_str"));
                TweetsHelper.deleteTweet(id);
                TweetsHelper.saveTweet(tweet);
                System.out.println("Saved " + tweet.getString("created_at")
                        + " id= " + id);
            } else
                System.out.println("File " + file.getName()
                        + " not found for user"
                        + tweet.getJSONObject("user").getString("name"));
        }
    }

    public static void redirectMediaImageToCache(JSONObject tweet)
            throws Exception {
        if (!tweet.getJSONObject("entities").has("media"))
            return;
        JSONArray mediaArray = tweet.getJSONObject("entities")
                .getJSONArray("media");
        for (int i = 0; i < mediaArray.length(); i++) {
            if (!mediaArray.getJSONObject(i).getString("type").equals("photo"))
                continue;

            String url = mediaArray.getJSONObject(i).getString("media_url");
            if (url.startsWith("http")) {
                File file = new File(FileHelper.mediaFolder, url.substring(
                        Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\"))
                                + 1));
                if (file.exists()) {
                    String newURL = "media/" + file.getName();
                    mediaArray.getJSONObject(i).put("media_url", newURL);

                    JSONObject entities = tweet.getJSONObject("entities")
                            .put("media", mediaArray);
                    tweet.put("entities", entities);

                    long id = Long.valueOf(tweet.getString("id_str"));
                    TweetsHelper.deleteTweet(id);
                    TweetsHelper.saveTweet(tweet);
                    System.out.println("Saved " + tweet.getString("created_at")
                            + " id= " + id);
                } else {
                    System.out.println("File not found " + file.getName());
                    System.out.println(tweet.getString("id_str"));
                }
            }
        }
    }

    public static void redirectAllToLocal() throws Exception {
        ArrayList<JSONObject> tweets = FileHelper.loadAllTweets();
        Collections.sort(tweets, TweetsHelper.tweetsComparator);
        Collections.reverse(tweets);

        for (JSONObject tweet : tweets) {
            cacheProfileImage(tweet, false);
            cacheMediaImage(tweet, false);
        }

        for (JSONObject tweet : tweets) {
            redirectProfileImageToCache(tweet);
            redirectMediaImageToCache(tweet);
        }

        // Remove corrupted files
        for (File file : FileHelper.avatarsFolder.listFiles())
            if (file.length() == 0)
                file.delete();
        for (File file : FileHelper.mediaFolder.listFiles())
            if (file.length() == 0)
                file.delete();

    }

}
