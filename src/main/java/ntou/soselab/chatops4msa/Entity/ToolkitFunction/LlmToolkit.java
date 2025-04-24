package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

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
/**
 * Rename certain parameters to avoid conflicts with reserved keywords.
 * Implement toolkit-flow-return inside CapabilityOrchestrator.
 */
@Component
public class LlmToolkit {

    private final LLMService llmService;

    @Autowired
    public LlmToolkit(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * @param prompt           The user input content for LLM
     * @param prompt_template  Template file name under resources/prompts (without .txt extension)
     * @return LLM response string
     */
    public String toolkitLlmCall(String prompt, String prompt_template) {
        try {
            JSONArray messages = new JSONArray();
            String fullPrompt = prompt;

            if (prompt_template != null && !prompt_template.isBlank() && !prompt_template.equalsIgnoreCase("none")) {
                String promptFilePath = "prompts/" + prompt_template + ".txt";
                ClassPathResource resource = new ClassPathResource(promptFilePath);
                if (resource.exists()) {
                    String template = loadSystemPrompt(promptFilePath);
                    fullPrompt = template + "\n" + prompt;
                    System.out.println("[INFO] Prompt template loaded: " + promptFilePath);
                    System.out.println("[INFO] Prompt template loaded: " + fullPrompt);
                } else {
                    System.out.println("[WARN] Prompt template file not found: " + promptFilePath);
                }
            }

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", fullPrompt);
            messages.put(userMessage);

            return llmService.callAPIFromOutside(messages);

        } catch (IOException | JSONException e) {
            throw new RuntimeException("Toolkit LLM error occurred while generating prompt.", e);
        }
    }

    private String loadSystemPrompt(String promptFile) throws IOException {
        ClassPathResource resource = new ClassPathResource(promptFile);
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}


