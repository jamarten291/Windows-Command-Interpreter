package util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class StreamGobbler implements Callable<Void> {
    private final InputStream input;
    private final PrintStream target;
    private volatile boolean running = true;

    public StreamGobbler(InputStream input, PrintStream target) {
        this.input = input;
        this.target = target;
    }

    public void stop() { running = false; try { input.close(); } catch (IOException ignored) {} }

    @Override
    public Void call() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = br.readLine()) != null) {
                target.println(line);
            }
        } catch (IOException ignored) {
            // ocurre al cerrar el stream tras destroyForcibly
        }
        return null;
    }
}

