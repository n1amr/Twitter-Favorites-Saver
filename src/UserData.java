import twitter4j.*;

import java.io.File;
import java.io.IOException;

public class UserData {
  static void load(User user) throws IllegalStateException, IOException,
      TwitterException, JSONException {

    // Set folders paths
    FileHelper.archiveDir = new File(
        new File("Twitter Favorites Archive").getAbsolutePath() + " for "
            + user.getScreenName());

    JSONArray users = JSONHelper
        .loadJSONArray(TwitterApp.USERS_LOGIN_DATA_FILE);
    for (int i = 0; i < users.length(); i++) {
      JSONObject jsonuser = users.getJSONObject(i);
      if (jsonuser.getInt(JSONHelper.JSON_USER_ID) == user.getId()) {
        if (jsonuser.has(JSONHelper.JSON_USER_DATA_PATH))
          FileHelper.archiveDir = new File(jsonuser.getString(JSONHelper.JSON_USER_DATA_PATH));
        break;
      }
    }

    FileHelper.avatarsFolder = new File(FileHelper.archiveDir.getAbsolutePath()
        + File.separator + "profile_images");
    FileHelper.mediaFolder = new File(
        FileHelper.archiveDir.getAbsolutePath() + File.separator + "media");
    FileHelper.recycledMediaFolder = new File(
        FileHelper.archiveDir.getAbsolutePath() + File.separator
            + "recycled media");
    FileHelper.tweetsdataDir = new File(FileHelper.archiveDir, "data/js");
    FileHelper.indexFile = new File(FileHelper.tweetsdataDir.getAbsolutePath()
        + File.separator + "tweet_index.js");

    if (!FileHelper.archiveDir.exists())
      createNewArchive(FileHelper.archiveDir);

    File file = new File(FileHelper.archiveDir, "/data");
    if (!file.exists())
      file.mkdirs();

    if (file.listFiles().length == 0)
      makeJsData(user);
  }

  private static void createNewArchive(File archiveDir) throws IOException {
    File emptyArchiveFolder = new File("Twitter Favorites Archive");
    File newArchiveFolder = FileHelper.archiveDir;
    newArchiveFolder.mkdirs();

    System.out.println("IMPORTANT: Your favorites archive will be saved at "
        + newArchiveFolder.getAbsolutePath());

    FileHelper.avatarsFolder.mkdirs();
    FileHelper.mediaFolder.mkdirs();
    FileHelper.recycledMediaFolder.mkdirs();

    new File(FileHelper.mediaFolder.getAbsolutePath() + "/videos/thumbnails/")
        .mkdirs();

    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "css/application.min.css");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder, "img/bg.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder, "img/sprite.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "js/application.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder, "js/en.js");

    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/bootstrap-dropdown.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/bootstrap-modal.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/bootstrap-tooltip.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/bootstrap-transition.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/bootstrap.min.css");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/glyphicons-halflings-white.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/bootstrap/glyphicons-halflings.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/hogan/hogan-2.0.0.min.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/jquery/jquery-1.8.3.min.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/twt/sprite.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/twt/sprite.rtl.png");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/twt/twt.all.min.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/twt/twt.min.css");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder,
        "lib/underscore/underscore-min.js");
    FileHelper.copyFile(emptyArchiveFolder, newArchiveFolder, "index.html");
  }

  private static void makeJsData(User user) throws IOException,
      IllegalStateException, TwitterException, JSONException {
    File file;
    String header;
    String fileContents;
    JSONObject jsonObject;
    JSONArray jsonArray;

    // tweets folder
    file = new File(FileHelper.archiveDir, "/data/js/tweets");
    if (!file.exists())
      file.mkdirs();

    // tweet_index
    file = new File(FileHelper.archiveDir, "/data/js/tweet_index.js");
    header = "var tweet_index = ";
    fileContents = null;
    jsonArray = null;
    try {
      fileContents = FileHelper.readDataFromFile(file)
          .substring(header.length());
      System.out.println(fileContents);
      jsonArray = new JSONArray(fileContents);
    } catch (Exception e) {
      file.createNewFile();
      jsonArray = new JSONArray();
    }

    fileContents = header + jsonArray.toString();
    FileHelper.writeDataIntoFile(file,
        TweetsHelper.unicodeEscape(fileContents));

    // payload_details
    file = new File(FileHelper.archiveDir, "/data/js/payload_details.js");
    header = "var payload_details = ";
    fileContents = null;
    jsonObject = null;
    try {
      fileContents = FileHelper.readDataFromFile(file)
          .substring(header.length());
      jsonObject = new JSONObject(fileContents);

    } catch (Exception e) {
      file.createNewFile();
      jsonObject = new JSONObject();
    }

    jsonObject.put("tweets", user.getStatusesCount());
    jsonObject.put("created_at", user.getCreatedAt());
    jsonObject.put("lang", user.getLang());

    fileContents = header + jsonObject.toString();
    FileHelper.writeDataIntoFile(file, fileContents);

    // user_details
    file = new File(FileHelper.archiveDir, "/data/js/user_details.js");
    header = "var user_details = ";
    fileContents = null;
    jsonObject = null;
    try {
      fileContents = FileHelper.readDataFromFile(file)
          .substring(header.length());
      jsonObject = new JSONObject(fileContents);

    } catch (Exception e) {
      file.createNewFile();
      jsonObject = new JSONObject();
    }

    jsonObject.put("expanded_url", user.getURL());
    jsonObject.put("screen_name", user.getScreenName());
    jsonObject.put("location", user.getLocation());
    jsonObject.put("url", user.getURL());
    jsonObject.put("full_name", user.getName());
    jsonObject.put("bio", user.getDescription());
    jsonObject.put("id", Long.toString(user.getId()));
    jsonObject.put("created_at", user.getCreatedAt());
    jsonObject.put("display_url", user.getURL());

    fileContents = header + jsonObject.toString();
    FileHelper.writeDataIntoFile(file,
        TweetsHelper.unicodeEscape(fileContents));

  }
}
