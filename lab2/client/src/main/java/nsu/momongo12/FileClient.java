package nsu.momongo12;

import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FileClient {

    private static final int BUFFER_SIZE = 8192;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Использование: java FileClient <путь к файлу> <сервер> <порт>");
            return;
        }

        String filePathStr = args[0];
        String serverHost = args[1];
        int serverPort = Integer.parseInt(args[2]);

        Path filePath = Paths.get(filePathStr);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            System.err.println("Указанный путь не существует или не является файлом");
            return;
        }

        String fileName = filePath.getFileName().toString();
        try {
            byte[] nameBytes = fileName.getBytes("UTF-8");
            if (nameBytes.length > 4096) {
                System.err.println("Длина имени файла превышает 4096 байт в UTF-8");
                return;
            }

            long fileSize = Files.size(filePath);
            if (fileSize > (1L << 40)) { // 1 ТБ
                System.err.println("Размер файла превышает 1 ТБ");
                return;
            }

            try (Socket socket = new Socket(serverHost, serverPort);
                 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream dis = new DataInputStream(socket.getInputStream());
                 InputStream fis = new BufferedInputStream(Files.newInputStream(filePath))) {

                dos.writeInt(nameBytes.length);
                dos.write(nameBytes);
                dos.writeLong(fileSize);
                dos.flush();

                byte[] buffer = new byte[BUFFER_SIZE];
                long totalSent = 0;
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, read);
                    totalSent += read;
                }
                dos.flush();

                int status = dis.readByte();
                if (status == 1) {
                    System.out.println("Файл успешно передан на сервер.");
                } else {
                    System.err.println("Передача файла завершилась неудачей.");
                }

            }

        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        }
    }
}
