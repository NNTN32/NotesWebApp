# Run Backend locally

Use Java 21. Start only the infrastructure containers; run one Backend JVM from your IDE.

```bash
cd /Users/nhannguyen/Desktop/NotesWebApp/notesWeb
export JAVA_HOME="$HOME/.local/share/jdks/amazon-corretto-21.jdk/Contents/Home"
docker compose up -d --wait notes-postgres notes-redis notes-kafka notes-rabbitmq
bash ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"
```

Local `.env` is ignored by Git. Use PostgreSQL `localhost:5433`, Redis `localhost:6380`, Kafka `localhost:9095`, RabbitMQ `localhost:5673`, and STOMP `localhost:61614`. Keep credentials consistent with the local Compose services.

- Cursor: open the repository root and select **Backend local (8082)** in Run and Debug.
- IntelliJ: select Java 21, main class `com.example.notesWeb.NotesWebApplication`, working directory `/Users/nhannguyen/Desktop/NotesWebApp/notesWeb`, and program arguments `--server.port=8082`.
- Swagger: http://localhost:8082/.

If `${DB_URL}` appears literally in a JDBC error, verify the working directory and `.env`. If port 8081 is occupied by OrbStack, use 8082 locally. Stop the existing local Backend before switching IDEs; both IDEs cannot listen on 8082 simultaneously.
