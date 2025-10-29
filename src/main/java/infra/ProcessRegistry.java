package infra;

import domain.Job;

import java.util.ArrayList;

public class ProcessRegistry {
    public static ArrayList<Job> processes = new ArrayList<>();

    public static void addJob(Job j) {
        processes.add(j);
    }

    public static boolean removeJob(long pid) {
        return processes.removeIf(j -> j.getPID() == pid);
    }

    public static boolean findById(long pid){
        return processes.stream().anyMatch(j -> j.getPID() == pid);
    }

    public static String execJobs() {
        StringBuilder result = new StringBuilder();

        String header = String.format("%-20s%-20s%-20s\n", "PID", "COMANDO", "HORA LANZAMIENTO");

        result.append(header);

        for (Job j : processes) {
            String formattedInfo = String.format("%-20d%-20s%20d:%d:%d\n",
                    j.getPID(),
                    j.getCmd(),
                    j.getInicio().getHour(),
                    j.getInicio().getMinute(),
                    j.getInicio().getSecond()
            );

            result.append(formattedInfo);
        }
        return result.toString();
    }
}
