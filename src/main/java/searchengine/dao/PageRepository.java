package searchengine.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    boolean existsBySiteAndPath(Site site, String path);

    @Modifying
    @Query(value = "DELETE p FROM page p INNER JOIN site s ON p.site_id = s.id WHERE s.url = :url", nativeQuery = true)
    void deleteBySiteUrl(@Param("url") String url);

    Optional<Page> findBySiteAndPath(Site site, String path);
    int countBySite(searchengine.model.Site site);

}
