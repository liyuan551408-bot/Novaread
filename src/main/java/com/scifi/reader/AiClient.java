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

    // 调用AI聊天接口，返回纯文本结果
    public static String callAi(String systemPrompt, String userPrompt) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", CHAT_MODEL);
            JsonArray messages = new JsonArray();

            String formatRule = "\n【格式要求：请直接输出排版清爽的纯文本，绝不要使用 **加粗** 或 ### 标题 等 Markdown 标记符号。】";

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
                return "API 异常: " + jsonResponse.getAsJsonObject("error").get("message").getAsString();
            }

            String rawContent = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
            return cleanMarkdown(rawContent);

        } catch (Exception e) {
            return "量子通信中断：" + e.getMessage();
        }
    }

    // 生成文本向量，用于RAG相似度检索
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

    // 计算两个向量的余弦相似度
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

    // 清理AI输出中的Markdown标记
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
        text = text.replaceAll("[•▪·▶✔✖➤➜➔➟]", "");
        text = text.replaceAll("[【】『』〖〗]", "");
        text = text.replaceAll("[★☆●■◆△▼▲]", "");
        return text.trim();
    }

    // 极速中英互译
    public static String translateText(String text) {
        if (text == null || text.trim().isEmpty()) return "请先选择文本。";
        String sysPrompt = "【最高指令】：你是一个无情的精准翻译机器。请自动识别输入文本的语言：如果是中文，请翻译为英文；如果是外文，请翻译为中文。\n" +
                "【严格限制】：只允许输出最终的翻译结果！绝不允许输出任何解释、拼音、前缀（如“翻译结果：”）、或者复读原文。";
        return callAi(sysPrompt, text);
    }

    // 语境查词（核心AI功能）
    public static String contextualLookup(String word, String context, boolean isNight, String learningGoal) {
        if (word == null || word.trim().isEmpty()) return "未选中有效词汇。";
        String tone = isNight ? "使用深夜陪读的温暖、耐心语气" : "使用专业、客观、犀利的极客语气";
        String goalPrompt = "用户的当前学习目标为：【" + learningGoal + "】。";

        String sysPrompt = "你是一个神经突触解析引擎。" + goalPrompt + tone + "\n" +
                "请精准解释【目标词】在给定【上下文】中的确切含义。请直接按以下结构输出文本（不要使用Markdown标记）：\n" +
                "1. 核心释义：(一句话精准概括)\n" +
                "2. 语境解析：(结合上下文具体分析)\n" +
                "3. 目标关联：(结合用户的学习目标，给出记忆或应用建议)";
        return callAi(sysPrompt, "上下文：" + context + "\n目标词：" + word);
    }

    // 句子结构深度分析
    public static String analyzeStructure(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return "未选中有效句子。";
        String sysPrompt = "你是一个精通认知语言学的神经解析引擎。请对用户输入的句子执行深度的【句法结构手术】。\n" +
                "【严格限制】：直接输出解析结果，拒绝任何客套话。请使用空格进行层级缩进排版（绝不使用Markdown格式），明确拆解出以下成分：\n" +
                "核心骨架（主、谓、宾）\n" +
                "修饰枝干（定、状、补）\n" +
                "若包含从句，必须单独剥离并进行二级拆解。";
        return callAi(sysPrompt, sentence);
    }

    // 生成知识图谱JSON
    public static String generateGraphJson(String text) {
        if (text == null || text.trim().isEmpty()) return "{}";
        String jsonStructure = "{\n  \"nodes\": [\n    { \"data\": { \"id\": \"1\", \"label\": \"实体名\" } }\n  ],\n  \"edges\": [\n    { \"data\": { \"source\": \"1\", \"target\": \"2\", \"label\": \"关系\" } }\n  ]\n}";
        String sysPrompt = "【最高指令】：你现在是一个纯粹的知识图谱数据生成器。请分析输入文本，提取核心实体为 nodes，提取实体间关系为 edges。\n" +
                "【生死红线】：\n1. 必须且只能输出合法JSON。\n2. 节点ID唯一。\n目标范例：\n" + jsonStructure;
        String jsonResult = callAi(sysPrompt, text);
        int startIndex = jsonResult.indexOf('{');
        int endIndex = jsonResult.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return jsonResult.substring(startIndex, endIndex + 1);
        } else {
            return "{ \"nodes\": [ { \"data\": { \"id\": \"error\", \"label\": \"图谱生成失败\" } } ] }";
        }
    }

    // 文本降维简化
    public static String simplifyText(String text, String learningGoal) {
        if (text == null || text.trim().isEmpty()) return "请先选择文本。";
        String sysPrompt = "你是一个擅长“降维打击”的知识解析导师。当前用户的学习目标为：【" + learningGoal + "】。\n" +
                "请将输入的复杂文本重构为极度通俗易懂的白话文。要求：直入主题，逻辑清晰，语言生动形象，绝不允许说废话或包含前缀。";
        return callAi(sysPrompt, text);
    }

    // 模拟原著作者回答问题
    public static String authorChat(String context, String question) {
        if (context == null || context.trim().isEmpty()) return "请先选择文本。";
        String sysPrompt = "你现在是这段【背景文本】的原著作者。请结合这段文本的意境和你的创作意图，亲自回答读者的提问。\n" +
                "背景文本：" + context;
        return callAi(sysPrompt, question);
    }

    // 压缩文本为记忆块（用于RAG）
    public static String compressMemoryForRag(String text) {
        if (text == null || text.trim().isEmpty()) return "文本为空。";
        String sysPrompt = "你是一个高维记忆压缩引擎。请用【绝对精简的一句话】概括输入文本的核心思想，提取最关键的实体和概念。\n" +
                "【严格限制】：直接输出这句概括的话，绝不允许包含废话。";
        return callAi(sysPrompt, text);
    }

    // 跨书语义共鸣检索
    public static String crossBookSpark(String currentText) {
        if (currentText == null || currentText.trim().isEmpty()) return "请先选择文本。";
        String semanticMemoryDb = DatabaseManager.getSemanticContext(currentText, 5);
        String sysPrompt = "你是一个寻找知识火花的“灵感碰撞引擎”。根据以下跨书记忆：\n\n【" + semanticMemoryDb + "】\n\n"
                + "请分析用户的【当前内容】，与上述记忆是否存在共鸣？直接输出犀利的分析结果。";
        return callAi(sysPrompt, currentText);
    }

    // 为生词自动生成4选1考核题
    public static String[] generateQuizOptions(String word, String context) {
        String sysPrompt = "你是一个严格的语言学导师。请根据用户提供的【目标词汇】，生成一个单项选择题。\n" +
                "要求提供1个该词汇最常用的正确中文释义（以剑桥词典为准）和3个具有迷惑性的错误释义。无需结合任何上下文。\n" +
                "【严格限制】：必须严格按以下多行格式输出，绝不能包含其他废话：\n" +
                "A. [选项1]\nB. [选项2]\nC. [选项3]\nD. [选项4]\n答案：[A/B/C/D]";
        String aiResp = callAi(sysPrompt, "目标词汇：" + word + "\n上下文：" + context);

        String a = extractOption(aiResp, "A.");
        String b = extractOption(aiResp, "B.");
        String c = extractOption(aiResp, "C.");
        String d = extractOption(aiResp, "D.");
        String ans = extractAnswer(aiResp);
        return new String[]{a, b, c, d, ans};
    }

    private static String extractOption(String text, String prefix) {
        if (text == null) return prefix + " (解析失败)";
        for (String line : text.split("\n")) {
            if (line.trim().toUpperCase().startsWith(prefix)) return line.trim();
        }
        return prefix + " (选项生成失败)";
    }

    private static String extractAnswer(String text) {
        if (text == null) return "A";
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains("答案：") || lines[i].contains("答案:")) {
                return lines[i].replaceAll(".*答案[：:]\\s*([A-Da-d]).*", "$1").toUpperCase();
            }
        }
        return "A";
    }
}