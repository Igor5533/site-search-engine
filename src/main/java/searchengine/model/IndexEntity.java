package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "`index`")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(columnDefinition = "FLOAT NOT NULL")
    private float rank;
}

