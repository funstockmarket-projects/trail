import java.time.DayOfWeek;
import java.time.LocalDate;

public class Utils {
    public static boolean isWeekend() {
        DayOfWeek d = LocalDate.now().getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }
}