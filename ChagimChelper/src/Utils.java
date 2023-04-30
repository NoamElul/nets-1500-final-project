import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;

/**
 * A class containing various global utilities and constants.
 * This class cannot be constructed; it contains only static methods
 */
public class Utils {
    private Utils() {}

    /**
     * A string representing Penn's timezone ("America/New_York")
     */
    public static final String PENN_TIMEZONE = "America/New_York";
    /**
     * A ZoneID representing Penn's timezone ("America/New_York")
     */
    public static final ZoneId PENN_ZONEID = ZoneId.of(PENN_TIMEZONE);

    /**
     * Return a stream of all the dates between the start and end dates, *inclusive*
     *
     * @param startDate  The day to start at
     * @param endDate    The day to end at
     * @return           A lazily-evaluated stream of dates between the two dates
     */
    public static Stream<LocalDate> datesUntilInclusive(LocalDate startDate, LocalDate endDate) {
        return Stream.concat(startDate.datesUntil(endDate), Stream.of(endDate));
    }
}
