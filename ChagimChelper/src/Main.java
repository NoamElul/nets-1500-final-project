import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Throwable {
        System.out.println("Welcome to Chagim Chelper: a tool to help you track which classes you may " +
                "miss for Jewish holidays.");
        System.out.println("We need to know which classes you are in, so please go on PennCoursePlan and download the .ics file with your schedule.");
        testBoth();
        System.out.print("Would you like us to generate emails you can send to your professors (Y/N)?");
        String response = (new Scanner(System.in)).nextLine();
        if (response.contains("Y") || response.contains("y")) {
            generateEmails();
        } else {
            System.out.print("Okay, thank you for using Chagim Chelper!");
        }
    }

    private static void generateEmails() throws Throwable {
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

        //FINISH IMPLEMENTING
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
                System.out.println("The course " + c.courseName() + " meeting from " + timeSlot(c.meetingTime())
                        + " on " + dateSlot(c.meetingTime()) + " conflicts with the holiday of " + h.eventName + ".");

            }
        }
    }

    private static String timeSlot(Interval interval) {
        String intervalString = interval.toString();

        int timeStartIndex = intervalString.indexOf("start=");
        int timeEndIndex = intervalString.indexOf("end=");
        String time = intervalString.substring(timeStartIndex + 17, timeStartIndex + 22) + "-" +
                intervalString.substring(timeEndIndex + 15, timeEndIndex + 20);


        return time;
    }

    private static String dateSlot(Interval interval) {
        String intervalString = interval.toString();

        int dateStartIndex = intervalString.indexOf('-');
        String date = intervalString.substring(dateStartIndex + 1, dateStartIndex + 3) + "/"
                + intervalString.substring(dateStartIndex + 4, dateStartIndex + 6);

        return date;
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