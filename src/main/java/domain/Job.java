package domain;

import infra.ProcessRegistry;

import java.time.LocalDateTime;

public class Job {
    private final long PID;
    private final LocalDateTime inicio;
    private final String cmd;
    private boolean estado;

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

    public boolean getEstado() {
        return ProcessHandle.of(this.PID).isPresent();
    }

    @Override
    public String toString() {
        return "Job{" +
                "PID=" + PID +
                ", inicio=" + inicio +
                ", cmd='" + cmd + '\'' +
                ", estado=" + estado +
                '}';
    }
}
