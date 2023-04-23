import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

public class Utils {
    private Utils() {}

    public static final String PENN_TIMEZONE = "America/New_York";
    public static final ZoneId PENN_ZONEID = ZoneId.of(PENN_TIMEZONE);

    public static String readFileToString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static ZonedDateTime instantToZoned(Instant i) {
        return ZonedDateTime.ofInstant(i, PENN_ZONEID);
    }

    public static Stream<LocalDate> datesUntilInclusive(LocalDate startDate, LocalDate endDate) {
        return Stream.concat(startDate.datesUntil(endDate), Stream.of(endDate));
    }
}
