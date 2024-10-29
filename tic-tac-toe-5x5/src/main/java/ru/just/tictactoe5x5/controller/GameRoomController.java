package ru.just.tictactoe5x5.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.just.tictactoe5x5.dto.GameRoomDto;
import ru.just.tictactoe5x5.dto.PickCellDto;
import ru.just.tictactoe5x5.dto.StartGameDto;
import ru.just.tictactoe5x5.model.GameRoom;
import ru.just.tictactoe5x5.service.GameService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tic-tac-toe-5x5")
@RequiredArgsConstructor
public class GameRoomController {
    private final GameService gameService;

    @PostMapping
    public ResponseEntity<UUID> createGameRoom(@RequestParam("userId") UUID userId) {
        return ResponseEntity.ok(gameService.createGameRoom(userId));
    }

    @PostMapping("/rooms/{roomId}")
    public ResponseEntity<Void> startGame(@PathVariable UUID roomId, @RequestBody StartGameDto startGameDto) {
        gameService.startGame(roomId, startGameDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}/rooms")
    public ResponseEntity<List<UUID>> getUserGameRooms(@PathVariable UUID userId) {
        return ResponseEntity.of(gameService.findUserGameRooms(userId));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<GameRoomDto> getRoomById(@PathVariable UUID roomId) {
        return ResponseEntity.of(gameService.findRoomById(roomId));
    }
}
