import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import java.awt.*;

public class SwingPublisher extends JFrame {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "SwingPublisher";
    private static final String TOPIC = "sensor/data";

    private JTextField messageField;
    private JButton sendButton;
    private JTextArea logArea;
    private MqttClient client;

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
            client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            client.connect();
            log("Connected to broker");
        } catch (MqttException ex) {
            log("Error connecting: " + ex.getMessage());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setVisible(true);
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
