package searchengine.model;

import lombok.*;
import javax.persistence.*;

@Entity
@Table(
        name = "page",
        indexes = {
                @Index(name = "path_index", columnList = "path")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "VARCHAR(1024) NOT NULL")
    private String path;

    @Column(columnDefinition = "INT NOT NULL")
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;
}
