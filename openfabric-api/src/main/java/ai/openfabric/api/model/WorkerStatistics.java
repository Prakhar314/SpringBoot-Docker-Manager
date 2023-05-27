package ai.openfabric.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity()
@NoArgsConstructor
@AllArgsConstructor
public class WorkerStatistics extends Datable {

    @Id
    @Column(name = "worker_id", columnDefinition = "character varying(255)")
    private String id;

    private float cpuUsage;
    private float memoryUsage;
    private float networkIn;
    private float networkOut;
    private float blockIn;
    private float blockOut;
    private long pidCount;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "worker_id")
    @JsonIgnore
    private Worker worker;
}
