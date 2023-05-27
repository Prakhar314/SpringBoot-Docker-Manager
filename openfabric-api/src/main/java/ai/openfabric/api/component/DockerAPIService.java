package ai.openfabric.api.component;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.cdimascio.dotenv.Dotenv;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class DockerAPIService {

    private static DockerAPIService instance;
    private DockerClient dockerClient;
    private DockerHttpClient httpClient;

    public static DockerClient getClient(){
        if(instance == null){
            instance = new DockerAPIService();
        }
        return instance.dockerClient;
    }

    private DockerAPIService() {
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

    public static void extractStats(Statistics statistics, WorkerStatistics workerStatistics){
        long network_in = 0, network_out = 0, block_in = 0, block_out = 0;

        MemoryStatsConfig memoryStats = statistics.getMemoryStats();
        long usedMemory = Optional.ofNullable(memoryStats).map(MemoryStatsConfig::getUsage).orElse(0L);
        usedMemory -= Optional.ofNullable(memoryStats).map(MemoryStatsConfig::getStats).map(StatsConfig::getCache).orElse(0L);
        CpuStatsConfig cpuStats = statistics.getCpuStats();
        CpuStatsConfig preCpuStats = statistics.getPreCpuStats();
        long cpu_delta = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getCpuUsage).map(CpuUsageConfig::getTotalUsage).orElse(0L)
                - Optional.ofNullable(preCpuStats).map(CpuStatsConfig::getCpuUsage).map(CpuUsageConfig::getTotalUsage).orElse(0L);
        long system_cpu_delta = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getSystemCpuUsage).orElse(0L)
                - Optional.ofNullable(preCpuStats).map(CpuStatsConfig::getSystemCpuUsage).orElse(0L);
        long num_cpus = Optional.ofNullable(cpuStats).map(CpuStatsConfig::getOnlineCpus).orElse(0L);
        float cpu_percent = 100.0f * cpu_delta / system_cpu_delta * num_cpus;
        long pid_count = Optional.ofNullable(statistics.getPidsStats()).map(PidsStatsConfig::getCurrent).orElse(0L);
        Optional<Collection<StatisticNetworksConfig>> networks = Optional.ofNullable(statistics.getNetworks()).map(Map::values);
        if (networks.isPresent()) {
            for (StatisticNetworksConfig networkStats : networks.get()) {
                network_in += Optional.ofNullable(networkStats.getRxBytes()).orElse(0L);
                network_out += Optional.ofNullable(networkStats.getTxBytes()).orElse(0L);
            }
        }
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

    public static void extractInfo(Container container, Worker worker){
        worker.setId(container.getId());
        worker.setCreated(new Date(container.getCreated() * 1000));
        worker.setCommand(container.getCommand());
        worker.setImageId(container.getImageId());
        worker.setStatus(container.getStatus());
        worker.setName(container.getNames()[0]);
        worker.setImage(container.getImage());
        worker.setPorts(Arrays.stream(container.getPorts()).map(ContainerPort::getPublicPort).collect(Collectors.toList()));
        worker.setState(container.getState());
    }

    @PreDestroy
    public void destroy() throws IOException {
        dockerClient.close();
        httpClient.close();
    }
}
