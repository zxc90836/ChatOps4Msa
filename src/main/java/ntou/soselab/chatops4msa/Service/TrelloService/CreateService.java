package ntou.soselab.chatops4msa.Service.TrelloService;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class CreateService {
    private final String API_KEY;
    private final String TOKEN;

    @Autowired
    public CreateService(Environment env) {
        this.API_KEY = env.getProperty("trello.api");
        this.TOKEN = env.getProperty("trello.token");
    }

    public void CreateCard(String boardId, String listName, String cardName, String cardDesc) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(
                URI.create(String.format("https://api.trello.com/1/boards/%s/lists?key=%s&token=%s", boardId, API_KEY, TOKEN)))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var responseBody = response.body();
        System.out.println(response.body());

        var listId = findListId(responseBody, listName);
        if (listId != null) {
            // 將卡片名稱和描述編碼後放入 URI 中
            var uri = URI.create(String.format("https://api.trello.com/1/cards?key=%s&token=%s&idList=%s&name=%s&desc=%s",
                    API_KEY, TOKEN, listId, URLEncoder.encode(cardName, StandardCharsets.UTF_8), URLEncoder.encode(cardDesc, StandardCharsets.UTF_8)));

            // 發送新增卡片的請求
            request = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            //System.out.println(response.body());
        } else {
            System.out.println("List not found.");
        }
    }

    @Nullable
    private static String findListId(String responseBody, String listName) {
        try {
            // 將 JSON 字串轉換為 JSONArray
            JSONArray jsonArray = new JSONArray(responseBody);

            // 遍歷 JSONArray 中的每個 JSONObject
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject listObject = jsonArray.getJSONObject(i);

                // 從 JSONObject 中取出清單名稱和 ID
                String name = listObject.getString("name");
                String id = listObject.getString("id");

                // 如果清單名稱與目標名稱匹配，返回該清單的 ID
                if (listName.equals(name)) {
                    return id;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null; // 如果沒有找到匹配的清單，返回 null
    }
}
