import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class chatServer {
    private static final int PORT = 12345;
    private static ConcurrentHashMap<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== Server Started on Port " + PORT + " ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String rawName = in.readLine();
                if (rawName == null || rawName.trim().isEmpty()) return;
                username = rawName.trim().replace(" ", "_"); // Force no spaces

                synchronized (connectedClients) {
                    if (connectedClients.containsKey(username)) username += "_" + (int)(Math.random()*100);
                    connectedClients.put(username, out);
                }

                System.out.println("Connected: " + username);
                broadcast("Server: " + username + " joined.", null);
                broadcastUserList();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/private ")) {
                        handlePrivateMessage(message);
                    } else {
                        broadcast(username + ": " + message, username); // Broadcast global
                    }
                }
            } catch (IOException e) {
            } finally {
                if (username != null) {
                    connectedClients.remove(username);
                    broadcast("Server: " + username + " left.", null);
                    broadcastUserList();
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void handlePrivateMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length == 3) {
                String target = parts[1];
                String content = parts[2];
                PrintWriter targetOut = connectedClients.get(target);
                if (targetOut != null) {
                    targetOut.println("[Private from " + username + "]: " + content);
                    out.println("[Private to " + target + "]: " + content);
                } else {
                    out.println("Server: User " + target + " not found.");
                }
            }
        }
    }

    private static void broadcast(String msg, String exclude) {
        for (PrintWriter w : connectedClients.values()) w.println(msg);
    }

    private static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("UPDATEUSERS:");
        for (String u : connectedClients.keySet()) sb.append(u).append(",");
        for (PrintWriter w : connectedClients.values()) w.println(sb.toString());
    }
}