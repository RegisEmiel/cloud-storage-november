package com.geekbrains;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Slf4j
public class MainController implements Initializable {
    private static final String CLIENT_DIRECTORY = "cloud-storage-november-client\\client";
    private Path clientDir;
    public ListView<String> clientView;
    public ListView<String> serverView;
    public TextField input;
    private DataInputStream is;
    private DataOutputStream os;

    private static byte[] buffer = new byte[1024];

    private String selectedFileName = "";

    public String getSelectedFileName() {
        return selectedFileName;
    }

    public void setSelectedFileName(String selectedFileName) {
        this.selectedFileName = selectedFileName;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            clientDir = Paths.get("cloud-storage-november-client", "client");
            if (!Files.exists(clientDir)) {
                Files.createDirectory(clientDir);
            }

            clientView.getItems().clear();
            clientView.getItems().addAll(getFiles(clientDir));
            clientView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String item = clientView.getSelectionModel().getSelectedItem();
                    input.setText(item);
                    setSelectedFileName(item);
                }
            });

            Socket socket = new Socket("localhost", 8189);
            is = new DataInputStream(socket.getInputStream());
            os = new DataOutputStream(socket.getOutputStream());
            Thread readThread = new Thread(this::read);
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    private void read() {
        try {
            while (true) {
                String msg = is.readUTF();
                log.debug("Received: {}", msg);
                Platform.runLater(() -> serverView.getItems().add(msg));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(ActionEvent actionEvent) throws IOException {
//        String text = input.getText();
//        os.writeUTF(text);
//        os.flush();
//        input.clear();
        // TODO: 28.10.2021 Передать файл на сервер

        sendFile(getSelectedFileName());
    }

    private void sendFile(String fileName) throws IOException {
        Path file = Paths.get(String.valueOf(clientDir), fileName);
        long fileSize = Files.size(file);
        int read = 0;

        os.writeUTF(fileName);
        os.writeLong(fileSize);

        InputStream fileInputStream = Files.newInputStream(file);

        while ((read = fileInputStream.read(buffer)) != -1)
            os.write(buffer, 0, read);

        os.flush();
    }

    public void sendFileAction(ActionEvent actionEvent) throws IOException {
        sendFile(getSelectedFileName());
    }
}
