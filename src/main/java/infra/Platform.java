package infra;

import java.util.ArrayList;
import java.util.List;

public class Platform {
    public static List<String> wrapForShell() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return new ArrayList<>(List.of("cmd.exe", "/c"));
        }
        else {
            return List.of("bash", "-lc");
        }
    }
}
