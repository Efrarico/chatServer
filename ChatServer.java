import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {

    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static Set<String> userNames = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("El servidor está ejecutándose...");
        ServerSocket listener = new ServerSocket(1090);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    private static class Handler extends Thread {
        private String userName;
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Pedir nombre de usuario
                userName = in.readLine();
                synchronized (userNames) {
                    if (userName == null || userName.trim().isEmpty() || userNames.contains(userName)) {
                        return;
                    }
                    userNames.add(userName);
                }

                // Agregar el escritor de este cliente a la lista
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                // Enviar la lista actualizada de usuarios a todos
                broadcastUserList();

                // Escuchar mensajes del cliente
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("GLOBAL:")) {
                        broadcastMessage("GLOBAL:" + userName + ": " + message.substring(7));
                    } else if (message.startsWith("PRIVATE:")) {
                        // Procesar mensaje privado
                        String[] parts = message.split(":", 3);
                        String recipient = parts[1];
                        String encryptedMessage = parts[2];
                        sendPrivateMessage(recipient, "PRIVATE:" + userName + ":" + encryptedMessage);
                    } else if (message.startsWith("PRIVATE_FILE:")) {
                        String[] parts = message.split(":", 4);
                        String recipient = parts[1];
                        String fileName = parts[2];
                        String fileSize = parts[3];
                        sendPrivateMessage(recipient, "PRIVATE_FILE:" + userName + ":" + fileName + ":" + fileSize);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (userName != null) {
                    userNames.remove(userName);
                    broadcastUserList();
                }
                if (out != null) {
                    clientWriters.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private void sendPrivateMessage(String recipient, String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }

        private void broadcastMessage(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }

        // Método para enviar la lista de usuarios conectados a todos los clientes
        private void broadcastUserList() {
            String userListMessage = "USERLIST:" + String.join(",", userNames);
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(userListMessage);
                }
            }
        }
    }
}
