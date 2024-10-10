import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;

public class ChatClient {
    private String serverAddress = "localhost"; // Dirección del servidor
    private int serverPort = 1090; // Puerto del servidor
    private Socket socket; // Socket para la conexión
    private PrintWriter out; // Para enviar mensajes al servidor
    private BufferedReader in; // Para recibir mensajes del servidor
    private String userName; // Nombre del usuario
    private JFrame globalChatWindow; // Ventana del chat global
    private JTextArea globalChatArea; // Área de texto para el chat global
    private JList<String> userList; // Lista de usuarios conectados

    // Usuarios conectados al chat global
    private DefaultListModel<String> connectedUsersModel = new DefaultListModel<>(); // Modelo de lista para usuarios conectados

    private static final String ALGORITHM = "AES"; // Algoritmo de cifrado
    private static final byte[] keyValue = "MySuperSecretKey".getBytes();  // Llave simétrica

    private Map<String, PrivateChatWindow> privateChats = new HashMap<>(); // Mapa para gestionar ventanas de chat privadas

    public static void main(String[] args) {
        new ChatClient().start(); // Inicia el cliente de chat
    }

    public void start() {
        try {
            socket = new Socket(serverAddress, serverPort); // Establece la conexión al servidor
            out = new PrintWriter(socket.getOutputStream(), true); // Inicializa el PrintWriter para enviar mensajes
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Inicializa el BufferedReader para recibir mensajes

            // Ingreso del nombre de usuario
            userName = JOptionPane.showInputDialog(null, "Ingrese su nombre:"); // Solicita el nombre de usuario
            out.println(userName); // Envía el nombre al servidor

            // Configuración de la interfaz de usuario
            setupGlobalChatWindow(); // Configura la ventana del chat global
            setupUserListWindow(); // Configura la ventana de lista de usuarios

            // Escuchar mensajes del servidor
            new Thread(new ServerListener()).start(); // Inicia un hilo para escuchar mensajes del servidor

        } catch (IOException e) {
            e.printStackTrace(); // Manejo de excepciones
        }
    }

    private void setupGlobalChatWindow() {
        globalChatWindow = new JFrame("Chat Global - " + userName); // Crea la ventana del chat global
        globalChatArea = new JTextArea(20, 50); // Área de texto para mostrar mensajes
        globalChatArea.setEditable(false); // El área de texto no es editable
        JTextField messageField = new JTextField(40); // Campo de texto para ingresar mensajes
        JButton sendButton = new JButton("Enviar"); // Botón para enviar mensajes

        // Acción al presionar el botón de enviar
        sendButton.addActionListener(e -> {
            String message = messageField.getText(); // Obtiene el mensaje ingresado
            out.println("GLOBAL:" + message); // Envía el mensaje al servidor
            messageField.setText(""); // Limpia el campo de texto
        });

        globalChatWindow.setLayout(new BorderLayout()); // Configura el layout de la ventana
        globalChatWindow.add(new JScrollPane(globalChatArea), BorderLayout.CENTER); // Agrega el área de texto al centro
        JPanel inputPanel = new JPanel(); // Panel para el campo de texto y botón
        inputPanel.add(messageField); // Agrega el campo de texto
        inputPanel.add(sendButton); // Agrega el botón
        globalChatWindow.add(inputPanel, BorderLayout.SOUTH); // Agrega el panel al sur

        globalChatWindow.pack(); // Ajusta el tamaño de la ventana
        globalChatWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Cierra la aplicación al cerrar la ventana
        globalChatWindow.setVisible(true); // Muestra la ventana
    }

    private void setupUserListWindow() {
        JFrame userListWindow = new JFrame("Usuarios Conectados"); // Crea la ventana de lista de usuarios
        userList = new JList<>(connectedUsersModel); // Crea la lista de usuarios conectados
        userListWindow.add(new JScrollPane(userList)); // Agrega la lista a la ventana

        // Manejo de eventos de clic en la lista de usuarios
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Verifica si se hizo doble clic
                    String selectedUser = userList.getSelectedValue(); // Obtiene el usuario seleccionado
                    if (!privateChats.containsKey(selectedUser)) { // Verifica si ya hay un chat privado
                        openPrivateChatWindow(selectedUser); // Abre la ventana de chat privado
                    }
                }
            }
        });

        userListWindow.pack(); // Ajusta el tamaño de la ventana
        userListWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Cierra la aplicación al cerrar la ventana
        userListWindow.setVisible(true); // Muestra la ventana
    }

    private void openPrivateChatWindow(String recipient) {
        PrivateChatWindow chatWindow = new PrivateChatWindow(recipient); // Crea una nueva ventana de chat privado
        privateChats.put(recipient, chatWindow); // Almacena la ventana de chat privado
        chatWindow.showWindow(); // Muestra la ventana de chat privado
        out.println("PRIVATE_REQUEST:" + recipient); // Solicita al servidor iniciar un chat privado
    }

    private void closePrivateChatWindow(String user) {
        PrivateChatWindow chatWindow = privateChats.remove(user); // Cierra la ventana de chat privado
        if (chatWindow != null) {
            chatWindow.closeWindow(); // Cierra la ventana si existe
        }
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                // Escucha mensajes del servidor
                while ((message = in.readLine()) != null) { // Lee mensajes hasta que se cierre la conexión
                    if (message.startsWith("GLOBAL:")) { // Si el mensaje es global
                        globalChatArea.append(message.substring(7) + "\n"); // Muestra el mensaje en el área de chat global
                    } else if (message.startsWith("PRIVATE_REQUEST:")) { // Si hay una solicitud de chat privado
                        String sender = message.split(":")[1]; // Obtiene el remitente
                        if (!privateChats.containsKey(sender)) { // Verifica si ya hay un chat privado
                            openPrivateChatWindow(sender); // Abre la ventana de chat privado
                        }
                    } else if (message.startsWith("PRIVATE:")) { // Si el mensaje es privado
                        String[] parts = message.split(":", 3); // Divide el mensaje en partes
                        String sender = parts[1]; // Obtiene el remitente
                        String encryptedMessage = parts[2]; // Obtiene el mensaje encriptado
                        try {
                            String decryptedMessage = decrypt(encryptedMessage); // Desencripta el mensaje
                            privateChats.get(sender).appendMessage(sender + ": " + decryptedMessage); // Muestra el mensaje desencriptado en la ventana de chat privado
                        } catch (Exception ex) {
                            ex.printStackTrace(); // Manejo de excepciones
                        }
                    } else if (message.startsWith("PRIVATE_FILE:")) { // Si hay un archivo privado
                        String[] parts = message.split(":", 4); // Divide el mensaje en partes
                        String sender = parts[1]; // Obtiene el remitente
                        String fileName = parts[2]; // Obtiene el nombre del archivo
                        long fileSize = Long.parseLong(parts[3]); // Obtiene el tamaño del archivo
                        receiveFile(sender, fileName, fileSize); // Llama al método para recibir el archivo
                    } else if (message.startsWith("USERLIST:")) { // Si hay una lista de usuarios
                        updateConnectedUsersList(message.substring(9).split(",")); // Actualiza la lista de usuarios conectados
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(); // Manejo de excepciones
            }
        }
    }

    private void updateConnectedUsersList(String[] users) {
        connectedUsersModel.clear(); // Limpia la lista de usuarios conectados
        for (String user : users) {
            if (!user.equals(userName)) { // Excluye al usuario actual de la lista
                connectedUsersModel.addElement(user); // Agrega el usuario a la lista
            }
        }
    }

    // Método para encriptar un mensaje
    public static String encrypt(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM); // Crea la clave para encriptar
        Cipher cipher = Cipher.getInstance(ALGORITHM); // Inicializa el cifrador
        cipher.init(Cipher.ENCRYPT_MODE, key); // Modo de encriptación
        byte[] encVal = cipher.doFinal(data.getBytes()); // Encripta el mensaje
        return Base64.getEncoder().encodeToString(encVal); // Devuelve el mensaje encriptado en formato Base64
    }

    // Método para desencriptar un mensaje
    public static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM); // Crea la clave para desencriptar
        Cipher cipher = Cipher.getInstance(ALGORITHM); // Inicializa el cifrador
        cipher.init(Cipher.DECRYPT_MODE, key); // Modo de desencriptación
        byte[] decodedValue = Base64.getDecoder().decode(encryptedData); // Decodifica el mensaje encriptado
        byte[] decValue = cipher.doFinal(decodedValue); // Desencripta el mensaje
        return new String(decValue); // Devuelve el mensaje desencriptado
    }

    // Enviar archivo a otro usuario
    private void sendFileToUser(String recipient) {
        JFileChooser fileChooser = new JFileChooser(); // Crea un selector de archivos
        int result = fileChooser.showOpenDialog(null); // Muestra el selector de archivos
        if (result == JFileChooser.APPROVE_OPTION) { // Si se seleccionó un archivo
            File selectedFile = fileChooser.getSelectedFile(); // Obtiene el archivo seleccionado
            if (selectedFile.length() <= 50 * 1024 * 1024) {  // Limitar a 50 MB
                try {
                    out.println("PRIVATE_FILE:" + recipient + ":" + selectedFile.getName() + ":" + selectedFile.length()); // Envía la información del archivo al servidor
                    new Thread(() -> { // Inicia un nuevo hilo para enviar el archivo
                        try {
                            sendFile(selectedFile); // Llama al método para enviar el archivo
                            privateChats.get(recipient).appendMessage("Has enviado el archivo: " + selectedFile.getName() + "\n"); // Muestra un mensaje de confirmación en el chat privado
                        } catch (IOException e) {
                            e.printStackTrace(); // Manejo de excepciones
                        }
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace(); // Manejo de excepciones
                }
            } else {
                JOptionPane.showMessageDialog(null, "El archivo es demasiado grande (máx. 50 MB)."); // Mensaje de error si el archivo es demasiado grande
            }
        }
    }

    // Enviar archivo a través de la red
private void sendFile(File file) throws IOException {
    byte[] buffer = new byte[8192];  // Usamos un buffer de 8KB para optimizar la transferencia
    long totalBytesSent = 0;
    long fileSize = file.length();

    try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
         DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())) {
        int bytesRead;
        
        // Enviar primero el tamaño del archivo
        dataOut.writeLong(fileSize);
        
        // Enviar el contenido del archivo
        while ((bytesRead = fileIn.read(buffer)) != -1) {
            dataOut.write(buffer, 0, bytesRead);
            totalBytesSent += bytesRead;

            // Mostrar progreso en consola (opcional)
            System.out.println("Progreso de envío: " + (100 * totalBytesSent / fileSize) + "%");
        }
        dataOut.flush();
        System.out.println("Archivo enviado completamente.");
    } catch (IOException e) {
        e.printStackTrace();
    }
}


    // Método para recibir un archivo
    private void receiveFile(String sender, String fileName, long fileSize) {
        // Incrementar el tamaño del buffer a 64KB
        byte[] buffer = new byte[65536];  // 64 KB buffer
        
        try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream("downloads/" + fileName)); // Inicializa el flujo de salida para guardar el archivo
             DataInputStream dataIn = new DataInputStream(socket.getInputStream())) { // Inicializa el flujo de entrada de datos
            long totalBytesRead = 0; // Total de bytes leídos
            int bytesRead;
            // Lee el archivo en bloques
            while (totalBytesRead < fileSize && (bytesRead = dataIn.read(buffer)) > 0) {
                fileOut.write(buffer, 0, bytesRead); // Escribe el buffer al flujo de salida
                totalBytesRead += bytesRead; // Actualiza el total de bytes leídos
                // Imprimir progreso de la recepción (opcional)
                System.out.println("Progreso de recepción: " + (100 * totalBytesRead / fileSize) + "%"); // Muestra el progreso de la recepción
            }
            fileOut.flush(); // Asegura que todos los datos se guarden
            System.out.println("Archivo recibido con éxito: " + fileName); // Mensaje de éxito
            
            // Mostrar el archivo recibido en el chat con un botón para abrirlo
            JButton openFileButton = new JButton("Abrir archivo: " + fileName); // Botón para abrir el archivo
            openFileButton.addActionListener(e -> { // Manejo de clic en el botón
                try {
                    Desktop.getDesktop().open(new File("downloads/" + fileName)); // Abre el archivo
                } catch (IOException ex) {
                    ex.printStackTrace(); // Manejo de excepciones
                }
            });
            privateChats.get(sender).appendMessage("Has recibido el archivo: " + fileName + "\n"); // Muestra un mensaje en el chat privado
            privateChats.get(sender).addFileButton(openFileButton); // Agrega el botón para abrir el archivo al chat privado
        } catch (IOException e) {
            e.printStackTrace(); // Manejo de excepciones
        }
    }

    // Clase de la ventana del chat privado
    private class PrivateChatWindow {
        private String recipient; // Usuario destinatario
        private JFrame chatWindow; // Ventana del chat privado
        private JTextArea chatArea; // Área de texto para mostrar mensajes
        private JTextField messageField; // Campo de texto para ingresar mensajes
        private JButton sendButton; // Botón para enviar mensajes
        private JButton sendFileButton; // Botón para enviar archivos
        private JPanel chatPanel; // Panel para organizar componentes de la ventana

        public PrivateChatWindow(String recipient) {
            this.recipient = recipient; // Inicializa el destinatario
            chatWindow = new JFrame("Chat privado con " + recipient); // Crea la ventana del chat privado
            chatArea = new JTextArea(20, 50); // Área de texto para mostrar mensajes
            chatArea.setEditable(false); // El área de texto no es editable
            messageField = new JTextField(40); // Campo de texto para ingresar mensajes
            sendButton = new JButton("Enviar"); // Botón para enviar mensajes
            sendFileButton = new JButton("Enviar Archivo"); // Botón para enviar archivos
            chatPanel = new JPanel(); // Crea un panel para organizar los componentes
            chatPanel.setLayout(new BorderLayout()); // Establece el layout del panel

            // Acción al presionar el botón de enviar
            sendButton.addActionListener(e -> sendMessage());
            sendFileButton.addActionListener(e -> sendFileToUser(recipient)); // Acción para enviar archivos

            // Panel de entrada para mensajes
            JPanel inputPanel = new JPanel();
            inputPanel.add(messageField); // Agrega el campo de texto
            inputPanel.add(sendButton); // Agrega el botón de enviar
            inputPanel.add(sendFileButton); // Agrega el botón de enviar archivo

            chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER); // Agrega el área de texto al panel
            chatPanel.add(inputPanel, BorderLayout.SOUTH); // Agrega el panel de entrada al sur

            chatWindow.setLayout(new BorderLayout()); // Establece el layout de la ventana
            chatWindow.add(chatPanel, BorderLayout.CENTER); // Agrega el panel al centro
            chatWindow.pack(); // Ajusta el tamaño de la ventana
        }

        public void showWindow() {
            chatWindow.setVisible(true); // Muestra la ventana de chat privado
        }

        public void closeWindow() {
            chatWindow.setVisible(false); // Cierra la ventana
            chatWindow.dispose(); // Libera recursos
        }

        public void appendMessage(String message) {
            chatArea.append(message + "\n"); // Agrega un mensaje al área de texto
        }

        public void addFileButton(JButton fileButton) {
            chatPanel.add(fileButton, BorderLayout.NORTH); // Agrega el botón de archivo al panel
            chatWindow.revalidate(); // Revalida el panel
            chatWindow.repaint(); // Repinta la ventana
        }

        private void sendMessage() {
            try {
                String message = messageField.getText(); // Obtiene el mensaje ingresado
                String encryptedMessage = encrypt(message); // Encripta el mensaje
                out.println("PRIVATE:" + recipient + ":" + encryptedMessage); // Envía el mensaje encriptado
                appendMessage("Tú: " + message); // Muestra el mensaje enviado en el área de texto
                messageField.setText(""); // Limpia el campo de texto
            } catch (Exception e) {
                e.printStackTrace(); // Manejo de excepciones
            }
        }
    }
}
