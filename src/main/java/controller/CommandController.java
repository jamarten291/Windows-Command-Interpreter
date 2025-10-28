package controller;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import domain.Job;
import infra.Platform;
import infra.ProcessRegistry;

public class CommandController {

    private static List<String> commandHistory = new ArrayList<>();
    private static int timeout = 5000;

    public static String handle(String command) {
        commandHistory.add(command); // Guardamos el comando en el historial
        String[] args = command.split(" ");
        String commandName = args[0];
        args = Arrays.copyOfRange(args, 1, args.length);

        return switch (commandName) {
            case "ejecuta" -> execEjecuta(args);
            case "run" -> execRun(args);
            case "runbg" -> execRunBG(args);
            case "jobs" -> execJobs();
//            case "kill" -> execKill(args);
//            case "details" -> execDetails(args);
//            case "getenv" -> execGetEnv(args);
//            case "setenv" -> execSetEnv(args);
//            case "setDirectory" -> execSetDirectory(args);
            case "timeout" -> execTimeout(args);
//            case "history" -> execHistory();
            case "exit" -> execExit();
            default -> "Comando no reconocido";
        };
    }

    private static boolean tryParseToInt(String number) {
        try {
            Integer.parseInt(number);
            return true;
        }  catch (NumberFormatException e) {
            return false;
        }
    }

    public static String execEjecuta(String[] command) {
        List<String> args = Arrays.asList(command);
        List<String> cmd = new ArrayList<>(Platform.wrapForShell());
        String fileIn = null, fileOut = null, fileErr = null;

        for (int i = 0; i < args.size(); ) {
            String arg = args.get(i);

            if (arg.equalsIgnoreCase("IN") ||
                    arg.equalsIgnoreCase("OUT") ||
                    arg.equalsIgnoreCase("ERR") ||
                    arg.equalsIgnoreCase("TIMEOUT")) {
                if (i < args.size() - 1) {
                    String path = args.get(i+1).equalsIgnoreCase("null") ? null : args.get(i+1);
                    switch (arg) {
                        case "IN" -> fileIn = path;
                        case "OUT" -> fileOut = path;
                        case "ERR" -> fileErr = path;
                        case "TIMEOUT" -> timeout = path != null && tryParseToInt(path) ? Integer.parseInt(path) : 5000;
                    }
                }
                i+=2;
            } else {
                cmd.add(arg);
                i++;
            }
        }

        if (cmd.isEmpty()) {
            return "Error: No se ha introducido ningún comando para ejecutar";
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);

        if (fileIn == null) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectInput(new File(fileIn));

        if (fileOut == null) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectOutput(new File(fileOut));

        if (fileErr == null) pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectError(new File(fileErr));

        return execCommandWithTimeout(pb);
    }

    public static String execRun(String[] command) {
        List<String> args = Arrays.asList(command);
        List<String> cmd = new ArrayList<>(Platform.wrapForShell());

        for (int i = 0; i < args.size(); ) {
            String arg = args.get(i);

            if (arg.equalsIgnoreCase("TIMEOUT")) {
                if (i < args.size() - 1) {
                    String path = args.get(i+1).equalsIgnoreCase("null") ? null : args.get(i+1);
                    if (arg.equals("TIMEOUT")) {
                        timeout = path != null && tryParseToInt(path) ? Integer.parseInt(path) : 5000;
                    }
                }
                i+=2;
            } else {
                cmd.add(arg);
                i++;
            }
        }

        if (cmd.isEmpty()) {
            return "Error: No se ha introducido ningún comando para ejecutar";
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.inheritIO();

        return execCommandWithTimeout(pb);
    }

    private static String execCommandWithTimeout(ProcessBuilder pb) {
        try {
            Process p = pb.start();
            boolean finalizado = p.waitFor(timeout, TimeUnit.MILLISECONDS);

            if (finalizado) {
                return "OK: Exit=" + p.exitValue() + "(timeout="+ timeout + ")";
            } else {
                p.destroy();
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
                return "TIMEOUT: Exit=" + p.exitValue() + "(timeout="+ timeout + ")";
            }
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
            return "ERROR: " + e.getMessage();
        }
    }

    public static String execRunBG(String[] command) {
        List<String> cmd = new ArrayList<>(Platform.wrapForShell());
        cmd.addAll(Arrays.asList(command));
        String commandExecuted = String.join(" ", command);

        if (cmd.isEmpty()) {
            return "Error: No se ha introducido ningún comando para ejecutar";
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);

        LocalDateTime launchDate = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

        File fileOut = new File("logs\\bg_out_"+ launchDate.format(formatter) +".log");
        File fileErr = new File("logs\\bg_err_"+ launchDate.format(formatter) +".log");

        pb.redirectOutput(fileOut);
        pb.redirectError(fileErr);

        try {
            Process process = pb.start();
            ProcessRegistry.addJob(
                    new Job(process.pid(),
                            launchDate,
                            commandExecuted
                    )
            );
            return "BG PID=" + process.pid() + " OUT=logs/"+fileOut.getName() + " ERR=logs/"+fileErr.getName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String execJobs() {
        StringBuilder result = new StringBuilder();

        String header = String.format("%-20s%-20s%-20s\n", "PID", "COMANDO", "HORA LANZAMIENTO");

        result.append(header);

        for (Job j : ProcessRegistry.processes) {
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

    public static void execKill(String[] command) {
        // Implementación de 'kill'
    }

    public static void execDetails(String[] command) {
        // Implementación de 'details'
    }

    public static void execGetEnv(String[] command) {
        // Implementación de 'getenv'
    }

    public static void execSetEnv(String[] command) {
        // Implementación de 'setenv'
    }

    public static void execSetDirectory(String[] command) {
        // Implementación de 'setDirectory'
    }

    public static String execTimeout(String[] command) {
        return String.valueOf(timeout);
    }

    public static void execHistory() {
        System.out.println("Historial de comandos:");
        for (String cmd : commandHistory) {
            System.out.println(cmd);
        }
    }

    public static String execExit() {
        System.out.println("Saliendo...");
        System.exit(0);
        return "EXIT";
    }
}