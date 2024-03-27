package example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class TrelloController {

    @Value("${trello.api}")
    private String apiKey;

    @Value("${trello.token}")
    private String token;

    @PostMapping("/createCard")
    public String createCard(@RequestParam String boardId, @RequestParam String listName, @RequestParam String cardName, @RequestParam String cardDesc) {
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(
                    URI.create(String.format("https://api.trello.com/1/boards/%s/lists?key=%s&token=%s", boardId, apiKey, token)))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var responseBody = response.body();
            System.out.println(response.body());

            var listId = findListId(responseBody, listName);
            if (listId != null) {
                // 將卡片名稱和描述編碼後放入 URI 中
                var uri = URI.create(String.format("https://api.trello.com/1/cards?key=%s&token=%s&idList=%s&name=%s&desc=%s",
                        apiKey, token, listId, URLEncoder.encode(cardName, StandardCharsets.UTF_8), URLEncoder.encode(cardDesc, StandardCharsets.UTF_8)));

                // 發送新增卡片的請求
                request = HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } else {
                return "List not found.";
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error occurred.";
        }
    }

    private String findListId(String responseBody, String listName) {
        try {
            JSONArray jsonArray = new JSONArray(responseBody);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject listObject = jsonArray.getJSONObject(i);
                String name = listObject.getString("name");
                String id = listObject.getString("id");
                if (listName.equals(name)) {
                    return id;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
