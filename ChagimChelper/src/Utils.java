import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {
    private Utils() {}

    static String readFileToString(Path path) throws IOException {
        return new String(Files.readAllBytes(Paths.get("file")), StandardCharsets.UTF_8);
    }
}
