package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import searchengine.config.SitesList;
import searchengine.dao.IndexRepository;
import searchengine.dao.LemmaRepository;
import searchengine.dao.PageRepository;
import searchengine.dao.SiteRepository;
import searchengine.dto.statistics.GenericResponse;
import searchengine.model.Site;
import searchengine.model.SiteStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IndexingServiceImplTest {

    @Mock private SitesList sitesList;
    @Mock private SiteRepository siteRepository;
    @Mock private PageRepository pageRepository;
    @Mock private LemmaRepository lemmaRepository;
    @Mock private IndexRepository indexRepository;

    @InjectMocks
    private IndexingServiceImpl indexingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(indexingService, "indexingInProgress", new AtomicBoolean(false));
    }

    @Test
    void testStartIndexingWhenAlreadyRunning() {
        ReflectionTestUtils.setField(indexingService, "indexingInProgress", new AtomicBoolean(true));
        GenericResponse response = indexingService.startIndexing();
        assertFalse(response.isResult());
        assertEquals("Индексация уже запущена", response.getError());
    }

    @Test
    void testStartIndexingDatabaseError() {
        when(sitesList.getSites()).thenThrow(new DataAccessException("DB error") {});
        GenericResponse response = indexingService.startIndexing();
        assertFalse(response.isResult());
        assertEquals("Ошибка базы данных", response.getError());
    }

    @Test
    void testStopIndexingWhenNotRunning() {
        GenericResponse response = indexingService.stopIndexing();
        assertFalse(response.isResult());
        assertEquals("Индексация не запущена", response.getError());
    }

    @Test
    void testStopIndexingSuccessfully() {
        ReflectionTestUtils.setField(indexingService, "indexingInProgress", new AtomicBoolean(true));
        Site site = new Site();
        site.setId(1);
        site.setUrl("http://example.com");
        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        when(siteRepository.findByStatus(SiteStatus.INDEXING))
                .thenReturn(Collections.singletonList(site));

        GenericResponse response = indexingService.stopIndexing();
        assertTrue(response.isResult());
        verify(siteRepository).save(argThat(s -> s.getStatus() == SiteStatus.FAILED));
    }

    @Test
    void testIndexPageWithInvalidUrl() {
        when(sitesList.getSites()).thenReturn(Collections.emptyList());
        GenericResponse response = indexingService.indexPage("http://invalid.com/page");
        assertFalse(response.isResult());
        assertEquals("Данная страница находится за пределами сайтов, указанных в конфигурационном файле", response.getError());
    }

    @Test
    void testIndexPageDatabaseError() {
        searchengine.config.Site configSite = new searchengine.config.Site();
        configSite.setUrl("http://example.com");
        configSite.setName("Example");
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findByUrl("http://example.com"))
                .thenThrow(new DataAccessException("DB fail") {});

        GenericResponse response = indexingService.indexPage("http://example.com/page1");
        assertFalse(response.isResult());
        assertTrue(response.getError().contains("Ошибка при работе с базой данных"));
    }

    @Test
    void testGetPathMethod() {
        String fullUrl = "http://example.com/page1";
        String baseUrl = "http://example.com";
        String path = (String) ReflectionTestUtils.invokeMethod(indexingService, "getPath", fullUrl, baseUrl);
        assertEquals("/page1", path);
    }
}
