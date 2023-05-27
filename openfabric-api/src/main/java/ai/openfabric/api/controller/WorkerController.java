package ai.openfabric.api.controller;

import ai.openfabric.api.component.DockerAPIService;
import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.transport.DockerHttpClient;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${node.api.path}/worker")
public class WorkerController {
    // Docker Engine API source: https://docs.docker.com/engine/api/v1.43
    // To enable Docker Engine API: https://gist.github.com/styblope/dc55e0ad2a9848f2cc3307d4819d819f

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private DockerAPIService dockerAPIService;

    @ApiOperation(value = "List all the workers", notes = "Returns id, name, state and status of all the workers")
    @GetMapping(path = "/workers")
    public @ResponseBody Page<WorkerRepository.BasicWorkerInfo> getWorkers(@RequestParam(required = false) Integer pageIndex, @RequestParam(required = false) Integer pageSize) {
        if (pageIndex == null) {
            pageIndex = 0;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        Pageable pageable = Pageable.ofSize(pageSize).withPage(pageIndex);

        return workerRepository.findAllMinimal(pageable);
    }

    @ApiOperation("Stop a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Worker stopped"),
            @ApiResponse(code = 304, message = "Worker already stopped"),
            @ApiResponse(code = 404, message = "Worker not found"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @PostMapping(path = "/workers/{id}/stop")
    public @ResponseBody ResponseEntity<String> stopWorker(@PathVariable String id) {
        ResponseEntity<String> responseEntity;
        try (DockerHttpClient.Response response = dockerAPIService.post("/containers/" + id + "/stop")) {
            String responseString = new BufferedReader(new InputStreamReader(response.getBody()))
                    .lines()
                    .collect(Collectors.joining("\n"));
            responseEntity = ResponseEntity.status(response.getStatusCode()).body(responseString);
        }
        return responseEntity;
    }

    @ApiOperation("Start a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Worker started"),
            @ApiResponse(code = 304, message = "Worker already started"),
            @ApiResponse(code = 404, message = "Worker not found"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @PostMapping(path = "/workers/{id}/start")
    public @ResponseBody ResponseEntity<String> startWorker(@PathVariable String id) {
        ResponseEntity<String> responseEntity;
        try (DockerHttpClient.Response response = dockerAPIService.post("/containers/" + id + "/start")) {
            String responseString = new BufferedReader(new InputStreamReader(response.getBody()))
                    .lines()
                    .collect(Collectors.joining("\n"));
            responseEntity = ResponseEntity.status(response.getStatusCode()).body(responseString);
        }
        return responseEntity;
    }

    @ApiOperation("Get more info about a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Worker statistics"),
            @ApiResponse(code = 404, message = "Worker not found")
    })
    @GetMapping(path = "/workers/{id}/info")
    public @ResponseBody Worker infoWorker(@PathVariable String id) {
        Optional<Worker> w = workerRepository.findById(id);
        if (w.isPresent()) {
            return w.get();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
    }

    @ApiOperation("Get current statistics of a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Worker statistics"),
            @ApiResponse(code = 404, message = "Worker not found")
    })
    @GetMapping(path = "/workers/{id}/stats")
    public @ResponseBody WorkerStatistics statWorker(@PathVariable String id) {
        Optional<Worker> w = workerRepository.findById(id);
        if (w.isPresent()) {
            return w.get().getWorkerStatistics();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
    }
}
