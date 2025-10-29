package controller;

import java.io.File;
import java.util.*;

import infra.Platform;
import infra.ProcessManager;
import infra.ProcessRegistry;
import util.NumberParsing;

public class CommandController {

    private static final File logsFile = new File("history\\mi_interprete_historial.log");
    private static int timeout = 5000;

    public static String handle(String command) {
        String[] args = command.split(" ");
        String commandName = args[0];
        if (args.length > 1) {
            args = Arrays.copyOfRange(args, 1, args.length);
        } else {
            args = null;
        }

        return switch (commandName) {
            case "ejecuta" -> execEjecuta(args);
            case "run" -> execRun(args);
            case "runbg" -> execRunBG(args);
            case "jobs" -> ProcessRegistry.execJobs();
            case "kill" -> ProcessManager.execKill(args);
            case "details" -> execDetails(args);
            case "getenv" -> execGetEnv();
            case "getDirectory" -> execGetDirectory();
            case "timeout" -> execTimeout(args);
            case "history" -> execHistory();
            case "pipe" -> execPipe(args);
            case "exit" -> execExit();
            default -> "Comando no reconocido";
        };
    }

    private static String execPipe(String[] args) {
        return "No implementado";
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
                        case "TIMEOUT" -> timeout = path != null && NumberParsing.tryParseToInt(path) ? Integer.parseInt(path) : 5000;
                    }
                }
                i+=2;
            } else {
                cmd.add(arg);
                i++;
            }
        }

        return ProcessManager.execCommandWithTimeout(cmd, timeout, fileIn, fileOut, fileErr);
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
                        timeout = path != null && NumberParsing.tryParseToInt(path) ? Integer.parseInt(path) : 5000;
                    }
                }
                i+=2;
            } else {
                cmd.add(arg);
                i++;
            }
        }

        return ProcessManager.execCommandWithTimeoutUsingGobbler(cmd, timeout);
    }

    public static String execRunBG(String[] command) {
        List<String> cmd = new ArrayList<>(Platform.wrapForShell());
        cmd.addAll(Arrays.asList(command));
        String commandExecuted = String.join(" ", command);

        return ProcessManager.runBackgroundCommand(cmd, commandExecuted);
    }

    public static String execDetails(String[] command) {
        if (command.length != 1 || !NumberParsing.tryParseToInt(command[0])) {
            return "Error: Solo se acepta un parámetro de tipo entero para el PID";
        }

        long pid = Long.parseLong(command[0]);
        return getProcessInfo(pid);
    }

    public static String getProcessInfo(long pid) {
        Optional<String> desc = ProcessManager.describeProcess(pid);
        return desc.orElseGet(() -> "No existe proceso con PID " + pid + ".");
    }

    public static String execGetEnv() {
        Map<String, String> env = System.getenv();
        env.keySet().stream()
                .sorted()
                .forEach(k -> System.out.println(k + "=" + env.get(k)));
        return "";
    }

    public static String execGetDirectory() {
        return "user.dir = " + System.getProperty("user.dir");
    }

    public static String execTimeout(String[] command) {
        if (command == null) {
            return String.valueOf(timeout);
        }

        if (command.length == 1 && NumberParsing.tryParseToInt(command[0])) {
            timeout = Integer.parseInt(command[0]);
            return String.valueOf(timeout);
        }
        return "Error: El comando timeout sólo acepta 1 parámetro de tipo entero";
    }

    public static String execHistory() {
        // TODO implementar historial de comandos
        return logsFile.getAbsolutePath();
    }

    public static String execExit() {
        System.out.println("Saliendo...");
        System.exit(0);
        return "EXIT";
    }
}