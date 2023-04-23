import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Schedule {
    static final String PENN_TIMEZONE = "America/New_York";
    static final ZoneId PENN_ZONEID = ZoneId.of(PENN_TIMEZONE);

    public Schedule(String icsText) {
        String icsTextConcat = CachedRegex.pattern("\\R[ \\t]", Pattern.UNICODE_CHARACTER_CLASS).matcher(icsText).replaceAll("");
        String[] icsLines = CachedRegex.pattern("\\R", Pattern.UNICODE_CHARACTER_CLASS).split(icsTextConcat);

        boolean pennLabsMode = false;
        ArrayList<Course> courses = new ArrayList<>();

        int lineNum = -1;
        while (++lineNum < icsLines.length) {
            String line = icsLines[lineNum];
            if (line.equals("END:VCALENDAR")) {
                break;
            } else if (line.equals("PRODID:Penn Labs")) {
                pennLabsMode = true;
            } else if (line.equals("BEGIN:VEVENT")) {
                // Process VEVENT
                Course course = new Course();
                while (++lineNum < icsLines.length) {
                    line = icsLines[lineNum];
                    if (line.equals("END:VEVENT")) {
                        break;
                    } else if (line.startsWith("DTSTART:")) {
                        ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                        LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(PENN_ZONEID).toLocalDateTime();
                        course.startDate = pennDateTime.toLocalDate();
                        course.startTime = pennDateTime.toLocalTime();
                    } else if (line.startsWith("DTEND:")) {
                        ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                        LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(PENN_ZONEID).toLocalDateTime();
                        course.endTime = pennDateTime.toLocalTime();
                    } else if (line.startsWith("RRULE:")) {
                        String[] components = CachedRegex.pattern(":|;").split(line);
                        for (String comp : components) {
                            if (comp.startsWith("UNTIL=")) {
                                String untilString = comp.substring(6);
                                ZonedDateTime parsedDateTime = DateTimeFormatter.ofPattern("yyyyMMddTHHmmssX")
                                        .parse(untilString)
                                        .query(ZonedDateTime::from);
                                LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(PENN_ZONEID).toLocalDateTime();
                                course.endDate = pennDateTime.toLocalDate();
                            } else if (comp.startsWith("BYDAY=")) {
                                String allDaysString = comp.substring(6);
                                String[] dayStrings = CachedRegex.pattern(",").split(allDaysString);
                                var days = Arrays.stream(dayStrings).map(Schedule::parseDayOfWeek).collect(Collectors.toSet());
                                course.days = days;
                            }
                        }
                    }
                }
                courses.add(course);
            }
        }
    }

    public static class Course {
        String name;
        Set<DayOfWeek> days;
        LocalTime startTime;
        LocalTime endTime;
        LocalDate startDate;
        LocalDate endDate;

        public Course() {
            // Create uninitialized Course
        }

        Course(String name, Set<DayOfWeek> days, LocalTime startTime, LocalTime endTime, LocalDate startDate, LocalDate endDate) {
            this.name = name;
            this.days = new TreeSet<>(days);
            this.startTime = startTime;
            this.endTime = endTime;
            this.startDate = startDate;
            this.endDate = endDate;
        }

//        Course(String name, Set<DayOfWeek> days, LocalDateTime startDateTime, LocalDateTime endDateTime) {
//            this.name = name;
//            this.days = new TreeSet<>(days);
//            this.assignStartDateTime(startDateTime);
//            this.assignEndDateTime(endDateTime);
//        }
//
//        Course(String name, Set<DayOfWeek> days, ZonedDateTime startDateTime, ZonedDateTime endDateTime) {
//            this.name = name;
//            this.days = new TreeSet<>(days);
//            this.assignStartDateTime(startDateTime);
//            this.assignEndDateTime(endDateTime);
//        }
//
//        public void assignStartDateTime(LocalDateTime startDateTime) {
//            this.startTime = startDateTime.toLocalTime();
//            this.startDate = startDateTime.toLocalDate();
//        }
//
//        public void assignStartDateTime(ZonedDateTime startDateTime) {
//            assignStartDateTime(startDateTime.withZoneSameInstant(PENN_ZONEID).toLocalDateTime());
//        }
//
//        public void assignEndDateTime(LocalDateTime endDateTime) {
//            this.endTime = endDateTime.toLocalTime();
//            this.endDate = endDateTime.toLocalDate();
//        }
//
//        public void assignEndDateTime(ZonedDateTime endDateTime) {
//            assignEndDateTime(endDateTime.withZoneSameInstant(PENN_ZONEID).toLocalDateTime());
//        }

    }

    /**
     * Parse a DTSTART or DTEND line from an ics file.
     *
     * @param dtString          The entire DTSTART or DTEND line.
     *
     * @return                  A ZonedDateTime parsed from the given string.
     * @throws IllegalArgumentException
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
     *                          https://github.com/pennlabs/penn-courses/issues/489
     *
     * @return                  A ZonedDateTime parsed from the given string.
     * @throws IllegalArgumentException
     */
    private static ZonedDateTime parseDTString(String dtString, boolean pennLabsOverride) throws IllegalArgumentException {
        Pattern p = CachedRegex.pattern("[^:;]+(?:;TZID=([^:;]*))?:([0-9A-Z]+)");
        Matcher m = p.matcher(dtString);
        if (!m.matches()) {
            throw new IllegalArgumentException("String could not be matched");
        }
        String dateString = m.group(2);
        String timeZoneString = m.group(1);
        if (timeZoneString != null && timeZoneString.isEmpty()) {
            timeZoneString = null;
        }

        if (dateString.endsWith("Z")) {
            if (timeZoneString != null) {
                throw new IllegalArgumentException("Multiple time zones specified (TZID and Z)");
            }
            timeZoneString = pennLabsOverride ? PENN_TIMEZONE : "Z";
            dateString = dateString.substring(0, dateString.length() - 1);
        }

        if (timeZoneString == null) {
            timeZoneString = PENN_TIMEZONE;
        }

        ZoneId timeZone = ZoneId.of(timeZoneString);
        LocalDateTime date = DateTimeFormatter.ofPattern("yyyyMMddTHHmmss")
                .parse(dateString)
                .query(LocalDateTime::from);

        return ZonedDateTime.of(date, timeZone);
    }

    private static DayOfWeek parseDayOfWeek(String s) {
        switch (s) {
            case "SU":
                return DayOfWeek.SUNDAY;
            case "MO":
                return DayOfWeek.MONDAY;
            case "TU":
                return DayOfWeek.TUESDAY;
            case "WE":
                return DayOfWeek.WEDNESDAY;
            case "TH":
                return DayOfWeek.THURSDAY;
            case "FI":
                return DayOfWeek.FRIDAY;
            case "SA":
                return DayOfWeek.SATURDAY;
            default:
                return DayOfWeek.valueOf(s);
        }
    }

}
