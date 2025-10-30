package infra;

import domain.Job;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProcessRegistry {
    public static ArrayList<Job> processes = new ArrayList<>();

    public static void addJob(Job j) {
        processes.add(j);
        ActivityLogger.logInfoInHistory(j);
    }

    public static boolean removeJob(long pid) {
        return processes.removeIf(j -> j.getPID() == pid);
    }

    public static boolean findById(long pid){
        return processes.stream().anyMatch(j -> j.getPID() == pid);
    }

    public static String execJobs() {
        StringBuilder result = new StringBuilder();

        String header = String.format("%-20s%-20s%-20s%-20s\n", "PID", "COMANDO", "HORA LANZAMIENTO", "ESTADO");

        result.append(header);

        for (Job j : processes) {
            result.append(j.toString())
                    .append('\n');
        }
        return result.toString();
    }

    public static Optional<String> describeProcess(long pid) {
        return ProcessHandle.of(pid).map(ph -> {
            ProcessHandle.Info info = ph.info();

            String command = info.command().orElse("<unknown>");
            String[] args = info.arguments().orElse(new String[0]);
            String cmdline = args.length == 0 ? command : command + " " + String.join(" ", args);
            String user = info.user().orElse("<unknown>");
            String start = info.startInstant()
                    .map(i -> LocalDateTime.ofInstant(i, ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .orElse("<unknown>");
            String cpu = info.totalCpuDuration()
                    .map(Duration::toString)
                    .orElse("<unknown>");
            long pidVal = ph.pid();
            boolean alive = ph.isAlive();
            Optional<ProcessHandle> parent = ph.parent();
            List<Long> children = ph.children().map(ProcessHandle::pid).toList();

            return "PID: " + pidVal + "\n" +
                    "Alive: " + alive + "\n" +
                    "User: " + user + "\n" +
                    "Command: " + cmdline + "\n" +
                    "Start: " + start + "\n" +
                    "CPU total: " + cpu + "\n" +
                    "Parent PID: " + parent.map(ProcessHandle::pid).map(String::valueOf).orElse("<none>") + "\n" +
                    "Children PIDs: " + (children.isEmpty() ? "<none>" : children.toString()) + "\n";
        });
    }
}
