package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Component
public class RestapiToolkit extends ToolkitFunction {

    public String toolkitRestapiGet(String url) {
        RestTemplate restTemplate = new RestTemplate();
        url = url.replaceAll("\"", "");
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        return responseEntity.getBody();
    }
    public String toolkitRestapiAuthGet(String url, String api_token) {
        RestTemplate restTemplate = new RestTemplate();
        url = url.replaceAll("\"", "");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString((api_token + ":").getBytes()));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return responseEntity.getBody();
    }

    public String toolkitRestapiPost(String url, String body, String authorization) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorization);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        return responseEntity.getBody();
    }
}
