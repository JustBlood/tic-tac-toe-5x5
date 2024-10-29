package ru.just.tictactoe5x5.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PickCellDto {
    private UUID userId;
    private Integer row;
    private Integer column;
}
