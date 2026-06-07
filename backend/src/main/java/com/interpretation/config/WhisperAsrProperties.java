package com.interpretation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asr.whisper")
public class WhisperAsrProperties {

    private boolean enabled = true;
    private String ffmpegPath = "ffmpeg";
    private String cliPath = "../tools/whisper.cpp/Release/whisper-cli.exe";
    private String modelPath = "../tools/whisper.cpp/models/ggml-base.bin";
    private int timeoutSeconds = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getCliPath() {
        return cliPath;
    }

    public void setCliPath(String cliPath) {
        this.cliPath = cliPath;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
