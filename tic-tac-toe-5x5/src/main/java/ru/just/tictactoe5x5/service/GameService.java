package ru.just.tictactoe5x5.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ru.just.tictactoe5x5.dto.*;
import ru.just.tictactoe5x5.model.GameRoom;
import ru.just.tictactoe5x5.repository.GameRoomRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    public static final Random generator = new Random();
    private final GameRoomRepository gameRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    public static final int winCount = 5;

    public UUID createGameRoom(UUID ownerId) {
        GameRoom gameRoom = new GameRoom();
        gameRoom.setId(UUID.randomUUID());
        gameRoom.setOwnerId(ownerId);
        try {
            gameRoom.setGameState(objectMapper.writeValueAsString(initializeGameState()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        gameRoomRepository.save(gameRoom);
        return gameRoom.getId();
    }

    public void startGame(UUID gameRoomId, StartGameDto startGameDto) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId).orElseThrow();
        gameRoom.setSecondPlayerId(startGameDto.getSecondPlayerId());
        if (generator.nextBoolean()) {
            gameRoom.setXOwner(gameRoom.getOwnerId());
            gameRoom.setOOwner(gameRoom.getSecondPlayerId());
        } else {
            gameRoom.setXOwner(gameRoom.getSecondPlayerId());
            gameRoom.setOOwner(gameRoom.getOwnerId());
        }
        gameRoomRepository.save(gameRoom);

        messagingTemplate.convertAndSend("/topic/rooms/" + gameRoomId, new StartGameMessage(gameRoom.getXOwner()));
    }

    public void pickCell(UUID gameRoomId, PickCellDto pickCellDto) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId).orElseThrow();

        if (gameRoom.getLastPickedPlayerId() == null && !pickCellDto.getUserId().equals(gameRoom.getXOwner())) {
            throw new IllegalStateException("Первым должен ходить игрок с крестиком");
        }

        if (gameRoom.getWinnerId() != null) {
            throw new IllegalStateException("Игра окончена");
        }

        try {
            char[][] board = objectMapper.readValue(gameRoom.getGameState(), new TypeReference<>() {});
            if (board[pickCellDto.getRow()][pickCellDto.getColumn()] != ' ') {
                throw new IllegalStateException("Клетка уже занята.");
            }
            board[pickCellDto.getRow()][pickCellDto.getColumn()] = Objects.equals(gameRoom.getXOwner(), pickCellDto.getUserId()) ? 'X' : 'O';
            gameRoom.setGameState(objectMapper.writeValueAsString(board));
            gameRoom.setLastPickedPlayerId(pickCellDto.getUserId());
            gameRoomRepository.save(gameRoom);
            messagingTemplate.convertAndSend("/topic/rooms/" + gameRoomId, pickCellDto);
            if (isPlayerWinInGame(gameRoom)) {
                gameRoom.setWinnerId(pickCellDto.getUserId());
                gameRoomRepository.save(gameRoom);
                messagingTemplate.convertAndSend("/topic/rooms/" + gameRoomId, new WinnerMessage(pickCellDto.getUserId()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isPlayerWinInGame(GameRoom gameRoom) throws JsonProcessingException {
        char[][] gameState = objectMapper.readValue(gameRoom.getGameState(), new TypeReference<>() {});
        int size = gameState.length;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                char cell = gameState[row][col];
                if (cell == ' ') continue; // Пропускаем пустые клетки

                // Проверка горизонтальной линии
                if (col + winCount <= size && checkLine(gameState, row, col, 0, 1, cell, winCount)) {
                    return true;
                }

                // Проверка вертикальной линии
                if (row + winCount <= size && checkLine(gameState, row, col, 1, 0, cell, winCount)) {
                    return true;
                }

                // Проверка диагонали вниз-вправо
                if (row + winCount <= size && col + winCount <= size && checkLine(gameState, row, col, 1, 1, cell, winCount)) {
                    return true;
                }

                // Проверка диагонали вниз-влево
                if (row + winCount <= size && col - winCount + 1 >= 0 && checkLine(gameState, row, col, 1, -1, cell, winCount)) {
                    return true;
                }
            }
        }
        return false;
    }



    private boolean checkLine(char[][] board, int row, int col, int rowDir, int colDir, char player, int winCount) {
        for (int i = 0; i < winCount; i++) {
            if (board[row + i * rowDir][col + i * colDir] != player) {
                return false;
            }
        }
        return true;
    }

    private char[][] initializeGameState() {
        char[][] gameState = new char[15][15];
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                gameState[i][j] = ' ';
            }
        }
        return gameState;
    }

    public Optional<List<UUID>> findUserGameRooms(UUID userId) {
        return Optional.of(
                gameRoomRepository.findAllByOwnerId(userId).stream()
                        .map(GameRoom::getId)
                        .collect(Collectors.toList())
        );
    }

    public Optional<GameRoomDto> findRoomById(UUID roomId) {
        return gameRoomRepository.findById(roomId).map(gr -> new GameRoomDto(gr.getId(), gr.getOwnerId(), gr.getGameState()));
    }
}
