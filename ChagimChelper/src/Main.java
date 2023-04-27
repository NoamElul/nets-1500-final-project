import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;

public class Main {
    private Schedule schedule;
    private List<HebCalAPI.HolidayInterval> holidays;
    boolean anyConflicts = false;
    HashMap<String, List<String>> conflictsPerCourse;
    String name = "";


    public static void main(String[] args) throws Throwable {
        System.out.println("Hello! \nTo start please enter your name: ");
        String name = (new Scanner(System.in)).nextLine();
        Main obj = new Main(name);
        System.out.println("Welcome to Chagim Chelper: a tool to help you track which classes you may " +
                "miss for Jewish holidays.");
        System.out.println("We need to know which classes you are in, so please go on PennCoursePlan and download the .ics file or .ics link with your schedule.");
        System.out.println("Alternatively, you can download the .ics of a Google Calendar with just your classes by going to settings and downloading your schedule using the provided .ics secret address.");
        obj.testBoth();
        System.out.print("Would you like us to generate emails you can send to your professors (Y/N)? ");
        String response = (new Scanner(System.in)).nextLine();
        if (response.contains("Y") || response.contains("y")) {
            obj.generateEmails();
        } else {
            System.out.print("Okay, thank you for using Chagim Chelper!");
        }
    }

    public Main(String name) {
        this.name = name;
    }

    private void generateEmails() throws Throwable {
        //FINISH IMPLEMENTING
        File file = new File("chagimChelperEmails.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("Error occurred creating email file.");
        }

        FileWriter fileWriter = new FileWriter("chagimChelperEmails.txt");

        conflictsPerCourse = new HashMap<String, List<String>>();
        for (var h : holidays) {
            var conflicts = schedule.meetingsInInterval(h.interval);
            if (!conflicts.isEmpty()) {
                System.out.println();
                anyConflicts = true;
            }
            for (var c: conflicts) {
                String course = c.courseName();
                if (conflictsPerCourse.containsKey(course)) {
                    List<String> existingConflicts = conflictsPerCourse.get(course);
                    existingConflicts.add("I will be missing class on " + dateSlot(c.meetingTime()) + " for the holiday of " + h.eventName + ".");
                    conflictsPerCourse.put(course, existingConflicts);
                } else {
                    List<String> newConflictList = new ArrayList<String>();
                    newConflictList.add("I will be missing class on " + dateSlot(c.meetingTime()) + " for the holiday of " + h.eventName + ".");
                    conflictsPerCourse.put(course, newConflictList);
                }

            }
        }

        for (String course : conflictsPerCourse.keySet()) {

            // See if class should be plural
            String c = "class";
            if (conflictsPerCourse.get(course).size() > 1) {
                c += "es";
            }
            fileWriter.write("\n\n -------" + course + "-------\n");
            fileWriter.write(" \nDear Professor, \n\n");
            fileWriter.write("I hope this email finds you well. I am enrolled to take " + course +  " with you this semester.\n\n");
            fileWriter.write("I wanted to reach out to you now to let you know that I am an observant Jew and will have to miss some " + c + " due to conflicts with Jewish holidays.\n\n");
            for (int i = 0; i < conflictsPerCourse.get(course).size(); i++) {
                fileWriter.write(conflictsPerCourse.get(course).get(i));
                fileWriter.write("\n");
            }

            fileWriter.write("\nI'm looking forward to taking your class, and hope these absences will not be too much of an inconvenience.\n" +
                    "\n" +
                    "Thank you so much for your understanding!\n");

            if (name.length() > 0) {
                fileWriter.write("\nBest, \n" + name);
            }

        }
        System.out.print("Email(s) generated into the file chagimChelperEmails.txt.\nThank you for using our tool!");

        fileWriter.close();
    }

    private void testBoth() throws Throwable {
        System.out.print("Enter the filepath or URL to the .ics file with your schedule: ");
        String path = (new Scanner(System.in)).nextLine();
        while (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (path.startsWith("http")) {
            URI url = URI.create(path);
            System.out.print("You entered: ");
            System.out.println(path);

            this.schedule = new Schedule(url);
        } else {
            Path filepath = Path.of(path);
            System.out.print("You entered: ");
            System.out.println(filepath);

            this.schedule = new Schedule(filepath);
        }

        this.holidays = HebCalAPI.getHolidays(schedule.startDate.minusDays(7), schedule.endDate.plusDays(7));

        for (var h : holidays) {
            var conflicts = schedule.meetingsInInterval(h.interval);
            if (!conflicts.isEmpty()) {
                System.out.println();
                this.anyConflicts = true;
            }
            for (var c: conflicts) {
                System.out.println("The course " + c.courseName() + " meeting from " + timeSlot(c.meetingTime())
                        + " on " + dateSlot(c.meetingTime()) + " conflicts with the holiday of " + h.eventName + ".");

            }
        }
        System.out.println();
        if (!anyConflicts) {
            System.out.println("There were no conflicts with your schedule");
        }
    }

    private String timeSlot(Interval interval) {
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

    private void testAPI() throws Throwable {
        var holidays = HebCalAPI.getHolidays(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-04-23"));
        System.out.println(holidays);
    }

    private  void testSchedule() throws Throwable {
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