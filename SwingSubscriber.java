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
        long timestamp;
        LogPacket(String deviceId, double x, double y, double rssi, String payload) {
            this.deviceId = deviceId;
            this.x = x;
            this.y = y;
            this.rssi = rssi;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }
        LogPacket(String msg) {
            this.isSimpleMsg = true;
            this.msg = msg;
            this.timestamp = System.currentTimeMillis();
        }
        @Override
        public String toString() {
            if (isSimpleMsg) return msg;
            if (!expanded) {
                return String.format("[%s] RSSI: %.2f", deviceId, rssi);
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String time = sdf.format(new java.util.Date(timestamp));
                return String.format(
                    "[%s]\n  RSSI: %.2f\n  (x=%.2f, y=%.2f)\n  Time: %s\n  JSON: %s",
                    deviceId, rssi, x, y, time, payload
                );
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
    trilatPanel.setPreferredSize(new Dimension(400, 400));
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
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            int r = 8; // Node size
            int pad = 2 * r; // Minimal padding
            Font origFont = g2.getFont();
            Font smallFont = origFont.deriveFont(origFont.getSize2D() * 0.8f);
            // Grid settings for -150 to 150
            double minCoord = -150, maxCoord = 150;
            int gridStep = 10;
            // Draw grid lines
            g2.setColor(new Color(230, 230, 230));
            for (int i = (int) minCoord; i <= (int) maxCoord; i += gridStep) {
                int gx = (int)((i - minCoord) / (maxCoord - minCoord) * (w - 2 * pad)) + pad;
                g2.drawLine(gx, pad, gx, h - pad);
                int gy = (int)((maxCoord - i) / (maxCoord - minCoord) * (h - 2 * pad)) + pad;
                g2.drawLine(pad, gy, w - pad, gy);
                if (i % 50 == 0) {
                    g2.setFont(smallFont);
                    g2.setColor(Color.GRAY);
                    g2.drawString(Integer.toString(i), gx + 2, h - pad + 12);
                    g2.drawString(Integer.toString(i), 2, gy - 2);
                    g2.setColor(new Color(230, 230, 230));
                }
            }
            g2.setFont(origFont);
            // Draw publishers as squares and collect their positions
            java.util.List<int[]> pubPoints = new java.util.ArrayList<>();
            for (Map.Entry<String, DeviceReading> entry : deviceReadings.entrySet()) {
                DeviceReading dr = entry.getValue();
                int dx = (int)((dr.x - minCoord) / (maxCoord - minCoord) * (w - 2 * pad)) + pad;
                int dy = (int)((maxCoord - dr.y) / (maxCoord - minCoord) * (h - 2 * pad)) + pad;
                pubPoints.add(new int[]{dx, dy});
                g2.setColor(Color.BLUE);
                g2.fillRect(dx - r, dy - r, 2 * r, 2 * r);
                g2.setColor(Color.BLACK);
                g2.setFont(smallFont);
                // Show only the suffix (after last '-') as the label
                String id = entry.getKey();
                int dashIdx = id.lastIndexOf('-');
                String label = (dashIdx >= 0 && dashIdx < id.length() - 1) ? id.substring(dashIdx + 1) : id;
                g2.drawString(label, dx + 10, dy - 16);
                g2.drawString(String.format("(%.1f,%.1f)", dr.x, dr.y), dx + 10, dy - 2);
                g2.setFont(origFont);
            }
            // Draw estimated receiver position as square and faint dotted lines
            if (deviceReadings.size() >= 3) {
                java.util.List<DeviceReading> readings = new java.util.ArrayList<>(deviceReadings.values());
                if (readings.size() > 3) readings = readings.subList(0, 3);
                double[] pos = trilaterate(readings.get(0), readings.get(1), readings.get(2));
                if (!Double.isNaN(pos[0]) && !Double.isNaN(pos[1])) {
                    int ex = (int)((pos[0] - minCoord) / (maxCoord - minCoord) * (w - 2 * pad)) + pad;
                    int ey = (int)((maxCoord - pos[1]) / (maxCoord - minCoord) * (h - 2 * pad)) + pad;
                    // Draw faint dotted lines from publishers to receiver
                    java.awt.Stroke oldStroke = g2.getStroke();
                    g2.setColor(new Color(150, 150, 150, 100));
                    float[] dash = {4f, 6f};
                    g2.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_BEVEL, 0, dash, 0));
                    for (int[] pub : pubPoints) {
                        g2.drawLine(pub[0], pub[1], ex, ey);
                    }
                    g2.setStroke(oldStroke);
                    // Draw receiver as square
                    g2.setColor(Color.RED);
                    g2.fillRect(ex - r, ey - r, 2 * r, 2 * r);
                    g2.setColor(Color.MAGENTA);
                    g2.setFont(smallFont);
                    g2.drawString("Receiver", ex + 10, ey - 16);
                    g2.drawString(String.format("(%.1f,%.1f)", pos[0], pos[1]), ex + 10, ey - 2);
                    g2.setFont(origFont);
                }
            }
        }
    }

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
                    for (Component cc : ((JPanel)c).getComponents()) {
                        if (cc instanceof TrilaterationPanel) {
                            cc.repaint();
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
