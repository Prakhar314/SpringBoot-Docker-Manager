package ai.openfabric.api.controller;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.repository.WorkerRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${node.api.path}/worker")
public class WorkerController {

    private final WorkerRepository workerRepository;

    public WorkerController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @PostMapping(path = "/hello")
    public @ResponseBody String hello(@RequestBody String name) {
        return "Hello!" + name;
    }

    @GetMapping(path = "/workers")
    public @ResponseBody List<Worker> getWorkers() {
        // return all workers in the database
        return (List<Worker>) workerRepository.findAll();
    }

    @PatchMapping(path = "/workers/stop/{id}")
    public @ResponseBody Worker stopWorker(@PathVariable String id) {
        return null;
    }

    @PatchMapping(path = "/workers/start/{id}")
    public @ResponseBody Worker startWorker(@PathVariable String id) {
        return null;
    }

    @GetMapping(path = "/workers/info/{id}")
    public @ResponseBody Worker infoWorker(@PathVariable String id) {
        return null;
    }

    @GetMapping(path = "/workers/stat/{id}")
    public @ResponseBody Worker statWorker(@PathVariable String id) {
        return null;
    }
}
