module ru.just.tictactoe5x5ui {
    requires java.base;

    requires javafx.controls;
    requires javafx.fxml;

    // Добавляем модули Spring для REST-запросов и WebSocket
    requires spring.web;
    requires spring.websocket;
    requires spring.messaging;
    requires lombok;
    requires org.apache.tomcat.embed.websocket;
    // Модуль Jackson для работы с JSON
    requires com.fasterxml.jackson.databind;

    opens ru.just.tictactoe5x5ui to javafx.fxml;
    exports ru.just.tictactoe5x5ui;
}
