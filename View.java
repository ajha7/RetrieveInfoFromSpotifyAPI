import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class View {

    public void printListofLists(List<ArrayList<String>> list) {
        for(int i = 0; i < list.get(0).size(); i++) {
            for(int j = 0; j < list.size(); j++) {
                System.out.println(list.get(j).get(i));
            }
            System.out.println("\n");
        }
    }

    public void printSingleList(List<String> categories) {
        for (int i = 0; i < categories.size(); i++) {
            System.out.println(categories.get(i));
        }
        System.out.println("\n");
    }

}
