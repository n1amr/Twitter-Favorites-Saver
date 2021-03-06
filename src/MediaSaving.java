import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.TreeMap;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Status;
import twitter4j.TwitterException;

public class MediaSaving {

  public static void downloadFile(String address, File file) throws Exception {
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
        String err_msg = "Error downloading file: " + address + " to "
            + file.getAbsoluteFile();
        System.out.println(err_msg);
        throw new IOException(err_msg);
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

  public static String getVideoURL(JSONObject media) throws JSONException {
    JSONArray variants = media.getJSONObject("video_info")
        .getJSONArray("variants");
    TreeMap<String, String> mapVariants = new TreeMap<>();
    for (int j = 0; j < variants.length(); j++) {
      JSONObject variant = variants.getJSONObject(j);
      String type = variant.getString("content_type");
      String url = variant.getString("url");
      mapVariants.put(type, url);
    }

    String url = null;
    if (mapVariants.containsKey("video/mp4")) {
      url = mapVariants.get("video/mp4");
    }

    return url;
  }

  public static void saveProfileImage(JSONObject tweet, boolean update)
      throws JSONException, TwitterException {
    String url = tweet.getJSONObject("user")
        .getString("profile_image_url_https");

    File file = new File(FileHelper.avatarsFolder, url
        .substring(Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));

    if (file.exists() && !update)
      return;

    if (file.exists())
      file.delete();

    try {
      if (url.startsWith("http")) {
        System.out.println("Saving profile image of @"
            + tweet.getJSONObject("user").getString("name") + " (id="
            + tweet.getJSONObject("user").getString("id_str") + ") to "
            + file.getName());

        downloadFile(url, file);
      }
    } catch (Exception e) {
      System.out.println(
          "Problem downloading profile image. Getting new link from twitter...");
      long id = Long.valueOf(tweet.getString("id_str"));
      Status status = Console.twitter.showStatus(id);
      url = status.getUser().getProfileImageURL();
      file.delete();
      try {
        downloadFile(url, file);
      } catch (Exception e1) {
        e1.printStackTrace();
      }
    }
  }

  public static void saveMediaImage(JSONObject tweet, boolean update)
      throws JSONException, TwitterException {
    if (!tweet.getJSONObject("entities").has("media"))
      return;

    JSONArray mediaArray = tweet.getJSONObject("entities")
        .getJSONArray("media");

    for (int i = 0; i < mediaArray.length(); i++) {
      JSONObject media = mediaArray.getJSONObject(i);

      if (media.getString("type").equals("photo")) {

        String url = media.getString("media_url");
        if (!url.startsWith("http"))
          continue;

        File file = new File(FileHelper.mediaFolder, url.substring(
            Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));

        if (file.exists() && !update)
          continue;

        saveFile(url, file, "image");
      } else if (media.getString("type").equals("video")) {
        String url = getVideoURL(media);
        String thubmnail_url = media.getString("media_url_https");

        if (!url.startsWith("http"))
          continue;

        File file = new File(
            FileHelper.mediaFolder.getAbsolutePath() + "/videos", url.substring(
                Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));

        File thumbnail = new File(
            FileHelper.mediaFolder.getAbsolutePath() + "/videos/thumbnails",
            url.substring(
                Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1)
                + ".jpg");

        if (!file.exists()) {
          saveFile(url, file, "video");
          saveFile(thubmnail_url, thumbnail, "thumbnail");
        }
      }
    }
  }

  public static void saveFile(String url, File file, String type) {
    String message = null;
    if (type == null) {
      message = "Saving: " + file.getAbsolutePath() + "...";
    } else {
      message = "Saving " + type + ": " + file.getName() + "...";
    }

    try {
      file.delete();
      System.out.println(message);
      downloadFile(url, file);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
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

  public static JSONObject redirectToLocalProfileImage(JSONObject tweet)
      throws Exception {
    boolean edited = false;
    JSONObject newTweet = new JSONObject(tweet.toString());

    String url = newTweet.getJSONObject("user")
        .getString("profile_image_url_https");
    if (url.startsWith("http")) {
      File file = new File(FileHelper.avatarsFolder, url.substring(
          Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));

      if (file.exists()) {
        newTweet.getJSONObject("user").put("profile_image_url_https",
            "profile_images/" + file.getName());
        edited = true;
      } else
        System.out.println("Profile Image " + file.getName() + " for @"
            + tweet.getJSONObject("user").getString("name") + " was not found");
    }
    if (edited)
      return newTweet;
    else
      return tweet;
  }

  public static JSONObject redirectToLocalImage(JSONObject tweet)
      throws Exception {
    if (!tweet.getJSONObject("entities").has("media"))
      return tweet;

    boolean edited = false;
    JSONObject newTweet = new JSONObject(tweet.toString());

    JSONArray mediaArray = newTweet.getJSONObject("entities")
        .getJSONArray("media");
    for (int i = 0; i < mediaArray.length(); i++) {
      if (mediaArray.getJSONObject(i).getString("type").equals("photo")) {
        String url = mediaArray.getJSONObject(i).getString("media_url");
        if (url.startsWith("http")) {
          File file = new File(FileHelper.mediaFolder, url.substring(
              Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1));
          if (file.exists()) {
            String newURL = "media/" + file.getName();
            mediaArray.getJSONObject(i).put("media_url", newURL);

            JSONObject newEntities = newTweet.getJSONObject("entities");
            newEntities.put("media", mediaArray);
            newTweet.put("entities", newEntities);

            edited = true;
          } else
            System.out.println("File " + file.getName() + " for tweet id = "
                + tweet.getString("id_str") + " was not found");
        }
      } else if (mediaArray.getJSONObject(i).getString("type")
          .equals("video")) {
        String url = getVideoURL(mediaArray.getJSONObject(i));
        if (url.startsWith("http")) {
          File file = new File(
              FileHelper.mediaFolder.getAbsolutePath() + "/videos/thumbnails/",
              url.substring(
                  Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1)
                  + ".jpg");
          if (file.exists()) {
            String newURL = "media/videos/thumbnails/" + file.getName();
            mediaArray.getJSONObject(i).put("media_url", newURL);

            JSONObject newEntities = newTweet.getJSONObject("entities");
            newEntities.put("media", mediaArray);
            newTweet.put("entities", newEntities);

            edited = true;
          } else
            System.out.println("File " + file.getName() + " for tweet id = "
                + tweet.getString("id_str") + " was not found");
        }
      }
    }

    if (edited)
      return newTweet;
    else
      return tweet;
  }

  public static void redirectAllTweetsMediaToLocal() throws Exception {
    TweetsHelper.updateAllTweetsList();
    Collections.sort(TweetsHelper.allTweets, TweetsHelper.tweetsComparator);

    for (JSONObject tweet : TweetsHelper.allTweets) {
      saveProfileImage(tweet, false);
      saveMediaImage(tweet, false);

      JSONObject oldTweet = tweet;
      tweet = redirectToLocalProfileImage(tweet);
      tweet = redirectToLocalImage(tweet);
      if (tweet != oldTweet)
        TweetsHelper.saveTweet(tweet, false);
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
