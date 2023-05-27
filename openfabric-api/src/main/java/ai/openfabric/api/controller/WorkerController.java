package ai.openfabric.api.controller;

import ai.openfabric.api.service.DockerAPIService;
import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
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

import java.util.Optional;

@RestController
@RequestMapping("${node.api.path}/workers")
public class WorkerController {
    // Docker Engine API source: https://docs.docker.com/engine/api/v1.43
    // To enable Docker Engine API: https://gist.github.com/styblope/dc55e0ad2a9848f2cc3307d4819d819f

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private DockerAPIService dockerAPIService;

    @ApiOperation(value = "List all the workers", notes = "Returns id, name, state and status of all the workers")
    @GetMapping(path = "/")
    public @ResponseBody Page<WorkerRepository.BasicWorkerInfo> getWorkers(@RequestParam(required = false) Integer pageIndex, @RequestParam(required = false) Integer pageSize) {
        // default values
        if (pageIndex == null) {
            pageIndex = 0;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        Pageable pageable = Pageable.ofSize(pageSize).withPage(pageIndex);

        // return minimal info
        return workerRepository.findAllMinimal(pageable);
    }

    @ApiOperation("Stop a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Worker stopped"),
            @ApiResponse(code = 304, message = "Worker already stopped"),
            @ApiResponse(code = 404, message = "Worker not found")
    })
    @PostMapping(path = "/{id}/stop")
    public @ResponseBody ResponseEntity<String> stopWorker(@PathVariable String id) {
        try (StopContainerCmd cmd = dockerAPIService.getClient().stopContainerCmd(id)) {
            try {
                cmd.exec();
            }
            catch (NotFoundException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Worker not found");
            }
            catch (NotModifiedException e){
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body("Worker already stopped");
            }
            catch (InternalServerErrorException e){
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.OK).body("Worker stopped");
    }

    @ApiOperation("Start a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Worker started"),
            @ApiResponse(code = 304, message = "Worker already started"),
            @ApiResponse(code = 404, message = "Worker not found")
    })
    @PostMapping(path = "/{id}/start")
    public @ResponseBody ResponseEntity<String> startWorker(@PathVariable String id) {
        try (StartContainerCmd cmd = dockerAPIService.getClient().startContainerCmd(id)) {
            try {
                cmd.exec();
            }
            catch (NotFoundException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Worker not found");
            }
            catch (NotModifiedException e){
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body("Worker already started");
            }
            catch (InternalServerErrorException e){
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.OK).body("Worker started");
    }

    @ApiOperation("Get more info about a worker")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Worker not found")
    })
    @GetMapping(path = "/{id}/info")
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
    @GetMapping(path = "/{id}/stats")
    public @ResponseBody WorkerStatistics statWorker(@PathVariable String id) {
        Optional<Worker> w = workerRepository.findById(id);
        if (w.isPresent()) {
            return w.get().getWorkerStatistics();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
    }
}
