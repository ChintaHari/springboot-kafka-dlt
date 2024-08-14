# SpringBoot Kafka Dead Letter Topic (DLT) Demo

## Overview of the Project

This Spring Boot application demonstrates the implementation of Kafka's Dead Letter Topic (DLT) concept. The primary purpose of this project is to handle message processing failures in a Kafka-driven messaging system effectively. It includes a setup with a Kafka message producer, consumer, and a DLT to manage errors efficiently.

## Why is the DLT Concept Necessary?

In any messaging system, the potential for message processing failures exists due to various reasons such as data errors, network issues, or service downtime. Kafka's Dead Letter Topic (DLT) provides a robust mechanism to handle these failures gracefully.

### Example Scenario:
Imagine a scenario where a Kafka consumer processes messages containing user data. If a message contains invalid data (e.g., a null value where a non-null is expected), the consumer might fail to process this message. Without a DLT, such a message could be lost or cause the consumer to repeatedly fail, blocking other messages.

With a DLT, the consumer can redirect messages that exceed a certain number of processing attempts to a Dead Letter Topic. This separation allows the consumer to continue processing other messages without interruption. Meanwhile, the messages in the DLT can be analyzed and handled appropriately, perhaps after fixing the underlying issue or manually intervening.

![Kafka DLT Flow](/images/Architecture.png)  

This flowchart shows how messages flow from the producer to the consumer, and if failures occur beyond the configured retry limit, they are redirected to the DLT, ensuring that message processing continues smoothly for valid messages.

## Dependencies Required

This project relies on several Maven dependencies for its operation:

1. **`Spring Boot Starter Web`**: Provides all the necessary dependencies for building web applications including RESTful applications using Spring MVC. It simplifies the setup and development of new applications.
2. **`Spring Kafka`**: Adds support for Kafka integration, allowing the application to produce and consume messages from Kafka topics.
3. **`Spring Boot Devtools`**: Provides fast application restarts, LiveReload, and configurations for enhanced development experience. Although optional, it helps in development.
4. **`Lombok`**: Simplifies the code by generating boilerplate code like getters, setters, and constructors through annotations.
5. **`Spring Boot Starter Test`**: Provides testing utilities but will not be discussed as per your request.
6. **`OpenCSV`**: Facilitates operations with CSV files, in this case used to read user data to be sent to Kafka.

## Configuration Explanation

### Docker Compose Configuration

The docker-compose.yaml file defines the Docker setup for Kafka along with Zookeeper and a Kafka UI for monitoring. Here's an explanation of key parts of the configuration:

```yaml
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - 22181:2181

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - 9092:9092
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    depends_on:
      - kafka
    ports:
      - 8090:8080
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: localhost:9092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
```

### Summary

- **Zookeeper**: Sets up Zookeeper, which Kafka uses for managing cluster metadata and coordination.
- **Kafka**: Configures a Kafka broker that depends on Zookeeper and exposes necessary settings for local development.
- **Kafka UI**: Provides a user interface to monitor Kafka clusters, making it easier to view messages and topics in a web browser.

This configuration uses Docker to create a self-contained environment for Kafka and Zookeeper, simplifying setup and ensuring consistent, repeatable deployments.


### `application.yml` and `properties`

The application utilizes both `application.yml` (or `application.properties`) for configuring Spring Boot specific settings, and custom properties defined within the code for Kafka setup. Here's an explanation of the key configurations:

#### `application.yml`

```yml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      bootstrap-servers: localhost:9092
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: demo
      auto-offset-reset: latest
      properties:
        spring:
          json:
            trusted:
              packages: com.example.springboot_kafka_dlt.dto
```

## Key-Value Serialization

### Key Serializer/Deserializer
- **Purpose**: Determines how message keys are serialized to bytes when sent to Kafka or deserialized when read from Kafka.
- **Importance**: This is crucial because Kafka uses byte arrays for keys and values, requiring a translation between the object representation in your application and the byte stream Kafka operates with.

### Value Serializer/Deserializer
- **Purpose**: Similarly, this configures how message values are serialized or deserialized.
- **Utility**: Using `JsonSerializer` and `JsonDeserializer` simplifies the process of sending and receiving Java objects as JSON, aligning with the common use of JSON for data interchange.

### Why Serialize and Deserialize?
- **Necessity**: Serialization and deserialization are essential in Kafka to ensure that the messages are correctly formatted as byte streams when sent to and from Kafka.
- **Benefits**: This process allows complex data structures like Java objects to be efficiently transmitted over the network and reconstructed at the destination, ensuring data integrity and compatibility.

## Kafka Publisher and Consumer Overview

### Publisher

The publisher in this project is responsible for sending user data as messages to a Kafka topic. The primary class involved is `KafkaMessagePublisher`, which uses Spring's `KafkaTemplate` for asynchronous message publishing.

```java
    public void sendEvents(User user) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topicName, user);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Sent message=[" + user.toString() +
                        "] with offset=[" + result.getRecordMetadata().offset() + "]");
            } else {
                System.out.println("Unable to send message=[" +
                        user.toString() + "] due to : " + ex.getMessage());
            }
        });
    }
}
```

### Consumer

The consumer is set up to receive messages from the Kafka topic, process them, and handle failures by potentially sending them to a Dead Letter Topic after retries. The primary class here is `KafkaMessageConsumer`, which uses annotations to listen to Kafka topics and handle messages accordingly.

```java
 @RetryableTopic(attempts = "4")
    @KafkaListener(topics = "${app.topic.name}", groupId = "demo-group")
    public void consumeEvents(User user, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            log.info("Received: {} from {} offset {}", new ObjectMapper().writeValueAsString(user), topic, offset);
            //validate restricted IP before process the records
            List<String> restrictedIpList = Stream.of("32.241.244.236", "15.55.49.164", "81.1.95.253", "126.130.43.183").collect(Collectors.toList());
            if (restrictedIpList.contains(user.getIpAddress())) {
                throw new RuntimeException("Invalid IP Address received !");
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
```

### DLT Handler

The DLT handler is a crucial component for error management in Kafka messaging systems, allowing developers to isolate problem messages and handle them according to the application’s needs, such as by logging, sending alerts, or even reprocessing under certain conditions.

```java
@DltHandler
    public void listenDLT(User user, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("DLT Received: {} from {} offset {}", user.getFirstName(), topic, offset);
    }
```

## How to Run the Application

Follow these simple steps to get your application up and running:

### Start Docker Containers

1. **Open a terminal** and navigate to the directory with your `docker-compose.yml`.
2. **Run Docker Compose**:
   ```bash
   docker-compose up -d
   ```
This command starts Kafka and Zookeeper containers in the background.

### Run the Spring Boot Application

1. Navigate to your project directory where `pom.xml` is located.
2. Run
   ```bash
   ./mvnw spring-boot:run
   ```

## Testing

To test the Kafka publisher with an example JSON payload using Postman:

### Set Up Your Request
1. **Open Postman** and create a new POST request.
2. **Set the URL** to your Spring Boot application's endpoint that handles Kafka message publishing. This will typically be something like `http://localhost:8081/producer-app/publishNew`.
3. **Configure Headers**:
   - Under the 'Headers' tab, set `Content-Type` to `application/json`.
4. **Prepare the JSON Payload**:
   - In the 'Body' tab, select 'raw' and input the JSON payload:

```json
{
  "id": 1,
  "firstName": "Hari",
  "lastName": "Krishna",
  "email": "hk@gmail.com",
  "gender": "Non-binary",
  "ipAddress": "73.15.57.171"
}
```

5. **Send the Request**:
   - Click the 'Send' button to publish the message to the Kafka topic configured in your application.

## Expected Output and Explanation

After sending the request through Postman, the application processes and sends the message to Kafka. Let's break down what happens and what the output signifies:

### Producer Logs
The logs will confirm that the message was sent successfully to the Kafka broker. It will log the details of the message along with the offset it was stored at in Kafka. For instance:

```plaintext
Sent message=[User(id=1, firstName=Hari, lastName=Krishna, email=hk@gmail.com, gender=Non-binary, ipAddress=73.15.57.171)] with offset=[0]
```

This output means the message has been accepted by Kafka and stored at offset 0 in the designated Kafka topic.

### Consumer Logs
The consumer will pick up this message and attempt to process it. If successful, it will log a message indicating receipt:

```plaintext
Received: {"id":1,"firstName":"Hari","lastName":"Krishna","email":"hk@gmail.com","gender":"Non-binary","ipAddress":"73.15.57.171"} from kafka-error-handler offset 0
```

This confirms the message was consumed from the Kafka topic. The message details are logged as received from the topic, and the offset indicates where in the topic this particular message was read from.

## Conclusion

This test verifies the end-to-end functionality of your Kafka integration within the Spring Boot application, from sending a message via a RESTful interface, through processing by Kafka, to consuming and logging by your application. By examining the logs and Kafka tool outputs, you can confirm that the application is correctly configured and functioning as expected in handling Kafka messages.
