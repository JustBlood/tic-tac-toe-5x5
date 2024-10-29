package ru.just.tictactoe5x5.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import ru.just.tictactoe5x5.dto.PickCellDto;
import ru.just.tictactoe5x5.service.GameService;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {
    private final GameService gameService;

    @MessageMapping("/game/move/{roomId}")
    public void sendMove(@Payload PickCellDto move, @DestinationVariable UUID roomId) {
        gameService.pickCell(roomId, move);
    }
}

