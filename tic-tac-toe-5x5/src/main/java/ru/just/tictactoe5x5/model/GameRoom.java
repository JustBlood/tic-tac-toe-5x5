package ru.just.tictactoe5x5.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.UUID;

@Entity
@Data
public class GameRoom {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID ownerId;
    @Column(nullable = true)
    private UUID secondPlayerId;
    @Column(nullable = true)
    private UUID winnerId;
    @Column(nullable = false, length = 10000)
    private String gameState;
    @Column(nullable = true)
    private UUID xOwner;
    @Column(nullable = true)
    private UUID oOwner;
    @Column(nullable = true)
    private UUID lastPickedPlayerId;
}
