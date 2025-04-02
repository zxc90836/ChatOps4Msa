package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import ntou.soselab.chatops4msa.Service.NLPService.LLMService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.json.JSONArray;
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
     *
     * @param prompt context for gpt
     * @return gpt response
     */
    public String toolkitLlmCall(String prompt) {
        try {
            // 建立 JSON 陣列來存儲對話歷史
            JSONArray messages = new JSONArray();

            // 添加使用者訊息
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            // 將使用者訊息加入 JSON 陣列
            messages.put(userMessage);

            // 呼叫 GPT API，傳遞 `JSONArray`
            return llmService.callAPIFromOutside(messages);

        } catch (JSONException e) {
            throw new RuntimeException("JSON 格式錯誤", e);
        }
    }
}
