import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import java.awt.*;

// Publisher class
public class SwingPublisher extends JFrame {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID_PREFIX = "SwingPublisher";
    private static final String TOPIC = "sensor/data";

    // Initialise Java Swing UI Components
    private JTextField messageField;
    private JButton sendButton;
    private JTextArea logArea;
    private MqttClient client;
    private String clientId;

    // Constructor
    public SwingPublisher() {
        super("MQTT Publisher (Device)");

        // UI setup
        messageField = new JTextField(20);
        sendButton = new JButton("Publish");
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Message:"));
        panel.add(messageField);
        panel.add(sendButton);

        add(panel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        sendButton.addActionListener(e -> publishMessage());

        // Connect to broker
        try {
            String suffix = System.getenv("CLIENT_SUFFIX");
            if (suffix == null || suffix.isBlank()) {
                suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            clientId = CLIENT_ID_PREFIX + "-" + suffix;

            client = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            client.connect(options);
            log("Connected to broker as " + clientId);

            // Start periodic RSSI publishing
            startMockRssiPublisher();
        } catch (MqttException ex) {
            log("Error connecting: " + ex.getMessage());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (client != null && client.isConnected()) {
                    try { client.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
        pack();
        setVisible(true);
    }
    // Periodically send mock RSSI data as JSON
    private void startMockRssiPublisher() {
        // Assign random but fixed coordinates for this device
        final double x = 10 + Math.random() * 90; // e.g., 10-100
        final double y = 10 + Math.random() * 90;

        // Use a fixed RSSI value for more stable trilateration
        final double baseRssi = -60; // e.g., -60 dBm
        Timer timer = new Timer(10000, e -> {
            try {
                // Add a small random noise to RSSI
                double rssi = baseRssi + (Math.random() - 0.5) * 2; // -61 to -59
                String json = String.format(
                    "{\"deviceId\":\"%s\",\"coordinates\":{\"x\":%.2f,\"y\":%.2f},\"rssi\":%.2f}",
                    clientId, x, y, rssi
                );
                MqttMessage message = new MqttMessage(json.getBytes());
                message.setQos(1);
                client.publish(TOPIC, message);
                log("[RSSI] " + json);
            } catch (Exception ex) {
                log("Error sending RSSI: " + ex.getMessage());
            }
        });
        timer.start();
    }

    private void publishMessage() {
        try {
            String text = messageField.getText();
            if (!text.isEmpty()) {
                MqttMessage message = new MqttMessage(text.getBytes());
                message.setQos(1);
                client.publish(TOPIC, message);
                log("Published: " + text);
                messageField.setText("");
            }
        } catch (MqttException ex) {
            log("Error publishing: " + ex.getMessage());
        }
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingPublisher::new);
    }
}
