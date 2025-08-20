# Run simulated MQTT Trilateration RTLS

You can run any number of subscribers and publishers at the same time.

## 1 Run Docker
docker run --rm -it -p 1883:1883 eclipse-mosquitto:2 mosquitto -c /mosquitto-no-auth.conf

## 2 Compile
javac -cp lib/paho-mqtt-client.jar SwingPublisher.java SwingSubscriber.java SimpleJsonParser.java

## 3 Start Subscriber(s)
### 3.1 Start multiple Subscribers
java -cp .:lib/paho-mqtt-client.jar SwingSubscriber &
java -cp .:lib/paho-mqtt-client.jar SwingSubscriber &

### 3.2 Start single Subscriber
java -cp .:lib/paho-mqtt-client.jar SwingSubscriber

### 3.3 Start subscriber with given PREFIX (i.e. A1)
CLIENT_SUFFIX=A1 java -cp .:lib/paho-mqtt-client.jar SwingSubscriber

## 4 Run multiple publishers:

CLIENT_SUFFIX=Publisher1 java -cp .:lib/paho-mqtt-client.jar SwingPublisher &
CLIENT_SUFFIX=Publisher2 java -cp .:lib/paho-mqtt-client.jar SwingPublisher &
CLIENT_SUFFIX=Publisher3 java -cp .:lib/paho-mqtt-client.jar SwingPublisher &

### Prereqs:
- Broker running on localhost:1883 (e.g., Docker: `docker run --rm -it -p 1883:1883 eclipse-mosquitto:2 mosquitto -c /mosquitto-no-auth.conf`)
- Paho client jar in `lib/paho-mqtt-client.jar`

### Notes:
- Keep `CLIENT_SUFFIX` unique per process to avoid disconnecting another client with the same ID.
- Messages published to `sensor/data` will fan out to all subscribers.
- Order is guaranteed per connection but not across different publishers.