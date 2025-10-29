package infra;

import domain.Job;
import util.StreamGobbler;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;

public class ProcessLauncher {
    public static String execCommandWithTimeout(List<String> cmd, int timeout, String fileIn, String fileOut, String fileErr) {
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

    public static String execCommandWithTimeoutUsingGobbler(List<String> cmd, int timeout) {
        if (cmd.isEmpty()) {
            return "Error: No se ha introducido ningún comando para ejecutar";
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.redirectErrorStream(true); // combinar stdout y stderr si quieres un solo gobbler
        ExecutorService exec = Executors.newFixedThreadPool(1); // 1 si unificas streams, o 2 para stdout+stderr
        Process p = null;
        Future<Void> gobblerFuture = null;

        try {
            p = pb.start();
            StreamGobbler gobbler = new StreamGobbler(p.getInputStream(), System.out);
            gobblerFuture = exec.submit(gobbler);

            boolean finished = p.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (finished) {
                int exit = p.exitValue();
                // esperar al gobbler un corto tiempo para consumir lo que queda
                gobbler.stop();
                try { gobblerFuture.get(200, TimeUnit.MILLISECONDS); } catch (TimeoutException | InterruptedException | ExecutionException ignored) {}
                return "OK: Exit=" + exit + " (timeout=" + timeout + ")";
            } else {
                // intentar matar descendientes (Java 9+)
                try {
                    ProcessHandle.of(p.pid()).ifPresent(ph -> {
                        ph.descendants().forEach(ProcessHandle::destroy);
                        ph.descendants().forEach(d -> { if (d.isAlive()) d.destroyForcibly(); });
                    });
                } catch (Throwable ignored) {}

                p.destroy();
                Thread.sleep(50);
                if (p.isAlive()) p.destroyForcibly();

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
            if (p != null) {
                try { p.getInputStream().close(); } catch (IOException ignored) {}
                try { p.getErrorStream().close(); } catch (IOException ignored) {}
                try { p.getOutputStream().close(); } catch (IOException ignored) {}
            }
        }
    }

    public static String runBackgroundCommand(List<String> cmd, String commandExecuted) {
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
            return "BG PID=" + process.pid() + " OUT=logs/" + fileOut.getName() + " ERR=logs/" + fileErr.getName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
