package ntou.soselab.chatops4msa.Service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CapabilityOrchestrator {

//        String methodName = "someMethod";
//    SomeClass obj = new SomeClass();
//    Method method = obj.getClass().getMethod(methodName);
//    method.invoke(obj);

    public String performTheCapability(String functionName, Map parameterMap) {
        // TODO: perform the capability
        return "ok";
    }

    private String extractToolkitClassName(String functionName) {
        return "Toolkit" + functionName.split("-")[1];
    }

    private String KebabCaseToCamelCase(String kebabCaseFunctionName) {
        String[] words = kebabCaseFunctionName.split("-");
        StringBuilder CamelCaseFunctionName = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            String capitalized = words[i].substring(0, 1).toUpperCase() + words[i].substring(1);
            CamelCaseFunctionName.append(capitalized);
        }
        return CamelCaseFunctionName.toString();
    }
}
