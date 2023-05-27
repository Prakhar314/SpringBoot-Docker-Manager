package ai.openfabric.api.service;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A service that provides access to the Docker Engine API
 */
@Service
public class DockerAPIService {

    private DockerClient dockerClient;
    private DockerHttpClient httpClient;

    public DockerClient getClient(){
        return dockerClient;
    }

    private DockerAPIService() {

        LoggerFactory.getLogger(DockerAPIService.class).info("Connecting to Docker Engine API");

        Dotenv dotenv = Dotenv.load();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(
                        dotenv.get("DOCKER_HOST", "tcp://localhost:2375")
                ).build();

        httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(5))
                .build();

        dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * Extracts statistics and stores it in a WorkerStatistics object
     * @param statistics the statistics to extract
     * @param workerStatistics the WorkerStatistics object to store the information in
     */
    public static void extractStats(Statistics statistics, WorkerStatistics workerStatistics){
        long network_in = 0, network_out = 0, block_in = 0, block_out = 0;
        // calculate memory usage
        MemoryStatsConfig memoryStats = statistics.getMemoryStats();
        long usedMemory = Optional.ofNullable(memoryStats).map(MemoryStatsConfig::getUsage).orElse(0L);
        usedMemory -= Optional.ofNullable(memoryStats).map(MemoryStatsConfig::getStats).map(StatsConfig::getCache).orElse(0L);
        // calculate cpu usage
        CpuStatsConfig cpuStats = statistics.getCpuStats();
        CpuStatsConfig preCpuStats = statistics.getPreCpuStats();
        long cpu_delta = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getCpuUsage).map(CpuUsageConfig::getTotalUsage).orElse(0L)
                - Optional.ofNullable(preCpuStats).map(CpuStatsConfig::getCpuUsage).map(CpuUsageConfig::getTotalUsage).orElse(0L);
        long system_cpu_delta = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getSystemCpuUsage).orElse(0L)
                - Optional.ofNullable(preCpuStats).map(CpuStatsConfig::getSystemCpuUsage).orElse(0L);
        long num_cpus = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getOnlineCpus).orElse(0L);
        float cpu_percent = 100.0f * cpu_delta / system_cpu_delta * num_cpus;
        // number of pids
        long pid_count = Optional.ofNullable(statistics.getPidsStats()).map(PidsStatsConfig::getCurrent).orElse(0L);
        // calculate network usage
        Optional<Collection<StatisticNetworksConfig>> networks = Optional.ofNullable(statistics.getNetworks()).map(Map::values);
        if (networks.isPresent()) {
            for (StatisticNetworksConfig networkStats : networks.get()) {
                network_in += Optional.ofNullable(networkStats.getRxBytes()).orElse(0L);
                network_out += Optional.ofNullable(networkStats.getTxBytes()).orElse(0L);
            }
        }
        // calculate block io
        Optional<List<BlkioStatEntry>> blkioStats = Optional.ofNullable(statistics.getBlkioStats()).map(BlkioStatsConfig::getIoServiceBytesRecursive);
        if (blkioStats.isPresent()){
            for (BlkioStatEntry blkioStat : blkioStats.get()) {
                if (blkioStat.getOp().equalsIgnoreCase("Read")) {
                    block_in += blkioStat.getValue();
                } else if (blkioStat.getOp().equalsIgnoreCase("Write")) {
                    block_out += blkioStat.getValue();
                }
            }
        }

        workerStatistics.setMemoryUsage(usedMemory);
        workerStatistics.setCpuUsage(cpu_percent);
        workerStatistics.setPidCount(pid_count);
        workerStatistics.setNetworkIn(network_in);
        workerStatistics.setNetworkOut(network_out);
        workerStatistics.setBlockIn(block_in);
        workerStatistics.setBlockOut(block_out);

    }

    /**
     * Extracts information from a container and stores it in a Worker object
     * @param container the container to extract information from
     * @param worker the worker object to store the information in
     */
    public static void extractInfo(Container container, Worker worker){
        worker.setId(container.getId());
        // convert seconds to milliseconds before storing date
        worker.setCreated(new Date(container.getCreated() * 1000));
        worker.setCommand(container.getCommand());
        worker.setImageId(container.getImageId());
        worker.setStatus(container.getStatus());
        worker.setName(container.getNames()[0]);
        worker.setImage(container.getImage());
        worker.setState(container.getState());
        // store only public ports, without duplicates
        List<Integer> portsNoDuplicates = Arrays.stream(container.getPorts()).map(ContainerPort::getPublicPort).distinct().collect(Collectors.toList());
        worker.setPorts(portsNoDuplicates);
    }

    @PreDestroy
    public void destroy() throws IOException {
        dockerClient.close();
        httpClient.close();
    }
}
