package ai.openfabric.api.controller;

import ai.openfabric.api.common.DockerEngineAPI;
import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("${node.api.path}/worker")
public class WorkerController {
    // Docker Engine API source: https://docs.docker.com/engine/api/v1.43
    // To enable Docker Engine API: https://gist.github.com/styblope/dc55e0ad2a9848f2cc3307d4819d819f

    private final WorkerRepository workerRepository;

    public WorkerController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @GetMapping(path = "/workers")
    public @ResponseBody List<Worker> getWorkers() {
//        return null;
        return (List<Worker>) workerRepository.findAll();
    }

    @PostMapping(path = "/workers/{id}/stop")
    public @ResponseBody void stopWorker(@PathVariable String id) {
        DockerEngineAPI dockerEngineAPI = DockerEngineAPI.getInstance();
        DockerHttpClient.Response response = dockerEngineAPI.post("/containers/" + id + "/stop");
        if (response.getStatusCode() == 304) {
            throw new ResponseStatusException(HttpStatus.NOT_MODIFIED, "Worker already stopped");
        }
        if (response.getStatusCode() == 404){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
        if (response.getStatusCode() == 500){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }
    }

    @PostMapping(path = "/workers/{id}/start")
    public @ResponseBody void startWorker(@PathVariable String id) {
        DockerEngineAPI dockerEngineAPI = DockerEngineAPI.getInstance();
        DockerHttpClient.Response response = dockerEngineAPI.post("/containers/" + id + "/start");
        if (response.getStatusCode() == 304) {
            throw new ResponseStatusException(HttpStatus.NOT_MODIFIED, "Worker already started");
        }
        if (response.getStatusCode() == 404){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
        if (response.getStatusCode() == 500){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }
    }

    @GetMapping(path = "/workers/{id}/info")
    public @ResponseBody Worker infoWorker(@PathVariable String id) {
        Optional<Worker> w = workerRepository.findById(id);
        if (w.isPresent()){
            return w.get();
        }
        else{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
    }

    @GetMapping(path = "/workers/{id}/stats")
    public @ResponseBody WorkerStatistics statWorker(@PathVariable String id) {
        Optional<Worker> w = workerRepository.findById(id);
        if (w.isPresent()){
            return w.get().getWorkerStatistics();
        }
        else{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
    }
}
