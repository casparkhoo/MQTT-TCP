    // ...existing code...
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
// import org.json.JSONObject;

public class SwingSubscriber extends JFrame {
    // LogPacket class for expandable log entries
    private static class LogPacket {
        String deviceId;
        double x, y, rssi;
        String payload;
        boolean expanded = false;
        boolean isSimpleMsg = false;
        String msg;
        LogPacket(String deviceId, double x, double y, double rssi, String payload) {
            this.deviceId = deviceId;
            this.x = x;
            this.y = y;
            this.rssi = rssi;
            this.payload = payload;
        }
        LogPacket(String msg) {
            this.isSimpleMsg = true;
            this.msg = msg;
        }
        @Override
        public String toString() {
            if (isSimpleMsg) return msg;
            if (!expanded) {
                return String.format("[%s] RSSI: %.2f, (x=%.2f, y=%.2f)", deviceId, rssi, x, y);
            } else {
                return String.format("[%s] RSSI: %.2f, (x=%.2f, y=%.2f)\n%s", deviceId, rssi, x, y, payload);
            }
        }
    }

    // Custom renderer for expandable log packets
    private static class LogPacketRenderer extends JTextArea implements ListCellRenderer<LogPacket> {
        public LogPacketRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends LogPacket> list, LogPacket value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value.toString());
            setBackground(isSelected ? Color.LIGHT_GRAY : Color.WHITE);
            setForeground(Color.BLACK);
            return this;
        }
    }
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID_PREFIX = "SwingSubscriber";
    private static final String TOPIC = "sensor/data";


    private DefaultListModel<LogPacket> logModel;
    private JList<LogPacket> logList;
    private MqttClient client;
    private String clientId;

    // Store latest RSSI and coordinates for each device
    private final Map<String, DeviceReading> deviceReadings = new HashMap<>();



    public SwingSubscriber() {
        super("MQTT Subscriber (Gateway)");

        // UI setup
        logModel = new DefaultListModel<>();
        logList = new JList<>(logModel);
        logList.setCellRenderer(new LogPacketRenderer());
        logList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = logList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    LogPacket pkt = logModel.get(idx);
                    pkt.expanded = !pkt.expanded;
                    logModel.set(idx, pkt); // trigger repaint
                }
            }
        });
        JScrollPane logScroll = new JScrollPane(logList);
        logScroll.setPreferredSize(new Dimension(350, 300));
        TrilaterationPanel trilatPanel = new TrilaterationPanel();
        trilatPanel.setPreferredSize(new Dimension(300, 300));
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(logScroll, BorderLayout.CENTER);
        mainPanel.add(trilatPanel, BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);

        // Connect to broker
        try {
            String suffix = System.getenv("CLIENT_SUFFIX");
            if (suffix == null || suffix.isBlank()) {
                suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            clientId = CLIENT_ID_PREFIX + "-" + suffix;


            client = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    SwingSubscriber.this.log("Connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    SwingSubscriber.this.log("Received [" + topic + "]: " + payload);
                    SwingSubscriber.this.handleRssiMessage(payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not needed for subscriber
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            client.connect(options);
            client.subscribe(TOPIC, 1);
            log("Connected as " + clientId + "; Subscribed to " + TOPIC);

        } catch (MqttException ex) {
            log("Error: " + ex.getMessage());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Graceful disconnect on window close
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

    // Log helper
    private void logPacket(String deviceId, double x, double y, double rssi, String payload) {
        logModel.addElement(new LogPacket(deviceId, x, y, rssi, payload));
        logList.ensureIndexIsVisible(logModel.size() - 1);
    }

    private void log(String msg) {
        logModel.addElement(new LogPacket(msg));
        logList.ensureIndexIsVisible(logModel.size() - 1);
    }

    // Helper class for device readings
    private static class DeviceReading {
        double x, y, distance;
        DeviceReading(double x, double y, double distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    // Visualization panel for device and estimated positions
    private class TrilaterationPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();
            g2.setColor(Color.BLUE);
            int r = 8;
            for (DeviceReading dr : deviceReadings.values()) {
                int dx = (int) (dr.x / 100.0 * (w - 2 * r)) + r;
                int dy = (int) (dr.y / 100.0 * (h - 2 * r)) + r;
                g2.fillOval(dx - r, dy - r, 2 * r, 2 * r);
                g2.drawString(String.format("(%.1f,%.1f)", dr.x, dr.y), dx + 5, dy - 5);
            }
            if (deviceReadings.size() >= 3) {
                java.util.List<DeviceReading> readings = new java.util.ArrayList<>(deviceReadings.values());
                if (readings.size() > 3) readings = readings.subList(0, 3);
                double[] pos = trilaterate(readings.get(0), readings.get(1), readings.get(2));
                if (!Double.isNaN(pos[0]) && !Double.isNaN(pos[1])) {
                    int ex = (int) (pos[0] / 100.0 * (w - 2 * r)) + r;
                    int ey = (int) (pos[1] / 100.0 * (h - 2 * r)) + r;
                    g2.setColor(Color.RED);
                    g2.fillOval(ex - r, ey - r, 2 * r, 2 * r);
                    g2.drawString(String.format("Est: (%.1f,%.1f)", pos[0], pos[1]), ex + 5, ey - 5);
                }
            }
        }
    }
    // ...existing code...

    // Parse JSON, store device readings, and attempt trilateration
    private void handleRssiMessage(String payload) {
        try {
            String deviceId = SimpleJsonParser.getString(payload, "deviceId");
            double x = SimpleJsonParser.getCoord(payload, "x");
            double y = SimpleJsonParser.getCoord(payload, "y");
            double rssi = SimpleJsonParser.getDouble(payload, "rssi");
            // Convert RSSI to distance (simple model: d = 10^((A - RSSI)/(10*n)))
            double A = -40;
            double n = 2.0;
            double distance = Math.pow(10, (A - rssi) / (10 * n));
            deviceReadings.put(deviceId, new DeviceReading(x, y, distance));
            logPacket(deviceId, x, y, rssi, payload);

            if (deviceReadings.size() >= 3) {
                List<DeviceReading> readings = new java.util.ArrayList<>(deviceReadings.values());
                if (readings.size() > 3) {
                    readings = readings.subList(0, 3);
                }
                double[] pos = trilaterate(readings.get(0), readings.get(1), readings.get(2));
                log(String.format("[Trilateration] Estimated position: (%.2f, %.2f)", pos[0], pos[1]));
                // Repaint visualization
                for (Component c : getContentPane().getComponents()) {
                    if (c instanceof JPanel) {
                        for (Component cc : ((JPanel)c).getComponents()) {
                            if (cc instanceof TrilaterationPanel) {
                                cc.repaint();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log("Error parsing RSSI message: " + ex.getMessage());
        }
    }

    // Trilateration for 2D (three circles)
    private double[] trilaterate(DeviceReading d1, DeviceReading d2, DeviceReading d3) {
        double x1 = d1.x, y1 = d1.y, r1 = d1.distance;
        double x2 = d2.x, y2 = d2.y, r2 = d2.distance;
        double x3 = d3.x, y3 = d3.y, r3 = d3.distance;

        double A = 2 * (x2 - x1);
        double B = 2 * (y2 - y1);
        double C = r1 * r1 - r2 * r2 - x1 * x1 + x2 * x2 - y1 * y1 + y2 * y2;
        double D = 2 * (x3 - x2);
        double E = 2 * (y3 - y2);
        double F = r2 * r2 - r3 * r3 - x2 * x2 + x3 * x3 - y2 * y2 + y3 * y3;

        double denominator = (A * E - B * D);
        if (Math.abs(denominator) < 1e-6) {
            return new double[]{Double.NaN, Double.NaN}; // Degenerate case
        }
        double x = (C * E - F * B) / denominator;
        double y = (A * F - D * C) / denominator;
        return new double[]{x, y};
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SwingSubscriber());
    }
}
// ...existing code...
