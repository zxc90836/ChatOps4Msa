package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import ntou.soselab.chatops4msa.Service.DiscordService.UserContextHolder;
import ntou.soselab.chatops4msa.Service.NLPService.LLMService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.json.JSONArray;
import org.springframework.util.FileCopyUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LlmToolkit is responsible for invoking LLM with optional prompt templates
 * and retaining short-term dialogue history per user for contextual reasoning.
 */
@Component
public class LlmToolkit {

    private final LLMService llmService;
    private final int MAX_HISTORY_SIZE = 10; // Maximum rounds (each round = user + assistant)
    private final long EXPIRE_MILLIS = 10 * 60 * 1000; // 10 minutes

    private static class UserChatRecord {
        List<JSONObject> messages = new ArrayList<>();
        long lastAccess = System.currentTimeMillis();
    }

    private final Map<String, UserChatRecord> chatHistoryMap = new ConcurrentHashMap<>();

    @Autowired
    public LlmToolkit(LLMService llmService) {
        this.llmService = llmService;
    }

    public String toolkitLlmCall(String prompt, String prompt_template) {
        try {
            String userId = UserContextHolder.getUserId();
            if (userId == null || userId.isBlank()) {
                throw new RuntimeException("User ID not found in context.");
            }

            cleanUpExpiredHistory();
            UserChatRecord chatRecord = chatHistoryMap.getOrDefault(userId, new UserChatRecord());
            if (chatRecord.messages.isEmpty()){
                // if empty insert language system prompt
                JSONObject languagePrompt = new JSONObject();
                languagePrompt.put("role", "system");
                languagePrompt.put("content", "請根據使用者提問的語言自動選擇使用繁體中文或英文回覆，並保持一致。請勿主動切換語言。");
                chatRecord.messages.add(languagePrompt);
            }
            String fullPrompt = prompt;
            if (prompt_template != null && !prompt_template.isBlank() && !prompt_template.equalsIgnoreCase("none")) {
                String promptFilePath = "prompts/" + prompt_template + ".txt";
                ClassPathResource resource = new ClassPathResource(promptFilePath);
                if (resource.exists()) {
                    String template = loadSystemPrompt(promptFilePath);
                    fullPrompt = template + "\n" + prompt;
                    System.out.println("[INFO] Loaded template: " + promptFilePath);
                } else {
                    System.out.println("[WARN] Template not found: " + promptFilePath);
                }
            }

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", fullPrompt);
            chatRecord.messages.add(userMessage);

            // Estimate token length and trim if needed
            while (estimateTotalTokens(chatRecord.messages) > 20000 && chatRecord.messages.size() > 1) {
                chatRecord.messages.remove(0);
                System.out.println("[WARN] Estimate token length and trim.");
            }

            JSONArray messageArray = new JSONArray(chatRecord.messages);
            String response = llmService.callAPIFromOutside(messageArray);

            JSONObject assistantMessage = new JSONObject();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", response);
            chatRecord.messages.add(assistantMessage);

            chatRecord.lastAccess = System.currentTimeMillis();
            chatHistoryMap.put(userId, chatRecord);

            return response;

        } catch (IOException | JSONException e) {
            throw new RuntimeException("Toolkit LLM error occurred during prompt generation.", e);
        }
    }
    public String toolkitLlmRag(String query_text, String table_name) {
        try {
            // 載入 YAML 對照表
            String filePath = "prompts/" + table_name + ".txt";
            ClassPathResource resource = new ClassPathResource(filePath);
            if (!resource.exists()) throw new RuntimeException("RAG file not found: " + filePath);

            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String yamlContent = new String(bytes, StandardCharsets.UTF_8);

            List<Map<String, Object>> table = parseYamlList(yamlContent);
            List<String> queryTerms = splitQueryTerms(query_text);

            // 已加入的項目 key（防止重複出現）
            Set<String> usedKeys = new HashSet<>();
            List<MatchResult> finalMatches = new ArrayList<>();

            for (String query : queryTerms) {
                // 對每個 query 找最相似的前3筆 entry（不包含已出現過的 key）
                List<MatchResult> tempMatches = new ArrayList<>();

                for (Map<String, Object> entry : table) {
                    String key = (String) entry.get("key");
                    if (usedKeys.contains(key)) continue;

                    List<String> aliases = (List<String>) entry.get("aliases");
                    int maxScore = 0;

                    for (String alias : aliases) {
                        int score = keywordMatchScore(alias, query);
                        maxScore = Math.max(maxScore, score);
                    }

                    if (maxScore > 0) {
                        tempMatches.add(new MatchResult(entry, maxScore));
                    }
                }

                // 取每個 query 的 top 3 相似 entry
                tempMatches.sort((a, b) -> Integer.compare(b.score, a.score));
                int limit = Math.min(3, tempMatches.size());

                for (int i = 0; i < limit; i++) {
                    MatchResult match = tempMatches.get(i);
                    String key = (String) match.entry.get("key");
                    if (!usedKeys.contains(key)) {
                        finalMatches.add(match);
                        usedKeys.add(key);
                    }
                }
            }

            // 統一扁平輸出（讓 LLM 後續處理）
            if (finalMatches.isEmpty()) {
                return "⚠️ 無法根據關鍵詞找到對應的指標定義。請確認提問內容是否明確。";
            }

            List<String> results = new ArrayList<>();
            for (MatchResult match : finalMatches) {
                Map<String, Object> item = match.entry;
                results.add(String.format(
                        "名稱：%s\n定義：%s\n範例：%s",
                        item.get("key"),
                        item.get("zh_definition"),
                        item.get("zh_example")
                ));
            }

            return String.join("\n\n", results);

        } catch (Exception e) {
            throw new RuntimeException("Error in toolkitLlmTableSearch", e);
        }
    }


    // 用來包裝每一筆配對結果與其相似度分數
    private static class MatchResult {
        Map<String, Object> entry;
        int score;

        public MatchResult(Map<String, Object> entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    // YAML 轉換為物件列表
    private List<Map<String, Object>> parseYamlList(String yamlContent) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        return yaml.load(yamlContent);
    }
    // 使用餘弦相似度計算文字相似性
    private int keywordMatchScore(String alias, String query) {
        alias = alias.toLowerCase();
        query = query.toLowerCase();

        // 若 query 中包含 alias（或反過來），直接給滿分
        if (query.contains(alias) || alias.contains(query)) {
            return 100;
        }

        // 建立兩字串的詞頻向量
        Map<String, Integer> aliasVec = toTermFrequency(alias);
        Map<String, Integer> queryVec = toTermFrequency(query);

        // 計算餘弦相似度並轉換為 0～100 的整數分數
        double sim = cosineSimilarity(aliasVec, queryVec);
        return (int) (sim * 100);
    }
    private Map<String, Integer> toTermFrequency(String text) {
        Map<String, Integer> freq = new HashMap<>();
        // 使用正規表示式切分字串（包含中英文空格與標點）
        for (String token : text.toLowerCase().split("[\\s\\p{Punct}，、。]+")) {
            if (token.length() >= 2) {
                freq.put(token, freq.getOrDefault(token, 0) + 1);
            }
        }
        return freq;
    }
    private double cosineSimilarity(Map<String, Integer> v1, Map<String, Integer> v2) {
        Set<String> terms = new HashSet<>(v1.keySet());
        terms.addAll(v2.keySet()); // 整併所有出現過的詞作為維度

        int dot = 0;
        double norm1 = 0.0, norm2 = 0.0;
        for (String term : terms) {
            int a = v1.getOrDefault(term, 0);
            int b = v2.getOrDefault(term, 0);
            dot += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        return (norm1 == 0 || norm2 == 0) ? 0.0 : dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }


    private List<String> splitQueryTerms(String query_text) {
        // 根據逗號、頓號、空格切分
        String[] terms = query_text.split("[,，、\\s]+");
        List<String> cleaned = new ArrayList<>();
        for (String t : terms) {
            String term = t.trim();
            if (!term.isEmpty()) cleaned.add(term);
        }
        return cleaned;
    }

    private String loadSystemPrompt(String promptFilePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(promptFilePath);
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // 將「上次使用時間距今已超過 EXPIRE_MILLIS」的使用者對話紀錄移除
    private void cleanUpExpiredHistory() {
        long now = System.currentTimeMillis();
        chatHistoryMap.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > EXPIRE_MILLIS);
    }
    //粗略預估文字所佔用的 token 數
    private int estimateToken(String text) {
        return text.length() / 4; // rough estimation
    }

    private int estimateTotalTokens(List<JSONObject> messages) {
        int total = 0;
        for (JSONObject msg : messages) {
            total += estimateToken(msg.optString("content", ""));
        }
        return total;
    }
}
