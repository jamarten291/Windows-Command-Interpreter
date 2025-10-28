package controller;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
        if (args.length > 1) {
            args = Arrays.copyOfRange(args, 1, args.length);
        } else {
            args = null;
        }

        return switch (commandName) {
            case "ejecuta" -> execEjecuta(args);
            case "run" -> execRun(args);
            case "runbg" -> execRunBG(args);
            case "jobs" -> execJobs();
            case "kill" -> execKill(args);
            case "details" -> execDetails(args);
            case "getenv" -> execGetEnv();
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

    public static String execKill(String[] command) {
        if (command == null || command.length != 1 || !tryParseToInt(command[0])) {
            return "Error: El parámetro debe ser un PID";
        }

        if (!ProcessRegistry.findById(Long.parseLong(command[0]))) {
            return "Error: El proceso con PID " + command[0] + " no ha sido lanzado por el programa.";
        }

        long pid = Long.parseLong(command[0]);

        // Comprobar coincidencia temporal para mitigar reutilización de PID
        ProcessHandle.of(pid).ifPresentOrElse(ph -> {
            Optional<Instant> startInstant = ph.info().startInstant();
            if (startInstant.isPresent()) {
                // Si coincide, proceder a destruir
                boolean destroyed = ph.destroy();
                if (!destroyed) {
                    // Intentar forzar si no se pudo con destroy()
                    ph.destroyForcibly();
                }
            }
        }, () -> {
            // El proceso no existe en el sistema: limpiamos el registro y señalamos error
            ProcessRegistry.removeJob(pid);
        });

        // Si llegamos aquí, la operación fue iniciada
        return "El proceso con PID " + pid + " ha sido destruido exitosamente";

    }

    public static String execDetails(String[] command) {
        if (command.length != 1 || !tryParseToInt(command[0])) {
            return "Error: Solo se acepta un parámetro de tipo entero para el PID";
        }

        long pid = Long.parseLong(command[0]);
        return getProcessInfo(pid);
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
            ;
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

    public static String getProcessInfo(long pid) {
        Optional<String> desc = describeProcess(pid);
        return desc.orElseGet(() -> "No existe proceso con PID " + pid + ".");
    }



    public static String execGetEnv() {
        Map<String, String> env = System.getenv();
        env.keySet().stream()
                .sorted()
                .forEach(k -> System.out.println(k + "=" + env.get(k)));
        return "";
    }

    public static void execSetEnv(String[] command) {
        // Implementación de 'setenv'
    }

    public static void execSetDirectory(String[] command) {
        // Implementación de 'setDirectory'
    }

    public static String execTimeout(String[] command) {
        if (command == null) {
            return String.valueOf(timeout);
        }

        if (command.length == 1 && tryParseToInt(command[0])) {
            timeout = Integer.parseInt(command[0]);
            return String.valueOf(timeout);
        }
        return "Error: El comando timeout sólo acepta 1 parámetro de tipo entero";
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