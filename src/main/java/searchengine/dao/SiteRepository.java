package searchengine.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.SiteStatus;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    List<Site> findByStatus(SiteStatus status);
    void deleteByUrl(String url);
    Site findByUrl(String url);
}
