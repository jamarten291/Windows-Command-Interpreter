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
}
