package ai.openfabric.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@Entity()
@NoArgsConstructor
@AllArgsConstructor
public class WorkerStatistics extends Datable{

    @Id
    @Column(name = "worker_id")
    private String id;

    private float cpuUsage;
    private float memoryUsage;
    private float networkIn;
    private float networkOut;
    private float blockIn;
    private float blockOut;
    private int pidCount;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "worker_id")
    @JsonIgnore
    private Worker worker;
}
