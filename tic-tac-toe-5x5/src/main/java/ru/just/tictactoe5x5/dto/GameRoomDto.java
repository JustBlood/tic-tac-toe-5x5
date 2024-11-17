package ru.just.tictactoe5x5.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameRoomDto {
    private UUID id;
    private UUID ownerId;
    private String gameState;
    private UUID xOwnerId;
    private UUID lastPickedPlayerId;
}
