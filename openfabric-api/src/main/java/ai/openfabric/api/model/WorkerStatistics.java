package ai.openfabric.api.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

@Entity()
public class WorkerStatistics extends Datable implements Serializable {

    @Id
    @Getter
    @Setter
    @Column(name = "worker_id")
    private String id;

    @Getter
    @Setter
    private float cpuUsage;
    @Getter
    @Setter
    private float memoryUsage;

    @Getter
    @Setter
    private float networkIn;
    @Getter
    @Setter
    private float networkOut;

    @Getter
    @Setter
    private float blockIn;
    @Getter
    @Setter
    private float blockOut;

    @Getter
    @Setter
    private int pidCount;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "worker_id")
    private Worker worker;
}
