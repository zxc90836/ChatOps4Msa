package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import ntou.soselab.chatops4msa.Service.DiscordService.JDAService;
import ntou.soselab.chatops4msa.Service.TrelloService.CreateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrelloToolkit extends ToolkitFunction {
    private final CreateService createService;

    @Autowired
    public TrelloToolkit(CreateService createService) {
        this.createService = createService;

    }

    public void toolkitTrelloCreateCard(String board_id, String list_name, String card_name, String card_desc) throws IOException, InterruptedException {
        createService.CreateCard(board_id, list_name, card_name, card_desc);
    }
}

