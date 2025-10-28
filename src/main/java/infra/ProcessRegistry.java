package infra;

import domain.Job;

import java.util.ArrayList;

public class ProcessRegistry {
    public static ArrayList<Job> processes = new ArrayList<Job>();

    public static void addJob(Job j) {
        processes.add(j);
    }

    public static void showJobs() {

    }
}
