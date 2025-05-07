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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Stores chat history and last access timestamp per userId.
     */
    private static class UserChatRecord {
        List<JSONObject> messages = new ArrayList<>();
        long lastAccess = System.currentTimeMillis();
    }

    private final Map<String, UserChatRecord> chatHistoryMap = new ConcurrentHashMap<>();

    @Autowired
    public LlmToolkit(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * @param prompt          User input to be sent to LLM
     * @param prompt_template  Optional prompt template filename (without .txt)
     * @return LLM response string
     */
    public String toolkitLlmCall(String prompt, String prompt_template) {
        try {
            String userId = UserContextHolder.getUserId();
            if (userId == null || userId.isBlank()) {
                throw new RuntimeException("User ID not found in context.");
            }

            cleanUpExpiredHistory();

            UserChatRecord chatRecord = chatHistoryMap.getOrDefault(userId, new UserChatRecord());

            // Load system prompt template if provided
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

            // Append user message
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", fullPrompt);
            chatRecord.messages.add(userMessage);

            // Trim to max history size (keep latest)
            while (chatRecord.messages.size() > MAX_HISTORY_SIZE * 2) {
                chatRecord.messages.remove(0); // Remove oldest message
            }

            // Call LLM with current history
            JSONArray messageArray = new JSONArray(chatRecord.messages);
            String response = llmService.callAPIFromOutside(messageArray);

            // Append assistant response
            JSONObject assistantMessage = new JSONObject();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", response);
            chatRecord.messages.add(assistantMessage);

            // Update timestamp and store
            chatRecord.lastAccess = System.currentTimeMillis();
            chatHistoryMap.put(userId, chatRecord);

            return response;

        } catch (IOException | JSONException e) {
            throw new RuntimeException("Toolkit LLM error occurred during prompt generation.", e);
        }
    }

    /**
     * Load the prompt file content from resources/prompts/
     */
    private String loadSystemPrompt(String promptFilePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(promptFilePath);
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Remove chat records not accessed in the last EXPIRE_MILLIS.
     */
    private void cleanUpExpiredHistory() {
        long now = System.currentTimeMillis();
        chatHistoryMap.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > EXPIRE_MILLIS);
    }
}