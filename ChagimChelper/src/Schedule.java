import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A class representing a user's course schedule.
 */
public class Schedule {
    /**
     * A list of the user's courses
     */
    List<Course> courses;
    /**
     * The earliest date of any course meeting
     */
    LocalDate startDate;
    /**
     * The latest date of any course meeting
     */
    LocalDate endDate;

    /**
     * Parse the user's schedule from a .ics file
     *
     * @param filePath  The path to the file, which must be in the .ics format
     */
    public Schedule(Path filePath) {
        this(Schedule.getScheduleFromFile(filePath));
    }

    /**
     * Request the user's schedule from a url
     *
     * @param url  The URL to request data from. The response must be a .ics calendar file
     */
    public Schedule(URI url) {
        this(Schedule.getScheduleFromURL(url));
    }

    /**
     * Request the user's .ics schedule from a url
     *
     * @param url  The url to request from
     * @return     The response body as a string

     */
    private static String getScheduleFromURL(URI url) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(url).build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while making a request to the given url");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
        if (response.statusCode() != 200) {
            System.out.println("The request to the url returned unsuccessful status code " + response.statusCode()
            + ".\nIf this is a PennCoursePlan url, use your browser to check the url is valid."
            + "\nIf this is a Google Calendar url, use incognito mode to check that the link is publicly viewable");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
        return response.body();
    }

    private static String getScheduleFromFile(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            System.out.println("An error occurred while reading from the schedule file.\nEnsure that the file exists and is accessible.");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
    }

    /**
     * Parse the user's schedule from a .ics file represented as plaintext.
     *
     * @param icsText  The text of the .ics file
     */
    public Schedule(String icsText) {
        try {
            String icsTextConcat = CachedRegex.pattern("\\R[ \\t]", Pattern.UNICODE_CHARACTER_CLASS).matcher(icsText).replaceAll("");
            String[] icsLines = CachedRegex.pattern("\\R", Pattern.UNICODE_CHARACTER_CLASS).split(icsTextConcat);

            boolean pennLabsMode = false;
            this.courses = new ArrayList<>();

            int lineNum = -1;
            label:
            while (++lineNum < icsLines.length) {
                String line = icsLines[lineNum];
                switch (line) {
                    case "END:VCALENDAR":
                        break label;
                    case "PRODID:Penn Labs":
                        pennLabsMode = true;
                        break;
                    case "BEGIN:VEVENT":
                        // Process VEVENT
                        String courseName = null;
                        LocalDateTime startDateTimePenn = null;
                        LocalDateTime endDateTimePenn = null;

                        Set<DayOfWeek> rruleWeeklyDays = null;
                        LocalDateTime rruleUntilPenn = null;

                        while (++lineNum < icsLines.length) {
                            line = icsLines[lineNum];
                            if (line.equals("END:VEVENT")) {
                                break;
                            } else if (line.startsWith("SUMMARY:")) {
                                courseName = line.substring(8);
                            } else if (line.startsWith("DTSTART")) {
                                ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                                startDateTimePenn = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                            } else if (line.startsWith("DTEND")) {
                                ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                                endDateTimePenn = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                            } else if (line.startsWith("RRULE:")) {
                                String[] components = CachedRegex.pattern("[:;]").split(line);
                                for (String comp : components) {
                                    if (comp.startsWith("UNTIL=")) {
                                        String untilString = comp.substring(6);
                                        ZonedDateTime parsedDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
                                                .parse(untilString)
                                                .query(ZonedDateTime::from);
                                        if (pennLabsMode) {
                                            // See https://github.com/pennlabs/penn-courses/issues/490
                                            rruleUntilPenn = parsedDateTime.toLocalDateTime();
                                        } else {
                                            rruleUntilPenn = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                                        }

                                    } else if (comp.startsWith("BYDAY=")) {
                                        String allDaysString = comp.substring(6);
                                        String[] dayStrings = CachedRegex.pattern(",").split(allDaysString);
                                        rruleWeeklyDays = Arrays.stream(dayStrings)
                                                .map(Schedule::parseDayOfWeek)
                                                .collect(Collectors.toSet());
                                    }
                                }
                            }
                        }

                        if (courseName == null || startDateTimePenn == null || endDateTimePenn == null) {
                            System.out.println("Could not find info for a course, skipping:");
                            System.out.println("courseName = " + courseName + ", startDateTimePenn = " + startDateTimePenn + ", endDateTimePenn = " + endDateTimePenn);
                            continue;
                        }

                        Course course;
                        if (rruleWeeklyDays != null) {
                            WeeklyCourse courseW = new WeeklyCourse(courseName, rruleWeeklyDays,
                                    startDateTimePenn.toLocalTime(), endDateTimePenn.toLocalTime(),
                                    startDateTimePenn.toLocalDate(), (rruleUntilPenn == null) ? null : rruleUntilPenn.toLocalDate());
                            course = courseW;

                            if (this.startDate == null || (courseW.startDate != null && this.startDate.isAfter(courseW.startDate))) {
                                this.startDate = courseW.startDate;
                            }
                            if (this.endDate == null || (courseW.endDate != null && this.endDate.isBefore(courseW.endDate))) {
                                this.endDate = courseW.endDate;
                            }

                        } else {
                            SingletonCourse courseS = new SingletonCourse(courseName, startDateTimePenn, endDateTimePenn);
                            course = courseS;
                            if (this.startDate == null || (courseS.interval.start != null && courseS.interval.start.toLocalDate().isBefore(this.startDate))) {
                                this.startDate = courseS.interval.start.toLocalDate();
                            }
                            if (this.endDate == null || (courseS.interval.end != null && courseS.interval.end.toLocalDate().isAfter(this.endDate))) {
                                this.endDate = courseS.interval.end.toLocalDate();
                            }
                        }
                        courses.add(course);
                        break;
                }
            }

            for (Course c : this.courses) {
                if (c instanceof WeeklyCourse wc) {
                    if (wc.endDate == null) {
                        wc.endDate = this.endDate;
                    }
                }
                c.assertValid();
            }
        } catch (Exception e) {
            System.out.println("An error occurred while parsing your schedule. Ensure that it is formatted correctly");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }

        if (this.startDate == null || this.endDate == null || this.courses == null || this.courses.isEmpty()) {
            System.out.println("No courses were found when parsing your schedule. Ensure that it is formatted correctly");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
    }

    /**
     * A record representing a single course meeting
     *
     * @param courseName   The name of the course
     * @param meetingTime  The interval of time of the course meeting
     */
    public record CourseMeeting(String courseName, Interval meetingTime) {}

    /**
     * Get a list of course meeting times on the specified day
     *
     * @param date  The date to check for meeting times on
     * @return      A (possibly empty) list of meeting times
     */
    public List<CourseMeeting> meetingsOnDate(LocalDate date) {
        List<CourseMeeting> rtn = new ArrayList<>();

        for (var c : this.courses) {
            Interval meetingTime = c.meetingOnDate(date);
            if (meetingTime != null) {
                rtn.add(new CourseMeeting(c.name(), meetingTime));
            }
        }

        return rtn;
    }

    /**
     * Get a list of course meeting times that overlap with the specified interval
     *
     * @param interval  The interval to check for meeting times during
     * @return          A (possibly empty) list of meeting times
     */
    public List<CourseMeeting> meetingsInInterval(Interval interval) {
        List<CourseMeeting> rtn = new ArrayList<>();

        interval = interval.canonical();
        LocalDate startDate = interval.start.toLocalDate();
        LocalDate endDate = interval.end.toLocalDate();

        List<LocalDate> dates = Utils.datesUntilInclusive(startDate, endDate).toList();

        for (var d : dates) {
            var onDate = meetingsOnDate(d);
            for (var m : onDate) {
                if (interval.overlaps(m.meetingTime)) {
                    rtn.add(m);
                }
            }
        }

        return rtn;
    }

    /**
     * Parse a DTSTART or DTEND line from an ics file.
     *
     * @param dtString          The entire DTSTART or DTEND line.
     * @return                  A ZonedDateTime parsed from the given string.
     * @throws IllegalArgumentException  If the string cannot be parsed
     */
    private static ZonedDateTime parseDTString(String dtString) throws IllegalArgumentException {
        return parseDTString(dtString, false);
    }

    /**
     * Parse a DTSTART or DTEND line from an ics file.
     *
     * @param dtString          The entire DTSTART or DTEND line.
     * @param pennLabsOverride  Set to true to replace the "Z" offset with the default
     *                          "America/New_York" offset. This is a workaround for a
     *                          bug in PennCourseReview's ics implementation; see
     *                          <a href="https://github.com/pennlabs/penn-courses/issues/489">#489</a>
     *
     * @return A ZonedDateTime parsed from the given string.
     * @throws IllegalArgumentException  If the string cannot be parsed
     */
    private static ZonedDateTime parseDTString(String dtString, boolean pennLabsOverride) throws IllegalArgumentException {
        String[] colonSplit = dtString.split(":");
        if (colonSplit.length != 2) {
            throw new IllegalArgumentException("Invalid string split: '" + dtString + "' split into " + Arrays.toString(colonSplit));
        }
        String dateString = colonSplit[1];
        String[] semicolonSplit = colonSplit[0].split(";");

        boolean dateMode = false;
        String timeZoneString = null;
        for (int i = 1; i < semicolonSplit.length; i++) {
            if (semicolonSplit[i].startsWith("TZID=")) {
                if (timeZoneString != null) {
                    throw new IllegalArgumentException("Multiple time zones specified: '" + dtString + "'");
                }
                timeZoneString = semicolonSplit[i].substring(5);
            } else if (semicolonSplit[i].equals("VALUE=DATE")) {
                dateMode = true;
            }
        }

        if (dateString.endsWith("Z")) {
            if (timeZoneString != null) {
                throw new IllegalArgumentException("Multiple time zones specified (TZID and Z)");
            }
            timeZoneString = pennLabsOverride ? Utils.PENN_TIMEZONE : "Z";
            dateString = dateString.substring(0, dateString.length() - 1);
        }

        if (timeZoneString == null) {
            timeZoneString = Utils.PENN_TIMEZONE;
        }

        ZoneId timeZone = ZoneId.of(timeZoneString);

        if (!dateMode) {
            LocalDateTime date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                    .parse(dateString)
                    .query(LocalDateTime::from);

            return ZonedDateTime.of(date, timeZone);
        } else {
            LocalDate date = DateTimeFormatter.ofPattern("yyyyMMdd")
                    .parse(dateString)
                    .query(LocalDate::from);

            boolean isEnd = semicolonSplit[0].equals("DTEND");
            if (isEnd) {
                return date.atTime(LocalTime.MAX).atZone(timeZone);
            } else {
                return date.atStartOfDay(timeZone);
            }
        }


    }

    /**
     * Parse a string representing a day of the week to a java.time.DayOfWeek object
     *
     * @param s  The abbreviated day of the week, as used in a .ics file
     * @return   A DayOfWeek object representing that day of the week.
     */
    private static DayOfWeek parseDayOfWeek(String s) {
        return switch (s) {
            case "SU" -> DayOfWeek.SUNDAY;
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.valueOf(s);
        };
    }

    /**
     * A class representing a course. Courses usually meet weekly (see {@link WeeklyCourse}), but
     * in some cases they may have only a single meeting (see {@link SingletonCourse})
     */
    public interface Course {
        /**
         * Returns the name of the course
         *
         * @return  The name of the course
         */
        String name();

        /**
         * Returns the meeting time of this course on the specified date, or null if there is none
         *
         * @param date  The date to check
         * @return      The time that this course meets on that date, or null
         */
        Interval meetingOnDate(LocalDate date);

        /**
         * Check if a Course has all its essentially fields set
         *
         * @return  True if the Course is valid, false otherwis.
         */
        boolean isValid();

        /**
         * Throw an exception if this.isValid() returns false;
         */
        default void assertValid() {
            if (!isValid()) {
                throw new IllegalArgumentException("Course is not valid; some fields are not assigned: " + this);
            }
        }
    }

    /**
     * A class representing a course which meets only once
     */
    public static class SingletonCourse implements Course {
        /**
         * The name of the course
         */
        protected String name;
        /**
         * The interval of the course meeting
         */
        protected Interval interval;

        public SingletonCourse(String name, LocalDateTime startDateTime, LocalDateTime endDateTime) {
            this.name = name;
            this.interval = new Interval(startDateTime, endDateTime);
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public Interval meetingOnDate(LocalDate date) {
            if (!this.interval.start.toLocalDate().isAfter(date)
            && !this.interval.end.toLocalDate().isBefore(date)) {
                return this.interval;
            } else {
                return null;
            }
        }

        @Override
        public boolean isValid() {
            return this.name != null && this.interval != null
                    && this.interval.start != null && this.interval.end != null;
        }

        @Override
        public String toString() {
            return "SingletonCourse{" +
                    "name='" + name + '\'' +
                    ", interval=" + interval +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SingletonCourse that = (SingletonCourse) o;
            return Objects.equals(name, that.name) && Objects.equals(interval, that.interval);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, interval);
        }
    }


    /**
     * A class which represents a weekly recurring course
     */
    public static class WeeklyCourse implements Course {
        /**
         * The name of the course
         */
        protected String name;
        /**
         * The days of the week that the course meets
         */
        protected Set<DayOfWeek> days;
        /**
         * The start time of the course meeting
         */
        protected LocalTime startTime;
        /**
         * The end time of the course meeting
         */
        protected LocalTime endTime;
        /**
         * The earliest date this course can meet on
         */
        protected LocalDate startDate;
        /**
         * The latest date this course can meet on
         */
        protected LocalDate endDate;

        public WeeklyCourse(String name, Set<DayOfWeek> days, LocalTime startTime, LocalTime endTime, LocalDate startDate, LocalDate endDate) {
            this.name = name;
            this.days = new TreeSet<>(days);
            this.startTime = startTime;
            this.endTime = endTime;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public Interval meetingOnDate(LocalDate date) {
            if (date.isBefore(this.startDate) || date.isAfter(this.endDate)) {
                return null;
            }
            DayOfWeek weekday = date.getDayOfWeek();
            if (!days.contains(weekday)) {
                return null;
            }
            return new Interval(date.atTime(this.startTime).atZone(Utils.PENN_ZONEID), date.atTime(this.endTime).atZone(Utils.PENN_ZONEID));
        }

        @Override
        public boolean isValid() {
            return (name != null) && (days != null) && (startTime != null) && (endTime != null) && (startDate != null) && (endDate != null);
        }

        @Override
        public String toString() {
            return "WeeklyCourse{" +
                    "name='" + name + '\'' +
                    ", days=" + days +
                    ", startTime=" + startTime +
                    ", endTime=" + endTime +
                    ", startDate=" + startDate +
                    ", endDate=" + endDate +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WeeklyCourse course = (WeeklyCourse) o;
            return Objects.equals(name, course.name) && Objects.equals(days, course.days) && Objects.equals(startTime, course.startTime) && Objects.equals(endTime, course.endTime) && Objects.equals(startDate, course.startDate) && Objects.equals(endDate, course.endDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, days, startTime, endTime, startDate, endDate);
        }
    }

}
