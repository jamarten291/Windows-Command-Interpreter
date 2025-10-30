package infra;

import domain.Job;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ActivityLogger {
    private static File processHistory = new File("history/cmd_history.txt");

    public static void logInfoInHistory(Job j) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(processHistory, true))) {
            bw.write(j.toString());
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            System.out.println("Error al abrir el archivo de historial: " + e.getMessage());
        }
    }
}
