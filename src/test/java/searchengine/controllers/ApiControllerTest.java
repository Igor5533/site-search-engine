package searchengine.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.*;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ApiController.class)
public class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatisticsService statisticsService;
    @MockBean
    private IndexingService indexingService;
    @MockBean
    private SearchService searchService;

    @Test
    void testStartIndexingSuccess() throws Exception {
        when(indexingService.startIndexing()).thenReturn(new GenericResponse(true));
        mockMvc.perform(get("/api/startIndexing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void testStartIndexingAlreadyRunning() throws Exception {
        when(indexingService.startIndexing()).thenReturn(new GenericResponse(false, "Индексация уже запущена"));
        mockMvc.perform(get("/api/startIndexing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Индексация уже запущена"));
    }

    @Test
    void testStopIndexingNotRunning() throws Exception {
        when(indexingService.stopIndexing()).thenReturn(new GenericResponse(false, "Индексация не запущена"));
        mockMvc.perform(get("/api/stopIndexing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Индексация не запущена"));
    }

    @Test
    void testStopIndexingSuccess() throws Exception {
        when(indexingService.stopIndexing()).thenReturn(new GenericResponse(true));
        mockMvc.perform(get("/api/stopIndexing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void testIndexPageSuccess() throws Exception {
        when(indexingService.indexPage("http://example.com/page1")).thenReturn(new GenericResponse(true));
        mockMvc.perform(post("/api/indexPage").param("url", "http://example.com/page1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void testIndexPageOutOfConfig() throws Exception {
        when(indexingService.indexPage("http://unknown.com/page"))
                .thenReturn(new GenericResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        mockMvc.perform(post("/api/indexPage").param("url", "http://unknown.com/page")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value(
                        "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
    }

    @Test
    void testIndexPagePageError() throws Exception {
        when(indexingService.indexPage("http://example.com/error"))
                .thenReturn(new GenericResponse(false, "Страница вернула ошибку 404"));
        mockMvc.perform(post("/api/indexPage").param("url", "http://example.com/error")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Страница вернула ошибку 404"));
    }

    @Test
    void testSearchMissingQuery() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Задан пустой поисковый запрос"));
    }

    @Test
    void testSearchSiteNotIndexed() throws Exception {
        SearchResponse searchResp = new SearchResponse();
        searchResp.setResult(false);
        searchResp.setError("Сайт не найден или не проиндексирован");
        when(searchService.search("тест", "http://invalid.com", 0, 20)).thenReturn(searchResp);
        mockMvc.perform(get("/api/search")
                        .param("query", "тест")
                        .param("site", "http://invalid.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Сайт не найден или не проиндексирован"));
    }

    @Test
    void testSearchNoIndexedSites() throws Exception {
        SearchResponse searchResp = new SearchResponse();
        searchResp.setResult(false);
        searchResp.setError("Нет доступных проиндексированных сайтов");
        when(searchService.search("тест", null, 0, 20)).thenReturn(searchResp);
        mockMvc.perform(get("/api/search").param("query", "тест"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.error").value("Нет доступных проиндексированных сайтов"));
    }

    @Test
    void testSearchSuccess() throws Exception {
        SearchData searchData = new SearchData();
        searchData.setSite("http://example.com");
        searchData.setSiteName("Example Site");
        searchData.setUri("/test-page");
        searchData.setTitle("Заголовок страницы");
        searchData.setSnippet("Пример сниппета");
        searchData.setRelevance(1.0f);
        List<SearchData> dataList = Collections.singletonList(searchData);

        SearchResponse searchResp = new SearchResponse();
        searchResp.setResult(true);
        searchResp.setCount(1);
        searchResp.setData(dataList);
        when(searchService.search("пример", null, 0, 20)).thenReturn(searchResp);

        mockMvc.perform(get("/api/search").param("query", "пример"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].site").value("http://example.com"))
                .andExpect(jsonPath("$.data[0].siteName").value("Example Site"))
                .andExpect(jsonPath("$.data[0].uri").value("/test-page"))
                .andExpect(jsonPath("$.data[0].title").value("Заголовок страницы"))
                .andExpect(jsonPath("$.data[0].snippet").value("Пример сниппета"))
                .andExpect(jsonPath("$.data[0].relevance").value(1.0));
    }

    @Test
    void testStatisticsSuccess() throws Exception {
        TotalStatistics total = new TotalStatistics();
        total.setSites(1);
        total.setPages(10);
        total.setLemmas(100);
        total.setIndexing(false);
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setUrl("http://example.com");
        item.setName("Example Site");
        item.setStatus("INDEXED");
        item.setStatusTime(System.currentTimeMillis() / 1000);
        item.setError(null);
        item.setPages(10);
        item.setLemmas(100);
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(Collections.singletonList(item));
        StatisticsResponse statsResp = new StatisticsResponse();
        statsResp.setResult(true);
        statsResp.setStatistics(data);
        when(statisticsService.getStatistics()).thenReturn(statsResp);

        mockMvc.perform(get("/api/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.statistics.total.sites").value(1))
                .andExpect(jsonPath("$.statistics.total.indexing").value(false))
                .andExpect(jsonPath("$.statistics.detailed[0].url").value("http://example.com"))
                .andExpect(jsonPath("$.statistics.detailed[0].status").value("INDEXED"));
    }
}

