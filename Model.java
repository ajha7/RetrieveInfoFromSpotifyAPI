import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.*;

public class Model {
    private static String userRequestTypeLink = "";
    private static String userRequestType = "";
    private static boolean flag = false;
    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scan = new Scanner(System.in);
        Controller controller = new Controller();
        boolean authorization = false;
        while(scan.hasNext()) {
            String input = scan.nextLine();
            Pattern playlistPattern = Pattern.compile("playlists[\\s]*(.*)");
            Matcher playlistType = playlistPattern.matcher(input);

            if (input.matches("auth")) {
                authorization = true;
                View view = new View();
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("-access")) {
                        controller.setSpotifyAccessServerPoint(args[i + 1]);     //custom authorization server path
                    } else if (args[i].equals("-resource")) {
                        controller.setBaseApiPath(args[i + 1]);     //default authorization server path, "https://accounts.spotify.com"
                    }
                }
                controller.createHttpServer();
                System.out.println("---SUCCESS---");
            }
            if (!authorization) {
                System.out.println("Please, provide access for application.");
                continue;
            }
            if (input.matches("featured")) {
                System.out.println("---FEATURED---");
                userRequestTypeLink = "featured-playlists";
                flag = true;

            } else if (input.matches("new")) {
                System.out.println("---NEW RELEASES---");
                userRequestTypeLink = "new-releases";
                flag = true;

            } else if (input.matches("categories")) {
                System.out.println("---CATEGORIES---");
                userRequestTypeLink = "categories";
                flag = true;

            } else if (playlistType.find()) {
                System.out.println("---" + playlistType.group(1).toUpperCase() + " PLAYLISTS---");
                userRequestTypeLink = "categories/{category_id}/playlists";
                flag = true;
            } else if (input.matches("prev")) {
                //move previous page
            } else if(input.matches("next")) {
                //move next page
            } else if (input.matches("exit")) {
                System.out.println("---GOODBYE!---");
            }

            if(flag) {
                userRequestType = input;
                controllerRequest();
                flag = false;
            }
        }
    }

    public static void controllerRequest() throws IOException, InterruptedException {
        Controller controller = new Controller();
        controller.triggerRequests(userRequestTypeLink, userRequestType);
    }
}
