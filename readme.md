# Docker Manager API in Spring Boot

### Setup
- Clone the repository
- Change the variables in `.env` file
- Open the project in IntelliJ IDEA
- Run the project using `Application.java`
- Test the API using Swagger UI (`http://localhost:8080/swagger-ui/#/`), or use curl commands, or any other tool

**Note**: If you do not have an API listening on port 2375, you can look here for instructions on how to set it up: https://gist.github.com/styblope/dc55e0ad2a9848f2cc3307d4819d819f

### API Endpoints
- See `http://localhost:8080/swagger-ui/#/` for the API documentation.

### Features
- List all workers (pagination supported)
- Start or stop a worker
- Get worker details
- Get worker stats (cpuUsage, memoryUsage, etc.)

### Design
#### Models
- Worker (id, name, status, stats, etc.)
- WorkerStatistics (cpuUsage, memoryUsage, networkIn, networkOut, etc.)

All the models are stored in the database.

#### Services
- BackgroundUpdaterService (updates the stats of all workers in the background periodically)
- DockerAPIService (for communication with the Docker API)