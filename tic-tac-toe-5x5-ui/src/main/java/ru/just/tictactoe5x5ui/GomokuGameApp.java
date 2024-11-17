package ru.just.tictactoe5x5ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class GomokuGameApp extends Application {

    private static final String URL_WS = "ws://localhost:8080/ws";
    private static final String URL_REST = "http://localhost:8080/api/v1/tic-tac-toe-5x5";

    private StompSession stompSession;
    private UUID roomId;
    private UUID userId;
    private char currentPlayerSymbol;
    private Button[][] boardButtons = new Button[15][15];
    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper objectMapper = new ObjectMapper();
    private Stage primaryStage;
    private Scene startScene;
    private Scene gameScene;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        userId = loadOrGenerateUserId();
        this.primaryStage = primaryStage;
        setupStartScene();
        primaryStage.setScene(startScene);
        primaryStage.setTitle("Гомоку");
        primaryStage.getIcons().add(new Image("file:src/main/resources/icon.png"));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private UUID loadOrGenerateUserId() {
        try {
            File file = new File("user-id.txt");
            if (file.exists()) {
                String id = new String(Files.readAllBytes(file.toPath()));
                return UUID.fromString(id);
            } else {
                UUID newId = UUID.randomUUID();
                Files.write(Paths.get("user-id.txt"), newId.toString().getBytes());
                return newId;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return UUID.randomUUID();
        }
    }

    private void setupStartScene() {
        VBox gamesBox = new VBox(10); // Отступы между кнопками
        gamesBox.setPadding(new Insets(20));

        List<?> unfinishedGames;
        ResponseEntity<List> response = restTemplate.getForEntity(URL_REST + "/users/" + userId + "/unfinished", List.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            unfinishedGames = response.getBody();
            // Создаем кнопки для каждой незаконченной игры
            for (var gameId : unfinishedGames) {
                Button gameButton = new Button(gameId.toString());
                gameButton.setOnAction(event -> continueGame(UUID.fromString(gameId.toString()))); // Обработчик нажатия
                gameButton.getStyleClass().add("normal-button");
                gamesBox.getChildren().add(gameButton); // Добавляем кнопку в VBox
            }
        }
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(gamesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll");


        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("main-layout");

        Label titleLabel = new Label("Добро пожаловать в Гомоку!");
        Label infoLabel1 = new Label("Вы можете начать новую игру:");
        Label infoLabel2 = new Label("Или продолжить незаконченную игру из списка ниже:");
        Label infoLabel3 = new Label("У вас пока нет незаконченных игр");
        Button newGameButton = new Button("Создать новую игру");
        Button joinRoomButton = new Button("Присоединиться к игре");

        newGameButton.setOnAction(event -> startNewGame());
        joinRoomButton.setOnAction(event -> showJoinGameDialog());

        titleLabel.getStyleClass().add("title-label");
        infoLabel1.getStyleClass().add("info-label");
        infoLabel2.getStyleClass().add("info-label");
        infoLabel3.getStyleClass().add("info-label");
        newGameButton.getStyleClass().add("normal-button");
        joinRoomButton.getStyleClass().add("normal-button");

        HBox newGameButtons = new HBox(10);
        newGameButtons.setAlignment(Pos.CENTER);
        newGameButtons.getChildren().addAll(newGameButton, joinRoomButton);

        layout.getChildren().addAll(titleLabel, infoLabel1, newGameButtons, infoLabel2, gamesBox.getChildren().isEmpty() ? infoLabel3 : scrollPane);

        startScene = new Scene(layout, 640, 710);
        startScene.getStylesheets().add("styles.css");
    }

    private void startNewGame() {
        createGameRoom();
        setupGameScene();
        primaryStage.setScene(gameScene);
    }

    private void setupGameScene() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-layout");
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(20));

        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                Button cell = new Button();
                cell.getStyleClass().add("cell-button");
                int finalRow = row;
                int finalCol = col;
                cell.setOnAction(event -> handleCellClick(finalRow, finalCol));
                boardButtons[row][col] = cell;
                gridPane.add(cell, col, row);
            }
        }

        Button saveAndExitButton = new Button("Сохранить и выйти");
        saveAndExitButton.getStyleClass().add("normal-button");
        saveAndExitButton.setOnAction(event -> returnToStartScene());

        Button copyRoomButton = new Button("Копировать UUID комнаты");
        copyRoomButton.getStyleClass().add("normal-button");
        copyRoomButton.setOnAction(event -> copyRoomIdToClipboard());

        HBox topButtons = new HBox(10, copyRoomButton, saveAndExitButton);
        topButtons.setAlignment(Pos.CENTER);
        topButtons.setPadding(new Insets(20));
        mainLayout.setTop(topButtons);
        mainLayout.setCenter(gridPane);

        gameScene = new Scene(mainLayout, 640, 710);
        gameScene.getStylesheets().add("styles.css");
    }

    private void returnToStartScene() {
        setupStartScene();
        primaryStage.setScene(startScene);
        if (stompSession != null) {
            stompSession.disconnect();
            stompSession = null;
        }
    }

    private boolean continueGame(UUID uuid){
        roomId = uuid;
        GameRoomDto gameRoomDto;

        try {
            gameRoomDto = restTemplate.getForObject(URL_REST + "/rooms/" + roomId, GameRoomDto.class);
            if (gameRoomDto == null) return false;
        } catch (HttpClientErrorException e) {
            return false;
        }

        if(userId.equals(gameRoomDto.xOwnerId)) currentPlayerSymbol = 'X';
        else currentPlayerSymbol = 'O';

        if(userId.equals(gameRoomDto.lastPickedPlayerId)) {
            onTurnChanged(false);
        }

        setupWebSocket();
        setupGameScene();
        primaryStage.setScene(gameScene);

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

    private void createGameRoom() {
        if (stompSession != null) stompSession.disconnect();
        ResponseEntity<UUID> response = restTemplate.postForEntity(URL_REST + "?userId=" + userId, null, UUID.class);
        roomId = response.getBody();
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

    private void showJoinGameDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Ввод UUID комнаты");

        TextField uuidField = new TextField();
        uuidField.setPromptText("Введите UUID комнаты");

        ButtonType joinButtonType = new ButtonType("Присоединиться", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(joinButtonType, cancelButtonType);

        // Обработчик нажатия кнопки "Присоединиться"
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == joinButtonType) {
                if (stompSession != null) stompSession.disconnect();
                //eraseTextOnButtons();
                String uuid = uuidField.getText();
                if (!joinGame(uuid)) {
                    String content = "Комнаты не существует";
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка");
                    alert.setHeaderText(null);
                    alert.setContentText(content);
                    alert.getDialogPane().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                    alert.getDialogPane().getStyleClass().add("alert");
                    alert.showAndWait();
                }
                System.out.println("Присоединился к комнате с UUID: " + uuid);
                setupGameScene();
                primaryStage.setScene(gameScene);
                dialog.close();
            }
            return null;
        });

        dialog.getDialogPane().setContent(new VBox(uuidField));
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");
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

        setupWebSocket();

        StartGameDto startGameDto = new StartGameDto(userId);
        HttpEntity<StartGameDto> request = new HttpEntity<>(startGameDto);
        restTemplate.postForEntity(URL_REST + "/rooms/" + roomId, request, Void.class);
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
        Platform.runLater(() -> Arrays.stream(boardButtons)
                .flatMap(Arrays::stream)
                .forEach(button -> button.setDisable(!isPlayerTurn)));
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
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            alert.getDialogPane().getStyleClass().add("alert");
            alert.showAndWait();
            returnToStartScene();
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
        private UUID xOwnerId;
        private UUID lastPickedPlayerId;
    }
}
