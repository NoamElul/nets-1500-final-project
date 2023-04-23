import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Schedule {
    List<Course> courses;
    LocalDate startDate;
    LocalDate endDate;

    public Schedule(Path filePath) throws IOException {
        this(Utils.readFileToString(filePath));
    }

    public Schedule(String icsText) {
        String icsTextConcat = CachedRegex.pattern("\\R[ \\t]", Pattern.UNICODE_CHARACTER_CLASS).matcher(icsText).replaceAll("");
        String[] icsLines = CachedRegex.pattern("\\R", Pattern.UNICODE_CHARACTER_CLASS).split(icsTextConcat);

        boolean pennLabsMode = false;
        this.courses = new ArrayList<>();

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
                    } else if (line.startsWith("SUMMARY:")) {
                        course.name = line.substring(8);
                    }
                    else if (line.startsWith("DTSTART")) {
                        ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                        LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                        course.startDate = pennDateTime.toLocalDate();
                        course.startTime = pennDateTime.toLocalTime();
                    } else if (line.startsWith("DTEND")) {
                        ZonedDateTime parsedDateTime = parseDTString(line, pennLabsMode);
                        LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                        course.endTime = pennDateTime.toLocalTime();
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
                                    course.endDate = parsedDateTime.toLocalDate();
                                } else {
                                    LocalDateTime pennDateTime = parsedDateTime.withZoneSameInstant(Utils.PENN_ZONEID).toLocalDateTime();
                                    course.endDate = pennDateTime.toLocalDate();
                                }

                            } else if (comp.startsWith("BYDAY=")) {
                                String allDaysString = comp.substring(6);
                                String[] dayStrings = CachedRegex.pattern(",").split(allDaysString);
                                course.days = Arrays.stream(dayStrings)
                                        .map(Schedule::parseDayOfWeek)
                                        .collect(Collectors.toSet());
                            }
                        }
                    }
                }
                course.assertValid();
                if (this.startDate == null || course.startDate.isBefore(this.startDate)) {
                    this.startDate = course.startDate;
                }
                if (this.endDate == null || course.endDate.isAfter(this.endDate)) {
                    this.endDate = course.endDate;
                }
                courses.add(course);
            }
        }
    }

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
                rtn.add(new CourseMeeting(c.name, meetingTime));
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
            timeZoneString = pennLabsOverride ? Utils.PENN_TIMEZONE : "Z";
            dateString = dateString.substring(0, dateString.length() - 1);
        }

        if (timeZoneString == null) {
            timeZoneString = Utils.PENN_TIMEZONE;
        }

        ZoneId timeZone = ZoneId.of(timeZoneString);
        LocalDateTime date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                .parse(dateString)
                .query(LocalDateTime::from);

        return ZonedDateTime.of(date, timeZone);
    }

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


    public static class Course {
        public String name;
        public Set<DayOfWeek> days;
        public LocalTime startTime;
        public LocalTime endTime;
        public LocalDate startDate;
        public LocalDate endDate;

        public Course() {
            // Create uninitialized Course
        }

        public Course(String name, Set<DayOfWeek> days, LocalTime startTime, LocalTime endTime, LocalDate startDate, LocalDate endDate) {
            this.name = name;
            this.days = new TreeSet<>(days);
            this.startTime = startTime;
            this.endTime = endTime;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        /**
         * Returns the meeting time of this course on the specified date, or null if there is none
         *
         * @param date  The date to check
         * @return      The time that this course meets on that date, or null
         */
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

        public boolean isValid() {
            return (name != null) && (days != null) && (startTime != null) && (endTime != null) && (startDate != null) && (endDate != null);
        }

        public void assertValid() {
            if (!isValid()) {
                throw new IllegalArgumentException("Course is not valid; some fields are not assigned: " + this);
            }
        }

        @Override
        public String toString() {
            return "Course{" +
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
            Course course = (Course) o;
            return Objects.equals(name, course.name) && Objects.equals(days, course.days) && Objects.equals(startTime, course.startTime) && Objects.equals(endTime, course.endTime) && Objects.equals(startDate, course.startDate) && Objects.equals(endDate, course.endDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, days, startTime, endTime, startDate, endDate);
        }
    }

}
