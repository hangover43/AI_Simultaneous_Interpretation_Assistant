package com.interpretation.service;

import com.interpretation.config.WhisperAsrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WhisperAsrService {

    private static final Logger log = LoggerFactory.getLogger(WhisperAsrService.class);

    private final WhisperAsrProperties properties;

    public WhisperAsrService(WhisperAsrProperties properties) {
        this.properties = properties;
    }

    public Optional<String> transcribe(String sessionId, int sequence, String payloadBase64) {
        if (!properties.isEnabled() || payloadBase64 == null || payloadBase64.isBlank()) {
            return Optional.empty();
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("ai-interpretation-asr-" + safe(sessionId) + "-" + sequence + "-");
            Path webm = workDir.resolve("input.webm");
            Path wav = workDir.resolve("input.wav");
            Path outputBase = workDir.resolve("transcript");
            Path transcript = workDir.resolve("transcript.txt");

            Files.write(webm, Base64.getDecoder().decode(payloadBase64));
            run(List.of(
                    properties.getFfmpegPath(),
                    "-y",
                    "-i", webm.toString(),
                    "-ar", "16000",
                    "-ac", "1",
                    wav.toString()
            ), workDir, "ffmpeg");

            run(List.of(
                    properties.getCliPath(),
                    "-m", properties.getModelPath(),
                    "-f", wav.toString(),
                    "-l", "auto",
                    "-nt",
                    "-np",
                    "-otxt",
                    "-of", outputBase.toString()
            ), Path.of("").toAbsolutePath(), "whisper.cpp");

            if (!Files.exists(transcript)) {
                log.warn("Whisper transcript file was not created for session {} sequence {}.", sessionId, sequence);
                return Optional.empty();
            }
            String text = Files.readString(transcript, StandardCharsets.UTF_8)
                    .replaceAll("\\[[^]]+]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (text.isBlank()) {
                log.info("Whisper returned empty transcript for session {} sequence {}.", sessionId, sequence);
                return Optional.empty();
            }
            log.info("ASR transcript for session {} sequence {}: {}", sessionId, sequence, text);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (RuntimeException | IOException exception) {
            log.warn("ASR failed for session {} sequence {}: {}", sessionId, sequence, exception.getMessage());
            return Optional.empty();
        } finally {
            cleanup(workDir);
        }
    }

    private void run(List<String> command, Path workDir, String name) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workDir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("{} timed out after {} seconds.", name, properties.getTimeoutSeconds());
                throw new IllegalStateException(name + " timed out.");
            }
            if (process.exitValue() != 0) {
                log.warn("{} failed with exit code {}.", name, process.exitValue());
                throw new IllegalStateException(name + " failed with exit code " + process.exitValue());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run " + name + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(name + " was interrupted.", exception);
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static void cleanup(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try (var paths = Files.walk(workDir)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
