package com.geekbrains.lesson2_nio;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public class NioServer {
    private ByteBuffer buf;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Path currentDirectory;

    public NioServer() {
        buf = ByteBuffer.allocate(256);

        currentDirectory = Paths.get("cloud-storage-november-server", "server");

        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(8189));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.debug("Server started...");
            while (serverChannel.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                for (SelectionKey key : keys) {
                    if (key.isAcceptable()) {
                        handleAccept();
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                }
                keys.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = channel.read(buf);
            if (read == -1) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }
            if (read > 0) {
                buf.flip();
                while (buf.hasRemaining()) {
                    sb.append((char) buf.get());
                }
                buf.clear();
            }
        }

        log.debug("Received: {}", sb);

        String message = sb.toString().trim();
        String[] tokens = message.split(" ");
        String commandName = tokens[0];
        String fileName = "";

        if (tokens.length > 1)
            fileName = tokens[1];

        switch (commandName) {
            case "ls": {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(currentDirectory)) {
                    for (Path filePath : files) {
                        String strFilePath = filePath.toString() + (Files.isDirectory(filePath) ? "\\" : "") + "\n\r";
                        channel.write(ByteBuffer.wrap((strFilePath).getBytes(StandardCharsets.UTF_8)));
                    }
                }

                break;
            }
            case "cat": {
                Path pathFile = currentDirectory.resolve(fileName);
                List<String> lines = Files.readAllLines(pathFile);

                for (String str : lines) {
                    channel.write(ByteBuffer.wrap((str + "\n\r").getBytes(StandardCharsets.UTF_8)));
                }

                break;
            }
            case "cd..": {
                Path parentDirectory = currentDirectory.getParent();

                if (Files.exists(parentDirectory))
                    currentDirectory = parentDirectory;

                String strFilePath = currentDirectory.toString() + (Files.isDirectory(currentDirectory) ? "\\" : "") + "\n\r";
                channel.write(ByteBuffer.wrap((strFilePath).getBytes(StandardCharsets.UTF_8)));

                break;
            }
            case "cd": {
                if ("..".equals(fileName)) {
                    Path parentDirectory = currentDirectory.getParent();

                    if (Files.exists(parentDirectory))
                        currentDirectory = parentDirectory;

                    String strFilePath = currentDirectory.toString() + (Files.isDirectory(currentDirectory) ? "\\" : "") + "\n\r";
                    channel.write(ByteBuffer.wrap((strFilePath).getBytes(StandardCharsets.UTF_8)));

                    break;
                }

                Path newDirectory = currentDirectory.resolve(fileName);

                if (Files.exists(newDirectory)) {
                    currentDirectory = newDirectory;

                    String strFilePath = currentDirectory.toString() + (Files.isDirectory(currentDirectory) ? "\\" : "") + "\n\r";
                    channel.write(ByteBuffer.wrap((strFilePath).getBytes(StandardCharsets.UTF_8)));
                }

                break;
            }
            case "mkdir": {
                Path newDirectory = currentDirectory.resolve(fileName);

                if (!Files.exists(newDirectory)) {
                    Files.createDirectory(newDirectory);

                    String strFilePath = newDirectory.toString() + (Files.isDirectory(newDirectory) ? "\\" : "") + "\n\r";
                    channel.write(ByteBuffer.wrap(("Создана директория: " + strFilePath).getBytes(StandardCharsets.UTF_8)));
                }

                break;
            }
            case "exit":
                channel.close();

                return;
            default:
                if (!commandName.isEmpty()) {
                    channel.write(ByteBuffer.wrap(("Команда не распознана: " + message + "\n\r").getBytes(StandardCharsets.UTF_8)));
                }

                break;
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);

        log.debug("Client connected...");
    }

    public static void main(String[] args) {
        new NioServer();
    }

    private ArrayList<String> getTokens(StringBuilder stringBuilder) {
        ArrayList<String> tokens = new ArrayList<>();
        String message = stringBuilder.toString().trim();
        String[] tmp = message.split(" ");

        for (String token: tmp) {
            if (!token.isEmpty())
                tokens.add(token);
        }

        return tokens;
    }
}