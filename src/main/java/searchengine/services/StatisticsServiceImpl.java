package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dao.LemmaRepository;
import searchengine.dao.PageRepository;
import searchengine.dao.SiteRepository;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.SiteStatus;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites((int) siteRepository.count());
        total.setPages((int) pageRepository.count());
        total.setLemmas((int) lemmaRepository.count());
        boolean indexing = siteRepository.findByStatus(SiteStatus.INDEXING).stream().findAny().isPresent();
        total.setIndexing(indexing);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sites = siteRepository.findAll();
        for (Site site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
            item.setError(site.getLastError());
            int sitePages = pageRepository.countBySite(site);
            int siteLemmas = lemmaRepository.countBySite(site);
            item.setPages(sitePages);
            item.setLemmas(siteLemmas);
            detailed.add(item);
        }

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);
        return response;
    }
}
