package com.scifi.reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AiClient {
    private static final String API_KEY = "98fd6c3bdc8c4c2083a79511543c1e3c.936dAWvK2v2ULpNo";
    private static final String CHAT_MODEL = "glm-4-flash";
    private static final String CHAT_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private static final String EMBED_MODEL = "embedding-2";
    private static final String EMBED_API_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // Calls the AI chat endpoint and returns plain text.
    public static String callAi(String systemPrompt, String userPrompt) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", CHAT_MODEL);
            JsonArray messages = new JsonArray();

            String formatRule = "\nFormatting requirement: output clean plain text only. Do not use Markdown markers such as **bold** or ### headings.";

            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt + formatRule);
            messages.add(sysMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userPrompt);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

            if (jsonResponse.has("error")) {
                return "API error: " + jsonResponse.getAsJsonObject("error").get("message").getAsString();
            }

            String rawContent = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
            return cleanMarkdown(rawContent);

        } catch (Exception e) {
            return "AI request failed: " + e.getMessage();
        }
    }

    // Generates text embeddings for RAG similarity search.
    public static double[] getEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) return new double[0];
        if (text.length() > 1500) text = text.substring(0, 1500);

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", EMBED_MODEL);
            requestBody.addProperty("input", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EMBED_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

            if (jsonResponse.has("error")) return new double[0];

            JsonArray vectorArray = jsonResponse.getAsJsonArray("data")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("embedding");

            double[] vector = new double[vectorArray.size()];
            for (int i = 0; i < vectorArray.size(); i++) {
                vector[i] = vectorArray.get(i).getAsDouble();
            }
            return vector;
        } catch (Exception e) {
            return new double[0];
        }
    }

    // Calculates cosine similarity for two vectors.
    public static double cosineSimilarity(double[] v1, double[] v2) {
        if (v1.length == 0 || v2.length == 0 || v1.length != v2.length) return 0.0;
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Removes Markdown markers from AI output.
    private static String cleanMarkdown(String text) {
        if (text == null) return "";
        String backticks = "`" + "`" + "`";
        text = text.replaceAll(backticks + "(?:json|[a-zA-Z]*)\n?", "");
        text = text.replaceAll(backticks, "");
        text = text.replaceAll("(?m)^#+\\s+", "");
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        text = text.replaceAll("__(.*?)__", "$1");
        text = text.replaceAll("\\*(.*?)\\*", "$1");
        text = text.replaceAll("(?m)^-\\s+", "");
        text = text.replaceAll("[\\u2022\\u25AA\\u00B7\\u25B6\\u2714\\u2716\\u27A4\\u279C\\u2794\\u279F]", "");
        text = text.replaceAll("[\\u3010\\u3011\\u300E\\u300F\\u3016\\u3017]", "");
        text = text.replaceAll("[\\u2605\\u2606\\u25CF\\u25A0\\u25C6\\u25B3\\u25BC\\u25B2]", "");
        return text.trim();
    }

    // Fast bidirectional translation.
    public static String translateText(String text) {
        if (text == null || text.trim().isEmpty()) return "Please select text first.";
        String sysPrompt = "You are a precise translation engine. Automatically detect the input language: if it is Chinese, translate it into English; otherwise, translate it into Chinese.\n" +
                "Strict rule: output only the final translation. Do not include explanations, pronunciation, prefixes such as \"Translation:\", or repeated source text.";
        return callAi(sysPrompt, text);
    }

    // Contextual word lookup.
    public static String contextualLookup(String word, String context, boolean isNight, String learningGoal) {
        if (word == null || word.trim().isEmpty()) return "No valid word selected.";
        String tone = isNight ? "Use a warm, patient late-night reading-companion tone." : "Use a professional, objective, sharp expert tone.";
        String goalPrompt = "The user's current learning goal is: " + learningGoal + ".";

        String sysPrompt = "You are a contextual vocabulary analysis engine. " + goalPrompt + tone + "\n" +
                "Explain the exact meaning of the target word in the given context. Output directly in this structure without Markdown:\n" +
                "1. Core meaning: one precise sentence\n" +
                "2. Context analysis: analyze how the word works in this passage\n" +
                "3. Learning link: provide a memory or usage suggestion based on the user's goal";
        return callAi(sysPrompt, "Context: " + context + "\nTarget word: " + word);
    }

    // Deep sentence-structure analysis.
    public static String analyzeStructure(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return "No valid sentence selected.";
        String sysPrompt = "You are an expert cognitive-linguistics sentence analysis engine. Perform a deep syntactic analysis of the user's sentence.\n" +
                "Strict rule: output the analysis directly with no pleasantries. Use spaces for indentation and do not use Markdown. Clearly break down:\n" +
                "Core structure: subject, predicate, object\n" +
                "Modifiers: attributives, adverbials, complements\n" +
                "Clauses: isolate each clause and analyze it at a second level.";
        return callAi(sysPrompt, sentence);
    }

    // Generates knowledge graph JSON.
    public static String generateGraphJson(String text) {
        if (text == null || text.trim().isEmpty()) return "{}";
        String jsonStructure = "{\n  \"nodes\": [\n    { \"data\": { \"id\": \"1\", \"label\": \"Entity name\" } }\n  ],\n  \"edges\": [\n    { \"data\": { \"source\": \"1\", \"target\": \"2\", \"label\": \"Relationship\" } }\n  ]\n}";
        String sysPrompt = "You are a pure knowledge-graph data generator. Analyze the input text, extract core entities as nodes, and extract relationships as edges.\n" +
                "Strict rules:\n1. Output valid JSON only.\n2. Node IDs must be unique.\nTarget example:\n" + jsonStructure;
        String jsonResult = callAi(sysPrompt, text);
        int startIndex = jsonResult.indexOf('{');
        int endIndex = jsonResult.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return jsonResult.substring(startIndex, endIndex + 1);
        } else {
            return "{ \"nodes\": [ { \"data\": { \"id\": \"error\", \"label\": \"Graph generation failed\" } } ] }";
        }
    }

    // Simplifies complex text.
    public static String simplifyText(String text, String learningGoal) {
        if (text == null || text.trim().isEmpty()) return "Please select text first.";
        String sysPrompt = "You are a clear knowledge-analysis tutor. The user's current learning goal is: " + learningGoal + ".\n" +
                "Rewrite the input text into extremely accessible plain language. Be direct, logical, vivid, and concise. Do not include filler or prefixes.";
        return callAi(sysPrompt, text);
    }

    // Simulates the original author answering questions.
    public static String authorChat(String context, String question) {
        if (context == null || context.trim().isEmpty()) return "Please select text first.";
        String sysPrompt = "You are the original author of the background passage. Answer the reader's question by drawing on the passage's mood and your imagined creative intent.\n" +
                "Background passage: " + context;
        return callAi(sysPrompt, question);
    }

    // Compresses text into a memory chunk for RAG.
    public static String compressMemoryForRag(String text) {
        if (text == null || text.trim().isEmpty()) return "Text is empty.";
        String sysPrompt = "You are a memory compression engine. Summarize the input text's core idea in one extremely concise sentence and extract the most important entities and concepts.\n" +
                "Strict rule: output only that concise sentence, with no filler.";
        return callAi(sysPrompt, text);
    }

    // Finds semantic connections across books.
    public static String crossBookSpark(String currentText) {
        if (currentText == null || currentText.trim().isEmpty()) return "Please select text first.";
        String semanticMemoryDb = DatabaseManager.getSemanticContext(currentText, 5);
        String sysPrompt = "You are an insight engine that finds conceptual sparks. Based on the cross-book memories below:\n\n" + semanticMemoryDb + "\n\n"
                + "Analyze whether the user's current content resonates with those memories. Output a sharp, direct analysis.";
        return callAi(sysPrompt, currentText);
    }

    // Generates a four-option quiz for a vocabulary item.
    public static String[] generateQuizOptions(String word, String context) {
        String sysPrompt = "You are a strict linguistics tutor. Generate a single-choice quiz for the target vocabulary item.\n" +
                "Provide one correct common English definition, based on Cambridge-style dictionary usage, and three plausible but wrong definitions. Context is optional.\n" +
                "Strict rule: use exactly this multi-line format and include no extra text:\n" +
                "A. [Option 1]\nB. [Option 2]\nC. [Option 3]\nD. [Option 4]\nAnswer: [A/B/C/D]";
        String aiResp = callAi(sysPrompt, "Target word: " + word + "\nContext: " + context);

        String a = extractOption(aiResp, "A.");
        String b = extractOption(aiResp, "B.");
        String c = extractOption(aiResp, "C.");
        String d = extractOption(aiResp, "D.");
        String ans = extractAnswer(aiResp);
        return new String[]{a, b, c, d, ans};
    }

    private static String extractOption(String text, String prefix) {
        if (text == null) return prefix + " (parse failed)";
        for (String line : text.split("\n")) {
            if (line.trim().toUpperCase().startsWith(prefix)) return line.trim();
        }
        return prefix + " (option generation failed)";
    }

    private static String extractAnswer(String text) {
        if (text == null) return "A";
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains("Answer:") || lines[i].contains("Answer:")) {
                return lines[i].replaceAll(".*Answer:\\s*([A-Da-d]).*", "$1").toUpperCase();
            }
        }
        return "A";
    }
}