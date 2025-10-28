package domain;

import java.time.LocalDateTime;

public class Job {
    private final long PID;
    private final LocalDateTime inicio;
    private final String cmd;

    public Job(long PID, LocalDateTime inicio, String cmd) {
        this.PID = PID;
        this.inicio = inicio;
        this.cmd = cmd;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public long getPID() {
        return PID;
    }

    public String getCmd() {
        return cmd;
    }
}
