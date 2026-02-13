# mvc-chat

JavaFX chat application (LAN/P2P style) with protocol fragments `001-013` and a decoupled transport layer.

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

## Architecture (UI Decoupling)
The project uses a simple transport abstraction so the JavaFX UI is not directly coupled to sockets.

- `ChatUIApp` depends on `ChatTransport` (interface), not on `Socket`/`ServerSocket`.
- `SocketChatTransport` is the concrete adapter that manages network connections and threads.
- `ChatTransportListener` sends async events back to UI (`connected`, `disconnected`, `message`, `error`).

This keeps networking concerns in `bl/server` and presentation concerns in `ui`, with a structure appropriate for a semester project.

## Notes
- The app uses SQLite for persistence. A local database file (for example `chat_upb.sqlite`) will be created in the project root.
- Main entry points:
  - UI: `edu.upb.chatupb_v2.ui.ChatUIApp`
  - Bootstrap wrapper: `edu.upb.chatupb_v2.ChatUPB_V2` (delegates to `ChatUIApp`)

## Project Structure
- `src/main/java/edu/upb/chatupb_v2/ui` - JavaFX UI
- `src/main/java/edu/upb/chatupb_v2/bl` - Business logic (protocol messages + transport abstractions)
- `src/main/java/edu/upb/chatupb_v2/repository` - Data access and models
- `src/main/resources/edu/upb/chatupb_v2/ui` - UI resources (CSS)
- `src/main/java/edu/upb/chatupb_v2/bl/message/explaination.md` - summary of the decoupling work
