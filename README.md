# mvc-chat

JavaFX-based chat application with a simple SQLite-backed data layer.

## Requirements
- Java 21
- Maven 3.9+

## Run
```bash
mvn javafx:run
```

## Build
```bash
mvn clean package
```

## Notes
- The app uses SQLite for persistence. A local database file (for example `chat_upb.sqlite`) will be created in the project root.
- Main entry points:
  - UI: `edu.upb.chatupb_v2.ui.ChatUIApp`
  - App bootstrap: `edu.upb.chatupb_v2.ChatUPB_V2`

## Project Structure
- `src/main/java/edu/upb/chatupb_v2/ui` - JavaFX UI
- `src/main/java/edu/upb/chatupb_v2/bl` - Business logic (messages, server)
- `src/main/java/edu/upb/chatupb_v2/repository` - Data access and models
- `src/main/resources/edu/upb/chatupb_v2/ui` - UI resources (CSS)
