package infra;

import domain.Job;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityLogger {
    // Files
    private static final File processHistory = new File("history" + File.separator + "cmd_history.log");

    public static void logInfoInHistory(Job j) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(processHistory, true))) {
            bw.write(j.toString());
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            System.out.println("Error al abrir el archivo de historial: " + e.getMessage());
        }
    }

    public static String createLogForCommand(ProcessBuilder pb) {
        LocalDateTime launchDate = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

        String fileOutName = "bg_out_"+ launchDate.format(formatter) +".log";
        String fileErrName = "bg_err_"+ launchDate.format(formatter) +".log";

        File fileOut = new File("logs" + File.separator + fileOutName);
        File fileErr = new File("logs" + File.separator + fileErrName);

        pb.redirectOutput(fileOut);
        pb.redirectError(fileErr);

        return "OUT=logs/" + fileOut.getName() + " ERR=logs/" + fileErr.getName();
    }
}
