package searchengine.services;

import searchengine.dto.statistics.GenericResponse;

public interface IndexingService {
    GenericResponse startIndexing();
    GenericResponse stopIndexing();
    GenericResponse indexPage(String url);
}
