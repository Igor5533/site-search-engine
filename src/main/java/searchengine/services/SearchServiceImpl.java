package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dao.IndexRepository;
import searchengine.dao.LemmaRepository;
import searchengine.dao.PageRepository;
import searchengine.dao.SiteRepository;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.util.LemmaFinder;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    @Transactional
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();

        List<Site> sites;
        if (siteUrl != null && !siteUrl.isBlank()) {
            Site site = siteRepository.findByUrl(siteUrl);
            if (site == null || site.getStatus() != SiteStatus.INDEXED) {
                response.setResult(false);
                response.setError("Сайт не найден или не проиндексирован");
                return response;
            }
            sites = Collections.singletonList(site);
        } else {
            sites = siteRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SiteStatus.INDEXED)
                    .collect(Collectors.toList());
            if (sites.isEmpty()) {
                response.setResult(false);
                response.setError("Нет доступных проиндексированных сайтов");
                return response;
            }
        }

        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (Exception e) {
            log.error("Ошибка инициализации LemmaFinder", e);
            response.setResult(false);
            response.setError("Ошибка лемматизации");
            return response;
        }
        Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);

        if (queryLemmas.isEmpty()) {
            response.setResult(false);
            response.setError("Не удалось выделить леммы из поискового запроса");
            return response;
        }

        float threshold = 0.8f;
        Set<String> filteredLemmas = new HashSet<>();
        for (String lemma : queryLemmas) {
            int totalCount = 0;
            int totalPages = 0;
            for (Site s : sites) {
                totalPages += pageRepository.countBySite(s);
                Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(s, lemma);
                if (optionalLemma.isPresent()) {
                    totalCount += optionalLemma.get().getFrequency();
                }
            }
            // Проверяем отношение
            if (totalPages == 0) {
                continue;
            }
            float ratio = (float) totalCount / (float) totalPages;
            if (ratio < threshold) {
                filteredLemmas.add(lemma);
            }
        }
        if (filteredLemmas.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(Collections.emptyList());
            return response;
        }

        List<String> sortedLemmas = filteredLemmas.stream()
                .sorted(Comparator.comparingInt(lemma -> getLemmaFrequencySum(lemma, sites)))
                .collect(Collectors.toList());

        Set<Page> resultPages = null;
        for (String lemma : sortedLemmas) {
            Set<Page> lemmaPages = findPagesByLemma(lemma, sites);
            if (resultPages == null) {
                resultPages = lemmaPages;
            } else {
                resultPages.retainAll(lemmaPages);
            }
            if (resultPages.isEmpty()) {
                break;
            }
        }

        if (resultPages == null || resultPages.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(Collections.emptyList());
            return response;
        }

        List<PageRelevance> relevanceList = new ArrayList<>();
        float maxAbsRelevance = 0;

        for (Page page : resultPages) {
            float absRelevance = 0;
            for (String lemma : sortedLemmas) {
                Optional<Lemma> lemOpt = lemmaRepository.findBySiteAndLemma(page.getSite(), lemma);
                if (lemOpt.isEmpty()) {
                    continue;
                }
                Lemma lem = lemOpt.get();
                IndexEntity idx = indexRepository
                        .findByPage(page)
                        .stream()
                        .filter(i -> i.getLemma().getId() == lem.getId())
                        .findFirst()
                        .orElse(null);
                if (idx != null) {
                    absRelevance += idx.getRank();
                }
            }
            if (absRelevance > maxAbsRelevance) {
                maxAbsRelevance = absRelevance;
            }
            relevanceList.add(new PageRelevance(page, absRelevance));
        }

        List<SearchData> dataList = new ArrayList<>();
        for (PageRelevance pr : relevanceList) {
            if (pr.absRelevance == 0) {
                continue;
            }
            float rel = pr.absRelevance / maxAbsRelevance;

            SearchData data = new SearchData();
            data.setSite(pr.page.getSite().getUrl());
            data.setSiteName(pr.page.getSite().getName());
            data.setUri(pr.page.getPath());
            data.setTitle(extractTitle(pr.page.getContent()));
            data.setSnippet(makeSnippet(pr.page.getContent(), queryLemmas));
            data.setRelevance(rel);

            dataList.add(data);
        }

        dataList.sort(Comparator.comparing(SearchData::getRelevance).reversed());

        int totalCount = dataList.size();
        int toIndex = Math.min(offset + limit, totalCount);
        if (offset > totalCount) {
            dataList = Collections.emptyList();
        } else {
            dataList = dataList.subList(offset, toIndex);
        }

        // 10) Формируем ответ
        response.setResult(true);
        response.setCount(totalCount);
        response.setData(dataList);
        return response;
    }

    /**
     * Возвращает суммарный frequency леммы по всем сайтам (для сортировки).
     */
    int getLemmaFrequencySum(String lemma, List<Site> sites) {
        int sum = 0;
        for (Site site : sites) {
            Optional<Lemma> optional = lemmaRepository.findBySiteAndLemma(site, lemma);
            if (optional.isPresent()) {
                sum += optional.get().getFrequency();
            }
        }
        return sum;
    }

    /**
     * Находит все страницы, на которых встречается лемма, по всем сайтам из списка.
     */
    Set<Page> findPagesByLemma(String lemma, List<Site> sites) {
        Set<Page> result = new HashSet<>();
        for (Site site : sites) {
            Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(site, lemma);
            if (optionalLemma.isPresent()) {
                Lemma l = optionalLemma.get();
                List<IndexEntity> idxList = indexRepository.findAll()
                        .stream()
                        .filter(i -> i.getLemma().getId() == l.getId())
                        .collect(Collectors.toList());
                for (IndexEntity idx : idxList) {
                    result.add(idx.getPage());
                }
            }
        }
        return result;
    }

    /**
     * Простой метод для извлечения заголовка из HTML-кода.
     * Можно улучшить, используя Jsoup.
     */
    String extractTitle(String html) {
        String lower = html.toLowerCase();
        int start = lower.indexOf("<title>");
        int end = lower.indexOf("</title>");
        if (start == -1 || end == -1 || start > end) {
            return "";
        }
        return html.substring(start + 7, end).replaceAll("\\s+", " ").trim();
    }

    /**
     * Примерная логика формирования сниппета:
     * Ищем первые вхождения слов из query в тексте и обрезаем вокруг.
     * В реальном проекте лучше использовать Jsoup + регулярки + более сложную логику.
     */
    String makeSnippet(String html, Set<String> queryLemmas) {
        String text = html.replaceAll("<[^>]+>", " ");
        text = text.trim().replaceAll("\\s+", " ");
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }
        for (String q : queryLemmas) {
            text = text.replaceAll("(?i)\\b" + q + "\\b", "<b>" + q + "</b>");
        }
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }

    /**
     * Вспомогательный класс для хранения пары (Page, absRelevance).
     */
    private static class PageRelevance {
        private final Page page;
        private final float absRelevance;

        public PageRelevance(Page page, float absRelevance) {
            this.page = page;
            this.absRelevance = absRelevance;
        }
    }
}
