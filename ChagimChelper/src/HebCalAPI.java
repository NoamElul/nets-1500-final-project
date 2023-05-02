import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class representing the API of hebcal.com, which is used to retrieve holiday information
 * This class cannot be constructed; it contains only static methods
 */
public class HebCalAPI {
    /**
     * This class cannot be constructed; it contains only static methods
     */
    private HebCalAPI() {}

    /**
     * Get all the holidays between the given start and end dates
     *
     * @param startDate  The starting date, assumed to be in Penn's timezone
     * @param endDate    The ending date, assumed to be in Penn's timezone
     * @return           A list of holidays in the give time period
     */
    public static List<HolidayInterval> getHolidays(LocalDate startDate, LocalDate endDate) {
        String url = getURL(startDate, endDate);
        String response = getResponse(url);
        List<HebCalAPI.HolidayInterval> parsedReponse;
        try {
            var splitResponse = splitResponse(response);
            parsedReponse = parseList(splitResponse);
        } catch (Exception e) {
            System.out.println("An error occurred while parsing the holiday API response");
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
        return parsedReponse;
    }

    /**
     * Generate the url used to request data from the api
     *
     * @param startDate  The starting date, assumed to be in Penn's timezone
     * @param endDate    The ending date, assumed to be in Penn's timezone
     * @return           The url, as a string
     */
    private static String getURL(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return "https://www.hebcal.com/hebcal?cfg=json&v=1&maj=on&leyning=off&c=on&geo=zip&zip=19104"
                + "&start=" + startDate.format(formatter)
                + "&end=" + endDate.format(formatter);
    }

    /**
     * Request data from the api, and return the body as a string
     *
     * @param url  The api url to request from, as a string
     * @return     The response body, as a string
     */
    private static String getResponse(String url) {
        URI requestUri = URI.create(url);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(requestUri).build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while making a request to the holiday api url " + requestUri);
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
        if (response.statusCode() != 200) {
            System.out.println("The request to the holiday API url '" + requestUri + "' returned unsuccessful status code " + response.statusCode());
            System.exit(1);
            throw new RuntimeException("System.exit() did not exit");
        }
        return response.body();
    }

    /**
     * Splits the JSON response into a list of relevant objects (sets of key-value pairs)
     *
     * @param response  The response body as text
     * @return          The response, split into a list of objects
     */
    private static ArrayList<Map<String, String>> splitResponse(String response) {
        Pattern itemsPat = CachedRegex.pattern("\"items\"\\s*:\\s*\\[([^\\[\\]]+)\\]");
        Matcher m1 = itemsPat.matcher(response);
        if (!m1.find()) {
            throw new IllegalArgumentException("Could not match pattern to response: " + response);
        }
        String itemString = m1.group(1);
        Pattern objectSeparator = CachedRegex.pattern("(?<=\\})\\s*,\\s*(?=\\{)");
        String[] items = objectSeparator.split(itemString);

        ArrayList<Map<String, String>> rtn = new ArrayList<>(items.length);
        for (var obj : items) {
            Pattern objectPattern = CachedRegex.pattern("\\s*\\{([^{}]*)\\}\\s*");
            Matcher m2 = objectPattern.matcher(obj);
            if (!m2.matches()) {
                throw new IllegalArgumentException("Could not match pattern to object: " + obj);
            }
            String objString = m2.group(1);

            Pattern stringSeparator = CachedRegex.pattern("((?<=\")\\s*,\\s*(?=\"))|(\\s*,\\s*(?=\"))|((?<=\")\\s*,\\s*)");
            String[] pairs = stringSeparator.split(objString);

            Map<String, String> map = new HashMap<>(pairs.length);
            for (var p: pairs) {
                Pattern pairPattern = CachedRegex.pattern("\\s*\"([^\"]*)\"\\s*:\\s*\"?([^\"]*)\"?\\s*");
                Matcher m3 = pairPattern.matcher(p);
                if (!m3.matches()) {
                    throw new IllegalArgumentException("Could not match pattern to kv pair: " + p);
                }

                String key = m3.group(1);
                String value = m3.group(2);
                map.put(key, value);
            }
            rtn.add(map);
        }

        return rtn;
    }

    /**
     * Parse the split response into a list of HolidayInterval objects
     *
     * @param rawList  The list of objects returned by {@link HebCalAPI#splitResponse(String response)}
     * @return         A list of HolidayInterval objects
     */
    private static ArrayList<HolidayInterval> parseList(ArrayList<Map<String, String>> rawList) {
        ArrayList<HolidayInterval> rtn = new ArrayList<>(5 + rawList.size() / 3);

        int i = -1;
        while (++i < rawList.size()) {
            var curr = rawList.get(i);

            if ("Candle lighting".equals(curr.get("title_orig"))) {
                String startDateString = curr.get("date");
                ZonedDateTime startDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .parse(startDateString).query(OffsetDateTime::from)
                        .atZoneSameInstant(Utils.PENN_ZONEID);

                ArrayList<String> eventNames = new ArrayList<>();
                ZonedDateTime endDateTime = null;
                while (++i < rawList.size()) {
                    curr = rawList.get(i);

                    if ("true".equals(curr.get("yomtov"))) {
                        String name = curr.get("title");
                        if (name != null) {
                            eventNames.add(name);
                        }
                    } else if ("Havdalah".equals(curr.get("title_orig"))) {
                        String endDateString = curr.get("date");
                        endDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                                .parse(endDateString).query(OffsetDateTime::from)
                                .atZoneSameInstant(Utils.PENN_ZONEID);
                        break;
                    }
                }

                HolidayInterval interval = new HolidayInterval(eventNames, startDateTime, endDateTime);
                rtn.add(interval);
            }
        }

        return rtn;
    }

    /**
     * A class representing a holiday, and the interval of time it takes place in
     */
    public static class HolidayInterval {
        public String eventName;
        public Interval interval;

        HolidayInterval(List<String> eventNames, ZonedDateTime startDateTime, ZonedDateTime endDateTime) {
            if (endDateTime == null) {
                // This can only happen if havdalah is past the end date we requested.
                // In that case, the end date doesn't really matter, so we set it to the max possible
                endDateTime = startDateTime.plusHours(1 + 3 * 24);
            }
            this.interval = new Interval(startDateTime, endDateTime);

            if (eventNames.isEmpty()) {
                if (containsShabbat(this.interval)) {
                    eventNames.add("Shabbat");
                } else {
                    eventNames.add("Yom Tov");
                }
            }

            var eventNamesFiltered = eventNames.stream()
                    .map(HolidayInterval::cleanName)
                    .distinct()
                    .toArray(String[]::new);
            this.eventName = String.join("/", eventNamesFiltered);
        }

        /**
         * Return a cleaned version of the name of a holiday.
         *
         * @param name  The raw name of the holiday
         * @return      A cleaned version of the name
         */
        private static String cleanName(String name) {
            {
                Pattern romanNumeral = CachedRegex.pattern("(.*?)[ ]+[IVX]+", Pattern.CASE_INSENSITIVE);
                Matcher m = romanNumeral.matcher(name);
                if (m.matches()) {
                    return m.group(1);
                }
            }

            {
                Pattern roshHashana = CachedRegex.pattern("(rosh hashana)[ ]+\\d{4}", Pattern.CASE_INSENSITIVE);
                Matcher m = roshHashana.matcher(name);
                if (m.matches()) {
                    return m.group(1);
                }
            }

            return name;
        }

        /**
         * Checks if a given interval contains the Sabbath
         *
         * @param interval  The interval to check
         * @return          True if the interval contains shabbat, false if otherwise
         */
        private static boolean containsShabbat(Interval interval) {
            if (interval.duration().toDays() >= 7) {
                return true;
            } else if (interval.start.getDayOfWeek().getValue() > interval.end.getDayOfWeek().getValue()) {
                return (interval.start.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue());
            } else {
                return (interval.start.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue())
                        && (interval.end.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue());
            }
        }

        @Override
        public String toString() {
            return "HolidayInterval{" +
                    "eventName='" + eventName + '\'' +
                    ", interval=" + interval +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HolidayInterval that = (HolidayInterval) o;
            return Objects.equals(eventName, that.eventName) && Objects.equals(interval, that.interval);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventName, interval);
        }
    }
}
