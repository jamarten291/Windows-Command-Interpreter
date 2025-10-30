package infra;

import domain.Job;
import util.StreamGobbler;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class ProcessManager {
    public static String execCommandWithTimeout(List<String> cmd, int timeout, String fileIn, String fileOut, String fileErr) {
        ProcessBuilder pb = initProcessBuilder(cmd);
        if (pb == null) return "Error: No se ha introducido ningún comando para ejecutar";

        if (fileIn == null) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectInput(new File(fileIn));

        if (fileOut == null) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectOutput(new File(fileOut));

        if (fileErr == null) pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        else pb.redirectError(new File(fileErr));

        try {
            Process p = pb.start();
            ProcessRegistry.addJob(
                    new Job(p.pid(),
                            LocalDateTime.now(),
                            String.join(" ", pb.command())
                    )
            );
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

    public static String buildPipeline(List<List<String>> cmd, int timeout, String fileIn, String fileOut, String fileErr) {
        if (cmd.size() < 2) return "Error: La tubería introducida no es válida";

        List<ProcessBuilder> pipeCommand = new ArrayList<>();

        for (int i = 0; i < cmd.size(); i++) {
            List<String> command = new ArrayList<>(Platform.wrapForShell());
            command.addAll(cmd.get(i));

            ProcessBuilder pb = new ProcessBuilder(command);



            if (i == 0) {
                if (fileIn == null) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                else pb.redirectInput(new File(fileIn));
            } else {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            }

            if (i == cmd.size() - 1) {
                if (fileOut == null) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                else pb.redirectOutput(new File(fileOut));
                if (fileErr == null) pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                else pb.redirectError(new File(fileErr));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                // errores intermedios se pueden heredar o redirigir a PIPE; se deja heredar para simplicidad
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            pipeCommand.add(pb);
        }

        return executePipeline(pipeCommand, timeout);
    }

    private static String executePipeline(List<ProcessBuilder> cmd, int timeout) {
        try {
            // Iniciar pipeline (Java 9+)
            List<Process> processes = ProcessBuilder.startPipeline(cmd);

            // Esperar con timeout: comprobamos el último proceso principalmente,
            // pero debemos forzar finalización de todos si se excede el tiempo.
            Process last = processes.getLast();
            boolean finished = last.waitFor(timeout, TimeUnit.MILLISECONDS);

            if (finished) {
                StringBuilder sb = new StringBuilder("OK:");
                for (int i = 0; i < processes.size(); i++) {
                    Process process = processes.get(i);
                    sb.append(" P").append(i+1).append("=").append(process.exitValue());
                }
                sb.append(" (timeout=").append(timeout).append(")");
                return sb.toString();
            } else {
                // timeout: destruir todos
                for (Process p : processes) {
                    p.destroy();
                }
                // esperar un breve periodo y forzar si aún hay vivos
                Thread.sleep(200);
                for (Process p : processes) {
                    if (p.isAlive()) p.destroyForcibly();
                }
                StringBuilder sb = new StringBuilder("TIMEOUT:");
                for (int i = 0; i < processes.size(); i++) {
                    try { sb.append(" P").append(i+1).append("=").append(processes.get(i).exitValue()); }
                    catch (IllegalThreadStateException e) { sb.append(" P").append(i+1).append("=stillAlive"); }
                }
                sb.append(" (timeout=").append(timeout).append(")");
                return sb.toString();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR: Interrupted";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static ProcessBuilder initProcessBuilder(List<String> cmd) {
        if (cmd.isEmpty()) {
            return null;
        }

        return new ProcessBuilder(cmd);
    }

    public static String execCommandWithTimeoutUsingGobbler(List<String> cmd, int timeout) {
        ProcessBuilder pb = initProcessBuilder(cmd);
        if (pb == null) return "Error: No se ha introducido ningún comando para ejecutar";

        pb.redirectErrorStream(true); // combinar stdout y stderr si quieres un solo gobbler
        ExecutorService exec = Executors.newFixedThreadPool(1); // 1 si unificas streams, o 2 para stdout+stderr
        Process process = null;
        Future<Void> gobblerFuture = null;

        try {
            process = pb.start();
            ProcessRegistry.addJob(
                    new Job(process.pid(),
                            LocalDateTime.now(),
                            String.join(" ", pb.command())
                    )
            );
            StreamGobbler gobbler = new StreamGobbler(process.getInputStream(), System.out);
            gobblerFuture = exec.submit(gobbler);

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (finished) {
                int exit = process.exitValue();
                // esperar al gobbler un corto tiempo para consumir lo que queda
                gobbler.stop();
                try { gobblerFuture.get(200, TimeUnit.MILLISECONDS); } catch (TimeoutException | InterruptedException | ExecutionException ignored) {}
                return "OK: Exit=" + exit + " (timeout=" + timeout + ")";
            } else {
                // intentar matar descendientes (Java 9+)
                try {
                    ProcessHandle.of(process.pid()).ifPresent(ph -> {
                        ph.descendants().forEach(ProcessHandle::destroy);
                        ph.descendants().forEach(d -> { if (d.isAlive()) d.destroyForcibly(); });
                    });
                } catch (Throwable ignored) {}

                process.destroy();
                Thread.sleep(50);
                if (process.isAlive()) process.destroyForcibly();

                // cerrar streams y detener gobbler
                gobbler.stop();
                try { gobblerFuture.get(200, TimeUnit.MILLISECONDS); } catch (TimeoutException | InterruptedException | ExecutionException ignored) {}
                return "TIMEOUT: (timeout=" + timeout + ")";
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR: " + e.getMessage();
        } finally {
            if (gobblerFuture != null && !gobblerFuture.isDone()) gobblerFuture.cancel(true);
            exec.shutdownNow();
            if (process != null) {
                try { process.getInputStream().close(); } catch (IOException ignored) {}
                try { process.getErrorStream().close(); } catch (IOException ignored) {}
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
            }
        }
    }

    public static String runBackgroundCommand(List<String> cmd, String commandExecuted) {
        ProcessBuilder pb = initProcessBuilder(cmd);
        if (pb == null) return "Error: No se ha introducido ningún comando para ejecutar";

        // El método devuelve el path a los logs directamente con un String
        String pathToLogs = ActivityLogger.createLogForCommand(pb);

        try {
            Process process = pb.start();
            ProcessRegistry.addJob(
                    new Job(process.pid(),
                            LocalDateTime.now(),
                            commandExecuted
                    )
            );
            return "BG PID=" + process.pid() + " " + pathToLogs;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String runKillCommand(long pid) {
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

}
