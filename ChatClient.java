import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;

public class ChatClient {
    private String serverAddress = "localhost";
    private int serverPort = 1090;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String userName;
    private JFrame globalChatWindow;
    private JTextArea globalChatArea;
    private JList<String> userList;

    // Usuarios conectados al chat global
    private DefaultListModel<String> connectedUsersModel = new DefaultListModel<>();

    private static final String ALGORITHM = "AES";
    private static final byte[] keyValue = "MySuperSecretKey".getBytes();  // Llave simétrica

    private Map<String, PrivateChatWindow> privateChats = new HashMap<>();

    public static void main(String[] args) {
        new ChatClient().start();
    }

    public void start() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ingreso del nombre de usuario
            userName = JOptionPane.showInputDialog(null, "Ingrese su nombre:");
            out.println(userName);

            // Configuración de la interfaz de usuario
            setupGlobalChatWindow();
            setupUserListWindow();

            // Escuchar mensajes del servidor
            new Thread(new ServerListener()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupGlobalChatWindow() {
        globalChatWindow = new JFrame("Chat Global - " + userName);
        globalChatArea = new JTextArea(20, 50);
        globalChatArea.setEditable(false);
        JTextField messageField = new JTextField(40);
        JButton sendButton = new JButton("Enviar");

        sendButton.addActionListener(e -> {
            String message = messageField.getText();
            out.println("GLOBAL:" + message);
            messageField.setText("");
        });

        globalChatWindow.setLayout(new BorderLayout());
        globalChatWindow.add(new JScrollPane(globalChatArea), BorderLayout.CENTER);
        JPanel inputPanel = new JPanel();
        inputPanel.add(messageField);
        inputPanel.add(sendButton);
        globalChatWindow.add(inputPanel, BorderLayout.SOUTH);

        globalChatWindow.pack();
        globalChatWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        globalChatWindow.setVisible(true);
    }

    private void setupUserListWindow() {
        JFrame userListWindow = new JFrame("Usuarios Conectados");
        userList = new JList<>(connectedUsersModel);
        userListWindow.add(new JScrollPane(userList));

        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (!privateChats.containsKey(selectedUser)) {
                        openPrivateChatWindow(selectedUser);
                    }
                }
            }
        });

        userListWindow.pack();
        userListWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        userListWindow.setVisible(true);
    }

    private void openPrivateChatWindow(String recipient) {
        PrivateChatWindow chatWindow = new PrivateChatWindow(recipient);
        privateChats.put(recipient, chatWindow);
        chatWindow.showWindow();
        out.println("PRIVATE_REQUEST:" + recipient);
    }

    private void closePrivateChatWindow(String user) {
        PrivateChatWindow chatWindow = privateChats.remove(user);
        if (chatWindow != null) {
            chatWindow.closeWindow();
        }
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("GLOBAL:")) {
                        globalChatArea.append(message.substring(7) + "\n");
                    } else if (message.startsWith("PRIVATE_REQUEST:")) {
                        String sender = message.split(":")[1];
                        if (!privateChats.containsKey(sender)) {
                            openPrivateChatWindow(sender);
                        }
                    } else if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);
                        String sender = parts[1];
                        String encryptedMessage = parts[2];
                        try {
                            String decryptedMessage = decrypt(encryptedMessage);
                            privateChats.get(sender).appendMessage(sender + ": " + decryptedMessage);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else if (message.startsWith("PRIVATE_FILE:")) {
                        String[] parts = message.split(":", 4);
                        String sender = parts[1];
                        String fileName = parts[2];
                        long fileSize = Long.parseLong(parts[3]);
                        receiveFile(sender, fileName, fileSize);
                    } else if (message.startsWith("USERLIST:")) {
                        updateConnectedUsersList(message.substring(9).split(","));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    

    private void updateConnectedUsersList(String[] users) {
        connectedUsersModel.clear();
        for (String user : users) {
            if (!user.equals(userName)) {
                connectedUsersModel.addElement(user);
            }
        }
    }

    // Método para encriptar un mensaje
    public static String encrypt(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encVal);
    }

    // Método para desencriptar un mensaje
    public static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedData);
        byte[] decValue = cipher.doFinal(decodedValue);
        return new String(decValue);
    }

    // Enviar archivo a otro usuario
    private void sendFileToUser(String recipient) {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile.length() <= 50 * 1024 * 1024) {  // Limitar a 50 MB
                try {
                    out.println("PRIVATE_FILE:" + recipient + ":" + selectedFile.getName() + ":" + selectedFile.length());
                    sendFile(selectedFile);
                    privateChats.get(recipient).appendMessage("Has enviado el archivo: " + selectedFile.getName() + "\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(null, "El archivo es demasiado grande. Elige un archivo de hasta 50 MB.");
            }
        }
    }

    // Enviar archivo a través de la red
    private void sendFile(File file) throws IOException {
        BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
        OutputStream socketOut = socket.getOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileIn.read(buffer)) != -1) {
            socketOut.write(buffer, 0, bytesRead);
        }
        socketOut.flush();
        fileIn.close();
    }

    // Recibir archivo desde otro usuario
    private void receiveFile(String sender, String fileName, long fileSize) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            int result = fileChooser.showSaveDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(fileToSave));
                InputStream socketIn = socket.getInputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long remaining = fileSize;

                while (remaining > 0 && (bytesRead = socketIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }

                fileOut.flush();
                fileOut.close();
                privateChats.get(sender).appendMessage("Has recibido el archivo: " + fileName + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Ventana de chat privado
    private class PrivateChatWindow {
        private String recipient;
        private JFrame privateChatWindow;
        private JTextArea privateChatArea;
        private JTextField messageField;
        private JButton sendFileButton;

        public PrivateChatWindow(String recipient) {
            this.recipient = recipient;
            privateChatWindow = new JFrame("Chat con " + recipient);
            privateChatArea = new JTextArea(20, 50);
            privateChatArea.setEditable(false);
            messageField = new JTextField(40);
            JButton sendButton = new JButton("Enviar");
            sendFileButton = new JButton("Enviar Archivo");

            sendButton.addActionListener(e -> {
                String message = messageField.getText();
                try {
                    String encryptedMessage = encrypt(message);
                    out.println("PRIVATE:" + recipient + ":" + encryptedMessage);
                    privateChatArea.append("Tú: " + message + "\n");
                    messageField.setText("");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            sendFileButton.addActionListener(e -> sendFileToUser(recipient));

            privateChatWindow.setLayout(new BorderLayout());
            privateChatWindow.add(new JScrollPane(privateChatArea), BorderLayout.CENTER);
            JPanel inputPanel = new JPanel();
            inputPanel.add(messageField);
            inputPanel.add(sendButton);
            inputPanel.add(sendFileButton);
            privateChatWindow.add(inputPanel, BorderLayout.SOUTH);
        }

        public void showWindow() {
            privateChatWindow.pack();
            privateChatWindow.setVisible(true);
        }

        public void appendMessage(String message) {
            privateChatArea.append(message + "\n");
        }

        public void closeWindow() {
            privateChatWindow.dispose();
        }
    }
}
