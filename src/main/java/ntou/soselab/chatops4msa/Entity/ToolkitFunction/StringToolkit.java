package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ntou.soselab.chatops4msa.Exception.ToolkitFunctionException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Objects;
@Component
public class StringToolkit extends ToolkitFunction {

    /**
     * @param string   like "hello world"
     * @param original like "world"
     * @param replace  like "world2"
     * @return like "hello world2"
     */
    public String toolkitStringReplace(String string, String original, String replace) {
        return string.replaceAll(original, replace);
    }

    /**
     *
     * @param object any object,like 123, true, null, List, Map
     * @return string，like "123", "true", "null", "[1, 2, 3]"
     */
    public String toolkitStringConvert(Object object) {
        // 先轉成字串
        String result = Objects.toString(object, "null");
        // 轉換 `\n` 字串為真正的換行
        return result.replace("\\n", "\n");
    }

    /**
     * @param original   like "hello world"
     * @param join like "world2"
     * @return like "hello world2"
     */
    public String toolkitStringJoin(String original, String join) {
        return original+join;
    }
    /**
     * @param string    like "https://github.com/sheng-kai-wang/ChatOps4Msa-Sample-Bookinfo"
     * @param separator like "/"
     * @return like ["https:", "", "github.com", "sheng-kai-wang", "ChatOps4Msa-Sample-Bookinfo"]
     */
    public String toolkitStringSplit(String string, String separator) throws ToolkitFunctionException, InterruptedException {
        //Thread.sleep(2000);
        string = string.replaceAll("\\[\"", "").replaceAll("\"]", "");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String[] split = string.split(separator);
            return objectMapper.writeValueAsString(split);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param string like "6"
     * @param regex  like "^(?!(?:[1-9]|10)$)\d+$"
     * @return like "true"
     */
    public String toolkitStringPattern(String string, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(string);
        return matcher.matches() ? "true" : "false";
    }
}
