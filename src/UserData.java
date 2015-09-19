import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.TwitterException;
import twitter4j.User;

public class UserData {
    static void load(User user) throws IllegalStateException, IOException,
            TwitterException, JSONException {

        // Set folders paths
        FileHelper.archiveDir = new File(
                new File("Twitter Favorites Archive").getAbsolutePath()
                        + " for " + user.getScreenName());

        //
        JSONArray users = JSONHelper
                .loadJSONArray(TwitterApp.USERS_LOGIN_DATA_FILE);
        for (int i = 0; i < users.length(); i++) {
            JSONObject jsonuser = users.getJSONObject(i);
            if (jsonuser.getInt("twitter.ID") == user.getId()) {
                FileHelper.archiveDir = new File(
                        jsonuser.getString("folder_path"));
                break;
            }
        }

        FileHelper.avatarsFolder = new File(
                FileHelper.archiveDir.getAbsolutePath() + File.separator
                        + "profile_images");
        FileHelper.mediaFolder = new File(
                FileHelper.archiveDir.getAbsolutePath() + File.separator
                        + "media");
        FileHelper.tweetsdataDir = new File(FileHelper.archiveDir, "data/js");
        FileHelper.indexFile = new File(
                FileHelper.tweetsdataDir.getAbsolutePath() + File.separator
                        + "tweet_index.js");

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

        FileHelper.avatarsFolder.mkdirs();
        FileHelper.mediaFolder.mkdirs();

        copyFile(emptyArchiveFolder, newArchiveFolder,
                "css/application.min.css");
        copyFile(emptyArchiveFolder, newArchiveFolder, "img/bg.png");
        copyFile(emptyArchiveFolder, newArchiveFolder, "img/sprite.png");
        copyFile(emptyArchiveFolder, newArchiveFolder, "js/application.js");
        copyFile(emptyArchiveFolder, newArchiveFolder, "js/en.js");

        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/bootstrap-dropdown.js");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/bootstrap-modal.js");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/bootstrap-tooltip.js");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/bootstrap-transition.js");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/bootstrap.min.css");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/glyphicons-halflings-white.png");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/bootstrap/glyphicons-halflings.png");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/hogan/hogan-2.0.0.min.js");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/jquery/jquery-1.8.3.min.js");
        copyFile(emptyArchiveFolder, newArchiveFolder, "lib/twt/sprite.png");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/twt/sprite.rtl.png");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/twt/twt.all.min.js");
        copyFile(emptyArchiveFolder, newArchiveFolder, "lib/twt/twt.min.css");
        copyFile(emptyArchiveFolder, newArchiveFolder,
                "lib/underscore/underscore-min.js");
        copyFile(emptyArchiveFolder, newArchiveFolder, "index.html");
    }

    static void assureFileExists(File file) throws IOException {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
    }

    private static void copyFile(File emptyArchiveFolder, File newArchiveFolder,
            String absoluteFilename) throws IOException {
        File file1 = new File(emptyArchiveFolder, absoluteFilename);
        File file2 = new File(newArchiveFolder, absoluteFilename);
        assureFileExists(file2);
        copyFile(file1, file2);
    }

    private static void copyFile(File source, File dest) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0)
                output.write(buf, 0, bytesRead);
        } finally {
            input.close();
            output.close();
        }
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
