package ru.just.tictactoe5x5ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class GomokuGameApp extends Application {

    private final String URL_WS;
    private final String URL_REST;

    {
        String host = "localhost";
        try (var hostnameFile = GomokuGameApp.class.getClassLoader().getResource("hostname").openStream()) {
            host = new String(hostnameFile.readAllBytes());
        } catch (Exception e) {
            System.out.println("Ошибка при парсинге файла hostname");
        }
        URL_WS = "ws://" + host + ":8080/ws";
        URL_REST = "http://" + host + ":8080/api/v1/tic-tac-toe-5x5";
    }

    private StompSession stompSession;
    private UUID roomId;
    private UUID userId;
    private UUID ownerId;
    private char currentPlayerSymbol;
    private Button[][] boardButtons = new Button[15][15];
    private Button copyRoomButton;
    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        userId = loadOrGenerateUserId();
        setupUI(primaryStage);
    }

    private UUID loadOrGenerateUserId() {
//        try {
            return UUID.randomUUID();
            // FIXME: раскомментировать
//            File file = new File("user-id.txt");
//            if (file.exists()) {
//                String id = new String(Files.readAllBytes(file.toPath()));
//                return UUID.fromString(id);
//            } else {
//                UUID newId = UUID.randomUUID();
//                Files.write(Paths.get("user-id.txt"), newId.toString().getBytes());
//                return newId;
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            return UUID.randomUUID();
//        }
    }

    private void setupUI(Stage primaryStage) {
        BorderPane mainLayout = new BorderPane();
        GridPane gridPane = new GridPane();

        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                Button cell = new Button();
                cell.setMinSize(40, 40);
                int finalRow = row;
                int finalCol = col;
                cell.setOnAction(event -> handleCellClick(finalRow, finalCol));
                boardButtons[row][col] = cell;
                gridPane.add(cell, col, row);
            }
        }

        Button createRoomButton = new Button("Создать комнату");
        createRoomButton.setOnAction(event -> createGameRoom());

        // Кнопка копирования UUID комнаты
        copyRoomButton = new Button("Копировать UUID комнаты");
        copyRoomButton.setOnAction(event -> copyRoomIdToClipboard());
        copyRoomButton.setDisable(true); // По умолчанию отключена, пока комната не создана

        Button joinRoomButton = new Button("Присоединиться к игре");
        joinRoomButton.setOnAction(event -> showJoinGameDialog());

        HBox topButtons = new HBox(10, createRoomButton, copyRoomButton, joinRoomButton);
        topButtons.setAlignment(Pos.CENTER);
        mainLayout.setTop(topButtons);
        mainLayout.setCenter(gridPane);

        primaryStage.setScene(new Scene(mainLayout, 600, 630));
        primaryStage.setTitle("Гомоку");
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void createGameRoom() {
        if (stompSession != null) stompSession.disconnect();
        eraseTextOnButtons();
        ResponseEntity<UUID> response = restTemplate.postForEntity(URL_REST + "?userId=" + userId, null, UUID.class);
        roomId = response.getBody();
        copyRoomButton.setDisable(false);
        System.out.println("Комната создана: " + roomId);

        setupWebSocket();
    }

    private void copyRoomIdToClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(roomId.toString());
        clipboard.setContent(content);
        System.out.println("UUID комнаты скопирован в буфер обмена: " + roomId);
    }

    private void eraseTextOnButtons() {
        Arrays.stream(boardButtons).flatMap(Arrays::stream).forEach(button -> {
            button.setDisable(false);
            button.setText("");
        });
    }

    private void showJoinGameDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Ввод UUID комнаты");

        TextField uuidField = new TextField();
        uuidField.setPromptText("Введите UUID комнаты");

        Button joinButton = new Button("Присоединиться");
        joinButton.setOnAction(e -> {
            if (stompSession != null) stompSession.disconnect();
            eraseTextOnButtons();
            String uuid = uuidField.getText();
            if (!joinGame(uuid)) {
                Dialog<String> errorDialog = new Dialog<>();
                errorDialog.setTitle("Ошибка");
                Text text = new Text("Комнаты не существует");
                VBox pane = new VBox(text);
                errorDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL); // добавляем кнопку отмены
                errorDialog.getDialogPane().setContent(pane);
                errorDialog.showAndWait();
            }
            System.out.println("Присоединился к комнате с UUID: " + uuid);
            dialog.close();
        });
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL); // добавляем кнопку отмены

        VBox dialogPane = new VBox(uuidField, joinButton);
        dialog.getDialogPane().setContent(dialogPane);
        dialog.showAndWait();
    }

    private boolean joinGame(String uuid) {
        roomId = UUID.fromString(uuid);
        GameRoomDto gameRoomDto;
        try {
            gameRoomDto = restTemplate.getForObject(URL_REST + "/rooms/" + roomId, GameRoomDto.class);
            if (gameRoomDto == null) return false;
        } catch (HttpClientErrorException e) {
            return false;
        }

        ownerId = gameRoomDto.ownerId;

        setupWebSocket();

        StartGameDto startGameDto = new StartGameDto(userId);
        HttpEntity<StartGameDto> request = new HttpEntity<>(startGameDto);
        restTemplate.postForEntity(URL_REST + "/rooms/" + roomId, request, Void.class);

        try {
            char[][] gameState = objectMapper.readValue(gameRoomDto.gameState, new TypeReference<>() {});
            for (int i = 0; i < boardButtons.length; i++) {
                for (int j = 0; j < boardButtons[i].length; j++) {
                    boardButtons[i][j].setText(String.valueOf(gameState[i][j]));
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private void setupWebSocket() {
        WebSocketClient wsClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(wsClient))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        try {
            stompSession = stompClient.connectAsync(URL_WS, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {}).get();
            stompSession.subscribe("/topic/rooms/" + roomId, new GameStompFrameHandler());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void handleCellClick(int row, int col) {
        if (!boardButtons[row][col].getText().isBlank()) {
            return;
        }
        PickCellDto pickCellDto = new PickCellDto(userId, row, col);
        stompSession.send("/app/game/move/" + roomId, pickCellDto);

        boardButtons[row][col].setText(String.valueOf(currentPlayerSymbol));
    }

    private class GameStompFrameHandler extends StompSessionHandlerAdapter {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Object.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof byte[] bytePayload) {
                try {
                    PickCellDto pickCellDto = objectMapper.readValue(bytePayload, PickCellDto.class);
                    char symbol = pickCellDto.getUserId().equals(userId) ? currentPlayerSymbol : (currentPlayerSymbol == 'X' ? 'O' : 'X');
                    Platform.runLater(() -> boardButtons[pickCellDto.getRow()][pickCellDto.getColumn()].setText(String.valueOf(symbol)));
                    onTurnChanged(currentPlayerSymbol != symbol);
                    return;
                } catch (IOException e) {
                    System.out.println("Ошибка при парсинге как PickCellDto");
                }
                try {
                    WinnerMessage winnerMessage = objectMapper.readValue(bytePayload, WinnerMessage.class);
                    showWinnerAlert(winnerMessage.getWinnerId());
                    onTurnChanged(false);
                    return;
                } catch (IOException e) {
                    System.out.println("Ошибка при парсинге как WinnerMessage");
                }
                try {
                    StartGameMessage startGameMessage = objectMapper.readValue(bytePayload, StartGameMessage.class);
                    System.out.println("Старт игры! " + startGameMessage);
                    if (startGameMessage.xOwnerId.equals(userId)) {
                        currentPlayerSymbol = 'X';
                        onTurnChanged(true);
                    } else {
                        currentPlayerSymbol = 'O';
                        onTurnChanged(false);
                    }
                    return;
                } catch (IOException e) {
                    System.out.println("Ошибка при парсинге как StartGameMessage");
                }
            }
            System.out.println("Непредвиденная ошибка при парсинге сообщения с вебсокета");
        }
    }

    // Примерный метод обновления состояния кнопок в зависимости от текущего хода
    private void updateButtonStates(boolean isPlayerTurn) {
        Platform.runLater(() -> {
            Arrays.stream(boardButtons)
                    .flatMap(Arrays::stream)
                    .forEach(button -> button.setDisable(!isPlayerTurn));
        });
    }

    // Вызов метода при каждом изменении состояния игры
    public void onTurnChanged(boolean isPlayerTurn) {
        updateButtonStates(isPlayerTurn);
    }

    private void showWinnerAlert(UUID winnerId) {
        Platform.runLater(() -> {
            String content = winnerId.equals(userId) ? "Вы победили!" : "Вы проиграли!";

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Конец игры!");
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StartGameDto {
        private UUID secondPlayerId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PickCellDto {
        private UUID userId;
        private Integer row;
        private Integer column;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WinnerMessage {
        private UUID winnerId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StartGameMessage {
        private UUID xOwnerId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GameRoomDto {
        private UUID id;
        private UUID ownerId;
        private String gameState;
    }
}
