import java.io.*; // Importa las clases necesarias para la entrada y salida
import java.net.*; // Importa las clases necesarias para el manejo de redes
import java.util.*; // Importa las clases necesarias para trabajar con colecciones

public class ChatServer {

    // Conjuntos para almacenar escritores de clientes y nombres de usuario
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static Set<String> userNames = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("El servidor está ejecutándose..."); // Mensaje para indicar que el servidor está activo
        ServerSocket listener = new ServerSocket(1090); // Crea un socket de servidor en el puerto 1090
        try {
            while (true) {
                // Acepta nuevas conexiones de clientes y crea un nuevo manejador para cada conexión
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close(); // Cierra el socket del servidor al finalizar
        }
    }

    // Clase interna Handler para manejar la comunicación con un cliente
    private static class Handler extends Thread {
        private String userName; // Nombre de usuario del cliente
        private Socket socket; // Socket del cliente
        private PrintWriter out; // Para enviar mensajes al cliente
        private BufferedReader in; // Para recibir mensajes del cliente

        public Handler(Socket socket) {
            this.socket = socket; // Inicializa el socket del cliente
        }

        public void run() {
            try {
                // Inicializa los flujos de entrada y salida
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Pedir nombre de usuario al cliente
                userName = in.readLine();
                synchronized (userNames) {
                    // Verifica si el nombre de usuario es válido y no está en uso
                    if (userName == null || userName.trim().isEmpty() || userNames.contains(userName)) {
                        return; // Si no es válido, termina la ejecución
                    }
                    userNames.add(userName); // Agrega el nombre de usuario a la lista
                }

                // Agregar el escritor de este cliente a la lista de escritores
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                // Enviar la lista actualizada de usuarios conectados a todos los clientes
                broadcastUserList();

                // Escuchar mensajes del cliente
                String message;
                while ((message = in.readLine()) != null) {
                    // Procesar mensajes globales
                    if (message.startsWith("GLOBAL:")) {
                        broadcastMessage("GLOBAL:" + userName + ": " + message.substring(7));
                    } 
                    // Procesar mensajes privados
                    else if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);
                        String recipient = parts[1]; // Destinatario del mensaje
                        String encryptedMessage = parts[2]; // Mensaje encriptado
                        sendPrivateMessage(recipient, "PRIVATE:" + userName + ":" + encryptedMessage);
                    } 
                    // Procesar archivos privados
                    else if (message.startsWith("PRIVATE_FILE:")) {
                        String[] parts = message.split(":", 4);
                        String recipient = parts[1]; // Destinatario del archivo
                        String fileName = parts[2]; // Nombre del archivo
                        String fileSize = parts[3]; // Tamaño del archivo
                        sendPrivateMessage(recipient, "PRIVATE_FILE:" + userName + ":" + fileName + ":" + fileSize);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(); // Manejo de excepciones de entrada/salida
            } finally {
                // Al salir, limpia el usuario y los escritores
                if (userName != null) {
                    userNames.remove(userName); // Remueve el usuario de la lista
                    broadcastUserList(); // Actualiza la lista de usuarios a los demás
                }
                if (out != null) {
                    clientWriters.remove(out); // Remueve el escritor del cliente
                }
                try {
                    socket.close(); // Cierra el socket del cliente
                } catch (IOException e) {
                    // Manejo de excepciones al cerrar el socket
                }
            }
        }

        // Método para enviar un mensaje privado a un destinatario
        private void sendPrivateMessage(String recipient, String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message); // Envía el mensaje a todos los clientes
                }
            }
        }

        // Método para transmitir un mensaje a todos los clientes conectados
        private void broadcastMessage(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message); // Envía el mensaje a todos los escritores
                }
            }
        }

        // Método para enviar la lista de usuarios conectados a todos los clientes
        private void broadcastUserList() {
            String userListMessage = "USERLIST:" + String.join(",", userNames); // Crea un mensaje con la lista de usuarios
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(userListMessage); // Envía la lista de usuarios a todos los escritores
                }
            }
        }
    }
}
