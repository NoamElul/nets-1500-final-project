import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;

/**
 * The main class
 */
public class Main {
    private Schedule schedule;
    private List<HebCalAPI.HolidayInterval> holidays;
    private List<Conflict> conflicts;

    /**
     * The entry point to the program
     *
     * @param args  Does not accept any command-line arguments
     */
    public static void main(String[] args) throws Throwable {
        Main mainObj = new Main();
        System.out.println("Welcome to Chagim Chelper: a tool to help you track which classes you may " +
                "miss for Jewish holidays.");
        System.out.println("We need to know which classes you are in, so please go on PennCoursePlan and download the .ics file or .ics link with your schedule.");
        System.out.println("Alternatively, you can download the .ics of a Google Calendar with just your classes by going to settings and downloading your schedule using the provided .ics secret address.");
        mainObj.setupAndPrompt();
        mainObj.printConflicts();
        System.out.print("Would you like us to generate emails you can send to your professors (Y/N)? ");
        String response = (new Scanner(System.in)).nextLine();
        if (response.contains("Y") || response.contains("y")) {
            mainObj.generateEmails();
        } else {
            System.out.print("Okay, thank you for using Chagim Chelper!");
        }
    }

    private record Conflict(HebCalAPI.HolidayInterval holiday, List<Schedule.CourseMeeting> courseMeetings) {}

    /**
     * Prompt the user for their calendar, then download the relevant holiday information
     * from the HebCal API, and finally calculate the conflicts in the schedule.
     */
    private void setupAndPrompt() throws IOException, InterruptedException {
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

        this.conflicts = new ArrayList<>();
        for (var h : holidays) {
            var newConflicts = schedule.meetingsInInterval(h.interval);
            if (!newConflicts.isEmpty()) {
                this.conflicts.add(new Conflict(h, newConflicts));
            }
        }
    }

    /**
     * Print the conflicts to the terminal.
     */
    private void printConflicts() {
        for (var c : conflicts) {
            if (!c.courseMeetings().isEmpty()) {
                System.out.println();
            }
            for (var m: c.courseMeetings()) {
                System.out.println("The course " + m.courseName() + " meeting from " + timeSlotString(m.meetingTime())
                        + " on " + dateSlotString(m.meetingTime()) + " conflicts with the holiday of " + c.holiday().eventName + ".");
            }
        }
        System.out.println();
        if (conflicts.isEmpty()) {
            System.out.println("There were no conflicts with your schedule");
        }
    }


    /**
     * Generate emails that the user could send to professors informing them of the class they
     * will be missing.
     */
    private void generateEmails() throws IOException {
        System.out.println("Please enter your name: ");
        String name = (new Scanner(System.in)).nextLine();
        File file = new File("chagimChelperEmails.txt");
        try {
            boolean result = file.createNewFile();
        } catch (IOException e) {
            System.out.println("Error occurred creating email file.");
        }

        FileWriter fileWriter = new FileWriter("chagimChelperEmails.txt");

        HashMap<String, List<String>> conflictsPerCourse = new HashMap<>();
        for (var conf : this.conflicts) {
            if (!conf.courseMeetings.isEmpty()) {
                System.out.println();
            }
            for (var c: conf.courseMeetings()) {
                String course = c.courseName();
                if (conflictsPerCourse.containsKey(course)) {
                    List<String> existingConflicts = conflictsPerCourse.get(course);
                    existingConflicts.add("I will be missing class on " + dateSlotString(c.meetingTime()) + " for the holiday of " + conf.holiday().eventName + ".");
                    conflictsPerCourse.put(course, existingConflicts);
                } else {
                    List<String> newConflictList = new ArrayList<String>();
                    newConflictList.add("I will be missing class on " + dateSlotString(c.meetingTime()) + " for the holiday of " + conf.holiday().eventName + ".");
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


    /**
     * Format an interval as a time range, e.g. "11:45 AM to 12:30 PM"
     *
     * @param interval  The interval to format. Only time information is taken into account
     * @return          The formatted string.
     */
    private String timeSlotString(Interval interval) {
        // For 24-hour clock format instead of am/pm, replace "hh:mm a" with "HH:mm"
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("hh:mm a");
        return dtf.format(interval.start) + " to " + dtf.format(interval.end);
    }

    /**
     * Format an interval as a date range, e.g. "4/30" or "5/6 & 5/7"
     *
     * @param interval  The interval to format. Only date information is taken into account
     * @return          The formatted string.
     */
    private static String dateSlotString(Interval interval) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd");
        String startDateString = dtf.format(interval.start);
        String endDateString = dtf.format(interval.end);
        if (startDateString.equals(endDateString)) {
            return startDateString;
        } else {
            // Realistically, this shouldn't occur for actual classes (they don't go across multiple days)
            return startDateString + " & " + endDateString;
        }
    }

}