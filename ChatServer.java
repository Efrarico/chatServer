import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static Set<String> userNames = new HashSet<>();
    private static Set<PrintWriter> clientWriters = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Servidor de chat iniciado...");
        ServerSocket listener = new ServerSocket(1090);

        try {
            while (true) {
                new ClientHandler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    private void updateConnectedUsersList() {
        String userList = "USERLIST:" + String.join(",", userNames);
        for (PrintWriter writer : clientWriters) {
            writer.println(userList);
        }
    }

    private static class ClientHandler extends Thread {
        private String userName;
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Obtener el nombre de usuario y agregarlo a la lista
                userName = in.readLine();
                synchronized (userNames) {
                    userNames.add(userName);
                    notifyUsersList();  // Notificar a todos sobre la lista actualizada
                }

                // Escuchar mensajes
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("PRIVATE:")) {
                        // Lógica de mensajes privados
                    } else {
                        // Mensajes globales
                        for (PrintWriter writer : clientWriters) {
                            writer.println("GLOBAL:" + userName + ": " + message);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error en la conexión con el usuario.");
            } finally {
                if (userName != null) {
                    synchronized (userNames) {
                        userNames.remove(userName);
                        notifyUsersList();  // Actualizar la lista de usuarios al desconectar
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private void notifyUsersList() {
            String usersList = "USERS:" + String.join(",", userNames);
            for (PrintWriter writer : clientWriters) {
                writer.println(usersList);
            }
        }
    }
}
