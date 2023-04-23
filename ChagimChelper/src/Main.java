import java.util.Scanner;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Throwable{
        System.out.print("Enter the path to the .ics file with your schedule: ");
        String filename = (new Scanner(System.in)).nextLine();
        while (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        Path filepath = Path.of(filename);
        System.out.print("You entered: ");
        System.out.println(filepath);

        var schedule = new Schedule(filepath);

        for (var c : schedule.courses) {
            System.out.println(c);
        }
    }
}