import com.sun.net.httpserver.*;

import java.net.*;
import java.net.http.*;
import com.google.gson.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.*;
import java.util.*;

public class Controller {
    private HttpServer httpServer;
    //secret only known by my application and authorization server, so authorization server only grants tokens to authorized requestors
    private static final String CLIENT_SECRET = "dccd75d314114b4293161b0ce5107100";
    protected static String CLIENT_ID = "176317a66ba447a289906b9af2780bd2";     //used to identify specific application(e.g. this one)
    protected static String REDIRECT_URL = "http://localhost:8080";  // redirecting user to this link when agreeing to scopes; in this case localhost on port 8080(http alt)
    protected static String SPOTIFY_ACCESS_SERVER_POINT = "https://accounts.spotify.com";    //default spotify server
    private static String TOKEN_URL = SPOTIFY_ACCESS_SERVER_POINT + "/api/token";   //url to get access token from Spotify Accounts service, spotify server to /api/token endpoint
    protected static String BASE_API_PATH = "https://api.spotify.com/v1/browse/";
    protected static String authorizationCode = "";
    protected static String accessToken = "";
    View view = new View();

    Controller() {}
    public void setSpotifyAccessServerPoint(String accessPoint) {
        SPOTIFY_ACCESS_SERVER_POINT = accessPoint;
        TOKEN_URL = SPOTIFY_ACCESS_SERVER_POINT + "/api/token";   //custom spotify access server point
    }
    public void setBaseApiPath(String apiPath) {
        BASE_API_PATH = apiPath + "/v1/browse/";
    } //custom BASE_API_PATH

    public void createHttpServer() {
        try {
            httpServer = HttpServer.create();    //creates http server
            /**binds http server to port 8080 with 0 for maximum backlog, meaning reject connections if server is currently serving connections,
             see https://stackoverflow.com/questions/36594400/what-is-backlog-in-tcp-connections for more backlog details */
            httpServer.bind(new InetSocketAddress(8080), 0);
            /**maps URI to handler which handles HTTP requests to server*/
            httpServer.createContext("/",
                    new HttpHandler() {
                        public void handle(HttpExchange exchange) throws IOException {
                            String query = exchange.getRequestURI().getQuery();     //query that Spotify API sends back after user accepts scopes
                            Pattern codePattern = Pattern.compile("code=(.*)");
                            Matcher codeMatcher = null;
                            String messageCodeResult = "";
                            if(query != null) {
                                codeMatcher = codePattern.matcher(query);
                                if (codeMatcher.find()) {
                                    messageCodeResult = "Got the code. Return back to your program.";
                                    authorizationCode = codeMatcher.group(1);       //extracting code from query
                                }
                                else {
                                    messageCodeResult = "Not found authorization code. Try again.";
                                }
                            }
                            else {
                                messageCodeResult = "Not found authorization code. Try again.";
                            }
                            exchange.sendResponseHeaders(200, messageCodeResult.length());
                            exchange.getResponseBody().write(messageCodeResult.getBytes());     //sending message to localhost:8080
                            exchange.getResponseBody().close();
                        }
                    }
            );

            /**null value means default executor is used; "An Executor must be established before start() is called, All HTTP requests are handled in tasks given to the executor" */
            httpServer.setExecutor(null);
            this.httpServer.start();      //starts server
            System.out.println("use this link to request access code");
            System.out.println(SPOTIFY_ACCESS_SERVER_POINT + "/authorize?client_id="+ CLIENT_ID + "&redirect_uri=" + REDIRECT_URL + "&response_type=code"); //link which sends user to accept/deny scopes
            System.out.println("waiting for code...");
            while(authorizationCode.equals("")){
                Thread.sleep(10);   //waits for code
            }
            System.out.println("code received");
            String accessTokenJson = makeRequestForToken(authorizationCode);     //sends Spotify API the authorization code along with other params in exchange for access token
            this.httpServer.stop(1);    //shuts server down
            accessToken = parseJsonForAccessToken(accessTokenJson);  //parsed access token
        } catch (IOException | InterruptedException e) {
            System.out.println("error IOException/Interrupted " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String makeRequestForToken(String authorizationCode) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        /**params grant type, authorization code, and the redirect url(has to be same as specified in authorization link) */
        String data = "grant_type=authorization_code&code=" + authorizationCode + "&redirect_uri=" + REDIRECT_URL + "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET;
        HttpRequest request = HttpRequest.newBuilder()      /**creating/sending(.build()) POST request to Spotify API for access token*/
                .header("Content-Type", "application/x-www-form-urlencoded")
                .uri(URI.create(TOKEN_URL))
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());     //Spotify's response to request, contains JSON with access token, etc.
        return response.body();
    }

    /** initiates process for user request
     @param     userRequestTypeLink     variable part of link to send to Spotify API to retrieve the type of information that particular link requests
     @param     userRequestType         type of request the user sent, used to determine what JSON to parse
     @see       #makeRequestForInfo(String)
     */
    public void triggerRequests(String userRequestTypeLink, String userRequestType) throws IOException, InterruptedException {
        if(userRequestTypeLink.contains("{category_id}")) {
            userRequestTypeLink = userRequestTypeLink.replaceAll("\\{category_id}", makeCategoryIDRequest(userRequestType));
        }
        String json = makeRequestForInfo(userRequestTypeLink);
        try {
            switch (userRequestType) {
                case "new":
                    parseJsonForNewInfo(json);
                    break;
                case "featured":
                    parseJsonForFeaturedInfo(json);
                    break;
                case "categories":
                    parseJsonForCategoriesInfo(json);
                    break;
                default:
                    if (userRequestType.matches("playlists[\\s]*(.*)")) {
                        parseJsonForPlaylistsInfo(json);
                    }
                    break;
            }
        }catch(NullPointerException e){
            System.out.println("Unsuccesful request");
        }
    }

    /** requests Spotify API for user requested info
     @return json text of info from user account
     */
    public String makeRequestForInfo(String userRequestTypeLink) throws IOException, InterruptedException {
        String API_PATH = BASE_API_PATH + userRequestTypeLink;
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .uri(URI.create(API_PATH))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String json = response.body();
        return json;
    }

    /** all parseJsons parse the given JSON for the desired values
     @param  json from makeRequestOfInfo
     */
    public void parseJsonForNewInfo(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject album = jsonObject.getAsJsonObject("albums");
        JsonArray jsonArray = album.getAsJsonArray("items");
        List<ArrayList<String>> list = new ArrayList<>();
        ArrayList<String> albums = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        for(JsonElement je: jsonArray) {
            JsonObject objs = je.getAsJsonObject();
            albums.add(objs.get("name").getAsString());
            artists.add(objs.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString());
            links.add(objs.get("external_urls").getAsJsonObject().get("spotify").getAsString());
        }
        list.add(albums);
        list.add(artists);
        list.add(links);
        view.printListofLists(list);
    }

    public void parseJsonForCategoriesInfo(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject category = jsonObject.getAsJsonObject("categories");
        JsonArray jsonArray = category.getAsJsonArray("items");
        List<String> categories = new ArrayList<>();
        for (JsonElement je : jsonArray) {
            JsonObject objs = je.getAsJsonObject();
            categories.add(objs.get("name").getAsString());
        }
        view.printSingleList(categories);
    }

    public void parseJsonForFeaturedInfo(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject playlists = jsonObject.getAsJsonObject("playlists");
        JsonArray jsonArray = playlists.getAsJsonArray("items");
        List<ArrayList<String>> list = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> descriptions = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        for (JsonElement je : jsonArray) {
            JsonObject objs = je.getAsJsonObject();
            names.add(objs.get("name").getAsString());
            descriptions.add(objs.get("description").getAsString());
            String desc = objs.get("description").getAsString();
            if(desc.contains(" <a href"))
                desc = desc.substring(0, (desc.indexOf("<") - 1));
            descriptions.add(desc);
            links.add(objs.get("external_urls").getAsJsonObject().get("spotify").getAsString());
        }
        list.add(names);
        list.add(descriptions);
        list.add(links);
        view.printListofLists(list);
    }

    /** makes the request for category ID to replace the variable part of the link when requesting playlists
     @param userRequestType type of request user entered
     @return parsed json of returned categoryID value
     @see #parseJsonForCategoryId(String, String)
     */
    public String makeCategoryIDRequest(String userRequestType) throws IOException, InterruptedException {
        Pattern playlistPattern = Pattern.compile("playlists[\\s]*(.*)");
        Matcher playlistType = playlistPattern.matcher(userRequestType);
        String category =  "";
        String CATEGORY_ID_PATH = BASE_API_PATH + "categories";
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .uri(URI.create(CATEGORY_ID_PATH))
                .GET()
                .build();
        HttpResponse<String> response = null;
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String json = response.body();
        if(playlistType.find())
            category = playlistType.group(1);
        String categoryId = parseJsonForCategoryId(category, json);
        return categoryId;
    }

    public String parseJsonForCategoryId(String category, String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject categories = jsonObject.getAsJsonObject("categories");
        JsonArray jsonArray = categories.getAsJsonArray("items");
        for (JsonElement je : jsonArray) {
            JsonObject objs = je.getAsJsonObject();
            if (category.equalsIgnoreCase(objs.get("name").getAsString())) {
                return objs.get("id").getAsString();
            }
        }
        return "";
    }

    public void parseJsonForPlaylistsInfo(String json) {
        if(json.contains("error")) {
            System.out.println("Specified id doesn't exist");
            return;
        }
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        JsonObject playlists = jsonObject.getAsJsonObject("playlists");
        JsonArray jsonArray = playlists.getAsJsonArray("items");
        List<ArrayList<String>> list = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> descriptions = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        for (JsonElement je : jsonArray) {
            JsonObject objs = je.getAsJsonObject();
            names.add(objs.get("name").getAsString());
            String desc = objs.get("description").getAsString();
            if(desc.contains(" <a href"))
                desc = desc.substring(0, (desc.indexOf("<") - 1));
            descriptions.add(desc);
            links.add(objs.get("external_urls").getAsJsonObject().get("spotify").getAsString());
        }
        list.add(names);
        list.add(descriptions);
        list.add(links);
        view.printListofLists(list);
    }

    /** parses the access token json Spotify API returns
     @param accessTokenJson json of access token
     @return parsed value of accessToken
     */
    public String parseJsonForAccessToken(String accessTokenJson) {
        JsonObject jo = JsonParser.parseString(accessTokenJson).getAsJsonObject();
        String accessToken = jo.get("access_token").getAsString();
        return accessToken;
    }
}
