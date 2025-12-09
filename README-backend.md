Backend README

This backend is a simple Java HTTP server using the built-in com.sun.net.httpserver.HttpServer.

How to build and run (Windows PowerShell):

1. Ensure JDK is installed and `javac`/`java` are on PATH.
2. Place any required JDBC jars in `backend/lib` (e.g., MySQL Connector/J).
3. From the repository root run:

    powershell -ExecutionPolicy ByPass -File backend\\run-server.ps1

Environment variables used:
- RECAPTCHA_SECRET_KEY - reCAPTCHA secret used by the backend (use test key for development)
- SERVER_PORT - optional server port (default 8081)

Notes:
- The run script compiles sources under `backend/src` into `backend/bin` and runs the server.
- Implementations are scaffolded as TODO stubs under `backend/src/com/delivery`.
