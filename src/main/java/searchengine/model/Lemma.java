package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "lemma")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String lemma;

    @Column(columnDefinition = "INT NOT NULL")
    private int frequency;
}

