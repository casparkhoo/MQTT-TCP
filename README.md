
# 1 Install Docker
docker run --rm -it -p 1883:1883 eclipse-mosquitto:2 mosquitto -c /mosquitto-no-auth.conf

# 2 Run Subscriber
java -cp .:lib/paho-mqtt-client.jar SwingSubscriber

# 3 Run Publisher
java -cp .:lib/paho-mqtt-client.jar SwingPublisher