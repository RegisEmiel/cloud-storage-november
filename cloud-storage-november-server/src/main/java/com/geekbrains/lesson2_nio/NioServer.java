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
        ArrayList<String> tokens = getTokens(message);
        String commandName = tokens.get(0);
        String fileName = "";

        if (tokens.size() > 1)
            fileName = tokens.get(1);

        switch (commandName) {
            case "ls": {
                commandLs(channel);

                break;
            }
            case "cat": {
                commandCat(channel, fileName);

                break;
            }
            case "cd..": {
                commandCdUp(channel);

                break;
            }
            case "cd": {
                if ("..".equals(fileName)) {
                    commandCdUp(channel);

                    break;
                }

                commandCd(channel, fileName);

                break;
            }
            case "mkdir": {
                commandMkDir(channel, fileName);

                break;
            }
            case "help": {
                commandCat(channel, "rule.txt");

                break;
            }
            case "exit":
                channel.close();

                return;
            default:
                if (!commandName.isEmpty()) {
                    channel.write(wrapByteBuffer("Команда не распознана: " + message + "\n\r"));
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

    private ArrayList<String> getTokens(String message) {
        ArrayList<String> tokens = new ArrayList<>();

        String[] tmp = message.split(" ");

        for (String token: tmp) {
            if (!token.isEmpty())
                tokens.add(token);
        }

        return tokens;
    }

    private void commandLs(SocketChannel channel) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(currentDirectory)) {
            for (Path filePath : files) {
                String strFilePath = pathToString(filePath);

                channel.write(wrapByteBuffer(strFilePath));
            }
        }
    }

    private void commandCat(SocketChannel channel, String fileName) throws IOException {
        if (fileName.isEmpty()) {
            channel.write(wrapByteBuffer("Файл не задан\n\r"));
            return;
        }

        Path pathFile = currentDirectory.resolve(fileName);
        List<String> lines = Files.readAllLines(pathFile);

        for (String str : lines) {
            channel.write(wrapByteBuffer(str + "\n\r"));
        }
    }

    private void commandCdUp(SocketChannel channel) throws IOException {
        Path parentDirectory = currentDirectory.getParent();

        if (parentDirectory != null && Files.exists(parentDirectory))
            currentDirectory = parentDirectory;

        String strFilePath = pathToString(currentDirectory);
        channel.write(wrapByteBuffer(strFilePath));
    }

    private void commandCd(SocketChannel channel, String fileName) throws IOException {
        Path newDirectory;
        try {
            newDirectory = currentDirectory.resolve(fileName);
        }
        catch (Exception ex) {
            channel.write(wrapByteBuffer("Указанный путь не поддерживается: " + fileName + "\n\r"));

            return;
        }


        if (Files.exists(newDirectory)) {
            currentDirectory = newDirectory;

            String strFilePath = pathToString(currentDirectory);
            channel.write(wrapByteBuffer(strFilePath));
        }
    }

    private void commandMkDir(SocketChannel channel, String fileName) throws IOException {
        if (fileName.isEmpty()) {
            channel.write(wrapByteBuffer("Директория не задана\n\r"));
            return;
        }

        Path newDirectory = currentDirectory.resolve(fileName);

        if (!Files.exists(newDirectory)) {
            Files.createDirectory(newDirectory);

            String strFilePath = pathToString(newDirectory);
            channel.write(wrapByteBuffer("Создана директория: " + strFilePath));
        }
        else
            channel.write(wrapByteBuffer(newDirectory + " уже существует\n\r"));
    }

    private ByteBuffer wrapByteBuffer(String msg) {
        return ByteBuffer.wrap((msg).getBytes(StandardCharsets.UTF_8));
    }

    private String pathToString(Path filePath) {
        return filePath.toString() + (Files.isDirectory(filePath) ? "\\" : "") + "\n\r";
    }
}