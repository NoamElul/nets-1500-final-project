import java.io.IOException;
import java.time.LocalDate;
import java.util.Scanner;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Throwable {
        testBoth();
    }

    private static void testBoth() throws Throwable {
        System.out.print("Enter the path to the .ics file with your schedule: ");
        String filename = (new Scanner(System.in)).nextLine();
        while (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        Path filepath = Path.of(filename);
        System.out.print("You entered: ");
        System.out.println(filepath);

        var schedule = new Schedule(filepath);

        var holidays = HebCalAPI.getHolidays(schedule.startDate.minusDays(7), schedule.endDate.plusDays(7));

        for (var h : holidays) {
            var conflicts = schedule.meetingsInInterval(h.interval);
            for (var c: conflicts) {
                System.out.println("The course " + c + " conflicts with the holiday " + h);
            }
        }
    }

    private static void testAPI() throws Throwable {
        var holidays = HebCalAPI.getHolidays(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-04-23"));
        System.out.println(holidays);
    }

    private static void testSchedule() throws Throwable {
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