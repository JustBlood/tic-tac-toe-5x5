package ru.just.tictactoe5x5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.messaging.core.MessagePostProcessor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StartGameMessage {
    private UUID xOwnerId;
}
