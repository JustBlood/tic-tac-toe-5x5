package ru.just.tictactoe5x5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.just.tictactoe5x5.model.GameRoom;

import java.util.List;
import java.util.UUID;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {
    List<GameRoom> findAllByOwnerId(UUID ownerId);

    List<GameRoom> findByOwnerIdOrSecondPlayerId(UUID userId1, UUID userId2);
}
