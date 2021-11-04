package com.geekbrains;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileAcceptHandler implements Runnable {
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    private boolean isRunning;

    private final Path serverDir;

    public FileAcceptHandler(Socket socket) throws IOException {
        serverDir = Paths.get("cloud-storage-november-server", "server");
        if (!Files.exists(serverDir)) {
            Files.createDirectory(serverDir);
        }

        dataInputStream = new DataInputStream(socket.getInputStream());
        dataOutputStream = new DataOutputStream(socket.getOutputStream());
        isRunning = true;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    @Override
    public void run() {
        final int SIZE = 1024;

        try {
            while (isRunning) {
                byte[] buffer = new byte[SIZE];

                String fileName = dataInputStream.readUTF();
                long fileSize = dataInputStream.readLong();

                Path file = Paths.get(String.valueOf(serverDir), fileName);

                int byteRead = 0;

                try (OutputStream fileOutputStream = Files.newOutputStream(file)) {
                    for (int i = 0; i < (fileSize + SIZE - 1) / SIZE; i++) {

                        byteRead = dataInputStream.read(buffer);

                        fileOutputStream.write(buffer, 0, byteRead);
                    }
                    fileOutputStream.flush();

                    dataOutputStream.writeUTF("File received: " + fileName);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
