import java.time.*;
import java.util.Objects;

/**
 * A class representing an interval of time
 */
public class Interval {
    public ZonedDateTime start;
    public ZonedDateTime end;

    /**
     * Construct an interval from absolute start and end date-times.
     */
    public Interval(ZonedDateTime start, ZonedDateTime end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Construct an interval from local start and end date-times, which are assumed
     * to be in Penn's timezone.
     */
    public Interval(LocalDateTime start, LocalDateTime end) {
        this.start = start.atZone(Utils.PENN_ZONEID);
        this.end = end.atZone(Utils.PENN_ZONEID);
    }

    /**
     * Returns the interval expressed in Penn's timezone. This does not change the
     * instants in time represented by this Interval.
     *
     * @return  And interval representing the same instant in time, but in the America/New_York timezone.
     */
    public Interval canonical() {
        return new Interval(
            start.withZoneSameInstant(Utils.PENN_ZONEID),
            end.withZoneSameInstant(Utils.PENN_ZONEID)
        );
    }

    /**
     * Modify an interval to be expressed in Penn's timezone, while still representing the
     * same instants in time.
     */
    public void convertToCanonical() {
        this.start = start.withZoneSameInstant(Utils.PENN_ZONEID);
        this.end = end.withZoneSameInstant(Utils.PENN_ZONEID);
    }

    /**
     * Check if two intervals intersect.
     *
     * @param other  the other interval.
     * @return       true if the two intervals overlap, false otherwise
     */
    public boolean overlaps(Interval other) {
        return this.contains(other.start) || this.contains(other.end)
                || other.contains(this.start) || other.contains(this.end);
    }

    /**
     * Check if this interval contains the specified date-time.
     *
     * @param dt  The date-time to check
     * @return    true if this interval contains dt, false otherwise
     */
    public boolean contains(ZonedDateTime dt) {
        return this.start.isBefore(dt) && this.end.isAfter(dt);
    }

    /**
     * Return a java.time.Duration representing the length of this Interval.
     *
     * @return  A Duration object representing the length of this Interval.
     */
    public Duration duration() {
        return Duration.between(this.start, this.end);
    }

    @Override
    public String toString() {
        return "Interval{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interval interval = (Interval) o;
        return Objects.equals(start, interval.start) && Objects.equals(end, interval.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
