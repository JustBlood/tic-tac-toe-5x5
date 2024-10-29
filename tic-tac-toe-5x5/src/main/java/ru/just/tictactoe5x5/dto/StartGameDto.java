package ru.just.tictactoe5x5.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class StartGameDto {
    private UUID secondPlayerId;
}
