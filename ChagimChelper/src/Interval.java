import java.time.*;
import java.util.Objects;

public class Interval {
    public ZonedDateTime start;
    public ZonedDateTime end;

    public Interval(ZonedDateTime start, ZonedDateTime end) {
        this.start = start;
        this.end = end;
    }

    public Interval(LocalDateTime start, LocalDateTime end) {
        this.start = start.atZone(Utils.PENN_ZONEID);
        this.end = end.atZone(Utils.PENN_ZONEID);
    }

    public Interval(Instant start, Instant end) {
        this.start = Utils.instantToZoned(start);
        this.end = Utils.instantToZoned(end);
    }

    /**
     * Returns the interval expressed in Penn's timezone
     *
     * @return  And interval representing the same instant in time, but in the America/New_York timezone
     */
    public Interval canonical() {
        return new Interval(
            start.withZoneSameInstant(Utils.PENN_ZONEID),
            end.withZoneSameInstant(Utils.PENN_ZONEID)
        );
    }

    public void convertToCanonical() {
        this.start = start.withZoneSameInstant(Utils.PENN_ZONEID);
        this.end = end.withZoneSameInstant(Utils.PENN_ZONEID);
    }

    public boolean overlaps(Interval other) {
        return this.contains(other.start) || this.contains(other.end)
                || other.contains(this.start) || other.contains(this.end);
    }

    public boolean contains(ZonedDateTime dt) {
        return this.start.isBefore(dt) && this.end.isAfter(dt);
    }

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
