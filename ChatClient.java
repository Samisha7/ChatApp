import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ChatClient extends JFrame {
    
    // --- Components ---
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JTextArea chatArea;
    private JTextField inputField;
    private JLabel currentChatLabel;
    
    // --- Networking ---
    private PrintWriter out;
    private BufferedReader in;
    private String myUsername;
    private String serverIP;

    // --- Data Structures ---
    // Stores the Chat History for every user: "Global" -> "Hello\nHi", "John" -> "Hey John\n"
    private Map<String, StringBuilder> chatHistories = new HashMap<>();
    
    // Track who we are currently looking at
    private String currentChatPartner = "Global"; // Default to Global chat

    public ChatClient() {
        super("Java Chat - Telegram Style");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Initialize Global History
        chatHistories.put("Global", new StringBuilder());

        // --- UI LAYOUT ---
        // 1. Left Panel (User List)
        userListModel = new DefaultListModel<>();
        userListModel.addElement("Global"); // Always have Global at top
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setSelectedIndex(0); // Select Global by default
        userList.setFont(new Font("Arial", Font.BOLD, 14));
        userList.setFixedCellHeight(30);
        
        JScrollPane leftScroll = new JScrollPane(userList);
        leftScroll.setPreferredSize(new Dimension(200, 0));
        leftScroll.setBorder(BorderFactory.createTitledBorder("Contacts"));

        // 2. Right Panel (Chat Area)
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Header
        currentChatLabel = new JLabel("Global Chat");
        currentChatLabel.setHorizontalAlignment(SwingConstants.CENTER);
        currentChatLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        currentChatLabel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        currentChatLabel.setOpaque(true);
        currentChatLabel.setBackground(new Color(230, 230, 250)); // Lavender
        
        // Text Area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        chatArea.setMargin(new Insets(10, 10, 10, 10));
        
        // Input Area
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JButton sendBtn = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        rightPanel.add(currentChatLabel, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        // 3. Split Pane (Combine Left and Right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightPanel);
        splitPane.setDividerLocation(200);
        add(splitPane);

        // --- LISTENERS ---

        // A. Switching Chats (Clicking a user)
        userList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    String selected = userList.getSelectedValue();
                    if (selected != null) {
                        currentChatPartner = selected;
                        currentChatLabel.setText("Chat with: " + selected);
                        
                        // Create history buffer if it doesn't exist yet
                        chatHistories.putIfAbsent(selected, new StringBuilder());
                        
                        // Load the history into the visible text area
                        chatArea.setText(chatHistories.get(selected).toString());
                    }
                }
            }
        });

        // B. Sending Messages
        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        };
        inputField.addActionListener(sendAction);
        sendBtn.addActionListener(sendAction);
    }

    private void sendMessage() {
        String msg = inputField.getText();
        if (msg == null || msg.trim().isEmpty()) return;

        // Logic 1: Send to Global
        if (currentChatPartner.equals("Global")) {
            out.println(msg); // Server broadcasts this
            // Note: We don't append locally here because the server will echo it back to us
        } 
        // Logic 2: Send Private
        else {
            // Protocol: /private <user> <message>
            out.println("/private " + currentChatPartner + " " + msg);
            
            // For private messages, we usually want to see what we sent immediately
            // But my Server code sends back a confirmation "[Private to Bob]: msg"
            // So we will wait for that confirmation to append it to the chat.
        }
        inputField.setText("");
    }

    // --- LOGIC TO HANDLE INCOMING MESSAGES ---
    
    // Helper to append message to the correct history storage
    private void appendToHistory(String userCategory, String message) {
        // 1. Ensure storage exists
        chatHistories.putIfAbsent(userCategory, new StringBuilder());
        
        // 2. Add message to memory
        chatHistories.get(userCategory).append(message).append("\n");
        
        // 3. If we are currently looking at this user, update the screen immediately
        if (currentChatPartner.equals(userCategory)) {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    public void start() {
        try {
            serverIP = JOptionPane.showInputDialog(this, "Enter Server IP:", "localhost");
            if (serverIP == null) serverIP = "localhost";
            myUsername = JOptionPane.showInputDialog(this, "Enter Username:", "User");
            if (myUsername == null || myUsername.trim().isEmpty()) myUsername = "Guest";
            myUsername = myUsername.trim().replace(" ", "_");

            setTitle("Chat App - Logged in as: " + myUsername);

            Socket socket = new Socket(serverIP, 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(myUsername); // Send login name

            String line;
            while ((line = in.readLine()) != null) {
                
                // 1. User List Update
                if (line.startsWith("UPDATEUSERS:")) {
                    updateUserList(line);
                } 
                // 2. Incoming Private Message (From someone else)
                else if (line.startsWith("[Private from ")) {
                    // Format: [Private from Bob]: Hello
                    String sender = line.substring(14, line.indexOf("]:"));
                    String content = line.substring(line.indexOf("]:") + 2);
                    
                    // Add to Bob's history
                    appendToHistory(sender, sender + ": " + content);
                }
                // 3. Outgoing Private Confirmation (From Me)
                else if (line.startsWith("[Private to ")) {
                    // Format: [Private to Bob]: Hello
                    String target = line.substring(12, line.indexOf("]:"));
                    String content = line.substring(line.indexOf("]:") + 2);
                    
                    // Add to Bob's history (so I can see what I sent him)
                    appendToHistory(target, "Me: " + content);
                }
                // 4. Server System Message
                else if (line.startsWith("Server:")) {
                    appendToHistory("Global", line);
                }
                // 5. Global Chat Message
                else {
                    // It's a global message. 
                    // However, we need to distinguish if it's really global or a server error.
                    // Usually format is "User: message"
                    appendToHistory("Global", line);
                }
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connection Error: " + e.getMessage());
            System.exit(0);
        }
    }

    private void updateUserList(final String msg) {
        SwingUtilities.invokeLater(() -> {
            String currentSelection = userList.getSelectedValue();
            userListModel.clear();
            userListModel.addElement("Global"); // Always first

            String[] users = msg.substring(12).split(",");
            for (String u : users) {
                if (!u.isEmpty() && !u.equals(myUsername)) {
                    userListModel.addElement(u);
                }
            }

            // Restore selection if possible
            if (currentSelection != null && userListModel.contains(currentSelection)) {
                userList.setSelectedValue(currentSelection, true);
            } else {
                userList.setSelectedIndex(0); // Default to Global
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
            new Thread(client::start).start();
        });
    }
}