package ru.just.tictactoe5x5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class WinnerMessage {
    private UUID winnerId;
}
