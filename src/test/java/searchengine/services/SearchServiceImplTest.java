package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import searchengine.dao.IndexRepository;
import searchengine.dao.LemmaRepository;
import searchengine.dao.SiteRepository;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchServiceImplTest {

    @Mock private SiteRepository siteRepository;
    @Mock private LemmaRepository lemmaRepository;
    @Mock private IndexRepository indexRepository;

    @InjectMocks
    private SearchServiceImpl searchService;

    private Site exampleSite;
    private Page examplePage;

    @BeforeEach
    void setUp() {
        exampleSite = new Site();
        exampleSite.setUrl("http://example.com");
        exampleSite.setName("Example");
        exampleSite.setStatus(SiteStatus.INDEXED);

        examplePage = new Page();
        examplePage.setSite(exampleSite);
        examplePage.setPath("/test-page");
        examplePage.setContent("<title>Test Page</title> Это тестовая страница.");
    }

    @Test
    void testSearchWithEmptyQuery() {
        SearchResponse response = searchService.search("", null, 0, 20);
        assertFalse(response.isResult());
        assertEquals("Не удалось выделить леммы из поискового запроса", response.getError());
    }

    @Test
    void testSearchSiteNotIndexed() {
        when(siteRepository.findByUrl("http://invalid.com")).thenReturn(null);
        SearchResponse response = searchService.search("тест", "http://invalid.com", 0, 20);
        assertFalse(response.isResult());
        assertEquals("Сайт не найден или не проиндексирован", response.getError());
    }

    @Test
    void testSearchWithNoIndexedSites() {
        when(siteRepository.findAll()).thenReturn(Collections.emptyList());
        SearchResponse response = searchService.search("тест", null, 0, 20);
        assertFalse(response.isResult());
        assertEquals("Нет доступных проиндексированных сайтов", response.getError());
    }

    @Test
    void testGetLemmaFrequencySum() {
        Lemma lemma = new Lemma();
        lemma.setLemma("тест");
        lemma.setFrequency(5);
        when(lemmaRepository.findBySiteAndLemma(any(), eq("тест")))
                .thenReturn(Optional.of(lemma));
        int freqSum = searchService.getLemmaFrequencySum("тест", List.of(exampleSite));
        assertEquals(5, freqSum);
    }

    @Test
    void testFindPagesByLemma() {
        Lemma lemma = new Lemma();
        lemma.setId(1);
        lemma.setLemma("тест");
        lemma.setSite(exampleSite);

        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPage(examplePage);
        indexEntity.setLemma(lemma);

        when(lemmaRepository.findBySiteAndLemma(any(), eq("тест")))
                .thenReturn(Optional.of(lemma));
        when(indexRepository.findAll()).thenReturn(Collections.singletonList(indexEntity));

        Set<Page> resultPages = searchService.findPagesByLemma("тест", List.of(exampleSite));
        assertFalse(resultPages.isEmpty());
        assertTrue(resultPages.contains(examplePage));
    }

    @Test
    void testExtractTitle() {
        String html = "<html><head><title>Пример заголовка</title></head><body>Содержимое страницы</body></html>";
        String title = searchService.extractTitle(html);
        assertEquals("Пример заголовка", title);
    }

    @Test
    void testMakeSnippet() {
        String html = "<html><body>Это <b>тест</b>овая страница с некоторым текстом.</body></html>";
        Set<String> queryLemmas = Set.of("тест");
        String snippet = searchService.makeSnippet(html, queryLemmas);
        assertTrue(snippet.contains("<b>тест</b>"));
    }
}
