package pkg.restoration.questions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads blank-line-separated DAT question records from files or classpath resources. */
public final class QuestionLoader {

    public List<EnvironmentalQuestion> loadDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Question directory does not exist: " + directory);
        }
        List<EnvironmentalQuestion> questions = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dat"))
                    .sorted().toList()) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    questions.addAll(parse(reader, file.toString()));
                }
            }
        }
        return List.copyOf(questions);
    }

    public List<EnvironmentalQuestion> loadResource(String resourcePath) throws IOException {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(normalized);
        if (stream == null) {
            throw new IOException("Question resource does not exist: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return parse(reader, resourcePath);
        }
    }

    private List<EnvironmentalQuestion> parse(BufferedReader reader, String source) throws IOException {
        List<EnvironmentalQuestion> questions = new ArrayList<>();
        Map<String, String> record = new LinkedHashMap<>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                addRecord(questions, record, source, lineNumber);
                record.clear();
            } else if (!trimmed.startsWith("#")) {
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    throw new IOException(source + ":" + lineNumber + ": expected key=value");
                }
                record.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        }
        addRecord(questions, record, source, lineNumber);
        return List.copyOf(questions);
    }

    private void addRecord(List<EnvironmentalQuestion> output, Map<String, String> values,
                           String source, int lineNumber) throws IOException {
        if (values.isEmpty()) {
            return;
        }
        try {
            List<String> choices = List.of(required(values, "choices").split("\\|", -1));
            String[] ranked = values.getOrDefault("answer", "").split(",");
            int best = Integer.parseInt(values.getOrDefault("best", ranked.length > 0 ? ranked[0] : ""));
            int second = Integer.parseInt(values.getOrDefault("second", ranked.length > 1 ? ranked[1] : ""));
            output.add(new EnvironmentalQuestion(
                    required(values, "id"), required(values, "prompt"), choices, best, second,
                    Double.parseDouble(values.getOrDefault("reward", "15")),
                    Double.parseDouble(values.getOrDefault("penalty", "10")),
                    values.get("feedback.correct"), values.get("feedback.wrong")));
        } catch (IllegalArgumentException exception) {
            throw new IOException(source + ": near line " + lineNumber + ": " + exception.getMessage(), exception);
        }
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }
}
