import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Scanner;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterApp {

  public static Scanner scanner;

  public static final File USERS_LOGIN_DATA_FILE = new File("data/users.json");

  private static AccessToken getAccessToken(JSONObject loginData) {
    try {
      String accessKey = loginData.getString(JSONHelper.JSON_ACCESS_KEY);
      String accessSecret = loginData.getString(JSONHelper.JSON_ACCESS_SECRET);

      if (accessKey != null && accessSecret != null)
        return new AccessToken(accessKey, accessSecret);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static JSONArray loadLoggedInUsers() {
    JSONArray jsonArray;

    if (!USERS_LOGIN_DATA_FILE.exists())
      try {
        FileHelper.assureFileExists(USERS_LOGIN_DATA_FILE);
        JSONHelper.saveJSONArray(USERS_LOGIN_DATA_FILE, new JSONArray());
      } catch (Exception e) {
        e.printStackTrace();
      }
    jsonArray = JSONHelper.getJSONArray(USERS_LOGIN_DATA_FILE);
    if (jsonArray == null)
      jsonArray = new JSONArray();
    return jsonArray;
  }

  private static String prompt(String string) {
    System.out.print(string + ": ");
    return scanner.nextLine().trim();
  }

  public static void setScanner(Scanner scanner) {
    TwitterApp.scanner = scanner;
  }

  public static void showLoggedInUsers() {
    JSONArray jsonArray = loadLoggedInUsers();
    for (int i = 0; i < jsonArray.length(); i++)
      try {
        JSONObject user = (JSONObject) jsonArray.get(i);
        System.out.println(
            i + 1 + ": " + user.getString(JSONHelper.JSON_USER_HANDLE));
      } catch (JSONException e) {
        e.printStackTrace();
      }
  }

  public static TwitterApp signIn() {
    System.out.println("Select user:");
    showLoggedInUsers();
    System.out.println("0: Add a new account");
    System.out.println("-x: Remove account #x");

    int userIndex = scanner.nextInt();
    scanner.nextLine();

    JSONArray usersLoginData = loadLoggedInUsers();

    TwitterApp twitterApp = null;
    if (userIndex == 0)
      try {
        twitterApp = new TwitterApp();
      } catch (Exception e) {
        e.printStackTrace();
      }
    else if (userIndex < 0) {
      JSONArray newLoginData = new JSONArray();
      for (int i = 0; i < usersLoginData.length(); i++)
        if (i != (-userIndex) - 1)
          try {
            newLoginData.put(usersLoginData.get(i));
          } catch (JSONException e) {
            e.printStackTrace();
          }
      storeLoggedInUsers(newLoginData);
      twitterApp = signIn();
    } else
      try {
        JSONObject userLoginData = usersLoginData.getJSONObject(userIndex - 1);
        twitterApp = new TwitterApp(userLoginData);
      } catch (Exception e) {
        e.printStackTrace();
      }
    return twitterApp;
  }

  private static void storeLoggedInUsers(JSONArray usersLoginData) {
    try {
      if (usersLoginData == null)
        usersLoginData = new JSONArray();
      JSONHelper.saveArray(USERS_LOGIN_DATA_FILE, usersLoginData);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Twitter twitter;

  public TwitterApp() throws TwitterException, JSONException {
    this(null);
  }

  public TwitterApp(JSONObject userLoginData)
      throws JSONException, TwitterException {
    ConfigurationBuilder builder = new ConfigurationBuilder()
        .setJSONStoreEnabled(true);
    twitter = new TwitterFactory(builder.build()).getInstance();

    // Set API Keys
    twitter.setOAuthConsumer(API_Keys.OAUTH_CONSUMERKEY,
        API_Keys.OAUTH_CONSUMERSECRET);

    AccessToken accessToken = null;

    // Get Access token
    if (userLoginData != null)
      accessToken = getAccessToken(userLoginData);
    else
      accessToken = getNewAccessToken();

    // Set Access token
    twitter.setOAuthAccessToken(accessToken);

    storeUserLoginData();
  }

  private AccessToken getNewAccessToken() throws TwitterException {
    RequestToken requestToken = twitter.getOAuthRequestToken();
    System.out.println(requestToken.getAuthenticationURL());

    // Open in browser
    if (Desktop.isDesktopSupported())
      if (Console.askBoolean("Open authorization link in browser?"))
        try {
          Desktop.getDesktop()
              .browse(new URI(requestToken.getAuthenticationURL()));
        } catch (Exception e) {
          e.printStackTrace();
        }
    AccessToken accessToken = twitter
        .getOAuthAccessToken(prompt("Please enter the PIN"));
    return accessToken;
  }

  public Twitter getTwitter() {
    return twitter;
  }

  private void storeUserLoginData() {
    try {
      JSONObject currentUser = new JSONObject();
      currentUser.put(JSONHelper.JSON_USER_HANDLE, twitter.getScreenName());
      currentUser.put(JSONHelper.JSON_USER_ID, twitter.getId());
      currentUser.put(JSONHelper.JSON_ACCESS_KEY,
          twitter.getOAuthAccessToken().getToken());
      currentUser.put(JSONHelper.JSON_ACCESS_SECRET,
          twitter.getOAuthAccessToken().getTokenSecret());

      // Load all users
      JSONArray users = loadLoggedInUsers();

      // Check if it's been saved before
      boolean exists = false;
      for (int i = 0; i < users.length(); i++)
        if (users.getJSONObject(i).getLong(JSONHelper.JSON_USER_ID) == twitter
            .getId())
          exists = true;

      // Add only if it's new
      if (!exists)
        users.put(currentUser);

      // Save all
      storeLoggedInUsers(users);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}