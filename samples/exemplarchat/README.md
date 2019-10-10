# Chat

This is a sample app that makes use of Misk. It demonstrates both web sockets and Misk's recommended
patterns for embedded web UIs.

## Usage

1. Build the React app in the `web` directory.
    ```
    cd samples/exemplarchat/web
    yarn
    yarn run build
    ```
2. Build the project using gradle.
    ```bash
    ./gradlew :samples:exemplarchat:build    
    ```
2. Run the sample.
    ```bash
    java -jar samples/exemplarchat/build/libs/exemplarchat-0.8.0-SNAPSHOT.jar    
    ```
   