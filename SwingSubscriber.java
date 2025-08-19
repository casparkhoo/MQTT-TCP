import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import java.awt.*;

public class SwingSubscriber extends JFrame {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "SwingSubscriber";
    private static final String TOPIC = "sensor/data";

    private JTextArea logArea;
    private MqttClient client;

    public SwingSubscriber() {
        super("MQTT Subscriber (Gateway)");

        // UI setup
        logArea = new JTextArea(15, 30);
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Connect to broker
        try {
            client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log("Connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    log("Received [" + topic + "]: " + new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not needed for subscriber
                }
            });

            client.connect();
            client.subscribe(TOPIC, 1);
            log("Subscribed to " + TOPIC);

        } catch (MqttException ex) {
            log("Error: " + ex.getMessage());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setVisible(true);
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingSubscriber::new);
    }
}
