import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import twitter4j.JSONArray;
import twitter4j.JSONException;
import twitter4j.JSONObject;
import twitter4j.JSONTokener;

public class JSONHelper {
    public static final String JSON_ACCESS_KEY = "access.key";
    public static final String JSON_ACCESS_SECRET = "access.secret";
    public static final String JSON_USER_HANDLE = "twitter.handle";
    public static final String JSON_USER_ID = "twitter.ID";

    public static JSONArray getJSONArray(File file) {
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(readDataFromFile(file));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return jsonArray;
    }

    public static Object getParameter(File file, String parameterKey)
            throws JSONException, IOException {

        JSONObject jsonObject = (JSONObject) new JSONTokener(
                readDataFromFile(file)).nextValue();
        return jsonObject.get(parameterKey);
    }

    public static ArrayList<JSONObject> jsonArrayToJSONObjectsList(
            JSONArray jsonArray) {
        ArrayList<JSONObject> jsonObjects = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++)
            try {
                jsonObjects.add(jsonArray.getJSONObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }

        return jsonObjects;
    }

    public static ArrayList<Object> jsonArrayToArrayList(JSONArray jsonArray) {
        ArrayList<Object> jsonObjects = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++)
            try {
                jsonObjects.add(jsonArray.get(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }

        return jsonObjects;
    }

    public static JSONArray jsonObjectsToJSONArray(
            ArrayList<JSONObject> objects) {
        JSONArray jsonArray = new JSONArray();

        for (JSONObject jsonObject : objects)
            jsonArray.put(jsonObject);
        return jsonArray;
    }

    public static JSONArray loadJSONArray(File file) {
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(readDataFromFile(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    public static JSONObject loadJSONObject(File file) {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(readDataFromFile(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public static ArrayList<JSONObject> loadJSONObjects(File file)
            throws JSONException, IOException {
        JSONArray jsonArray = new JSONArray(readDataFromFile(file));
        return jsonArrayToJSONObjectsList(jsonArray);
    }

    public static Object loadParameter(File file, String parameterKey)
            throws JSONException, IOException {
        // JSONObject jsonObject = (JSONObject) new JSONTokener(
        // readDataFromFile(file)).nextValue();
        JSONObject jsonObject = new JSONObject(readDataFromFile(file));
        return jsonObject.get(parameterKey);
    }

    public static void main(String[] args) throws IOException, JSONException {
        String testFilename = "test.json";
        File testFile = new File(testFilename);

        Map<String, Object> params = new HashMap<>();

        params.put("Name", "Amr Alaa");
        params.put("Age", 21);

        JSONObject jsonObject = parametersToJSONObject(params);
        System.out.println(jsonObject);

        saveJSONObject(testFile, jsonObject);
        jsonObject = null;
        jsonObject = loadJSONObject(testFile);
        System.out.println(jsonObject);

        saveParameters(testFile, params);

        String name = (String) loadParameter(testFile, "Name");
        Integer age = (Integer) loadParameter(testFile, "Age");

        System.out.println(name);
        System.out.println(age);
    }

    public static JSONObject parametersToJSONObject(
            Map<String, Object> params) {
        JSONObject jsonObject = new JSONObject();
        for (String key : params.keySet())
            try {
                jsonObject.put(key, params.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        return jsonObject;
    }

    public static String readDataFromFile(File file) throws IOException {
        BufferedReader reader = null;

        reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)));

        StringBuffer text = new StringBuffer();
        String line;

        while ((line = reader.readLine()) != null)
            text.append(line + "\n");

        try {
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    public static void saveArray(File file, JSONArray jsonArray)
            throws IOException {
        writeDataIntoFile(file, jsonArray.toString());
    }

    public static void saveJSONArray(File file, JSONArray jsonArray)
            throws IOException {
        writeDataIntoFile(file, (jsonArray.toString()));
    }

    public static void saveJSONObject(File file, JSONObject jsonObject)
            throws IOException {
        writeDataIntoFile(file, jsonObject.toString());
    }

    public static void saveJSONObjects(File file, ArrayList<JSONObject> objects)
            throws IOException {
        writeDataIntoFile(file, new JSONArray(objects).toString());
    }

    public static void saveParameters(File file, Map<String, Object> params)
            throws IOException {
        writeDataIntoFile(file, parametersToJSONObject(params).toString());
    }

    public static void test() throws IOException, JSONException {
        File testFile = new File("test.json");

        Map<String, Object> params = new HashMap<>();

        params.put("Name", "Amr Alaa");
        params.put("Age", 21);

        saveParameters(testFile, params);

        String name = (String) getParameter(testFile, "Name");
        Integer age = (Integer) getParameter(testFile, "Age");
        System.out.println(name + " " + age);
    }

    public static void writeDataIntoFile(File file, String data)
            throws IOException {
        Writer writer = null;
        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file)));

            writer.write(data);
            writer.flush();
            writer.close();

        } catch (IOException e) {
            try {
                writer.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            throw e;
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}