package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dao.IndexRepository;
import searchengine.dao.LemmaRepository;
import searchengine.dao.PageRepository;
import searchengine.dao.SiteRepository;
import searchengine.dto.statistics.GenericResponse;
import searchengine.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final List<ForkJoinPool> activePools = new CopyOnWriteArrayList<>();

    @Value("${indexing-settings.user-agent:HeliontSearchBot}")
    private String userAgent;

    @Value("${indexing-settings.referrer:http://www.google.com}")
    private String referrer;

    @Override
    @Transactional
    public GenericResponse startIndexing() {
        if (indexingInProgress.get()) {
            return new GenericResponse(false, "Индексация уже запущена");
        }
        indexingInProgress.set(true);

        for (searchengine.config.Site configSite : sitesList.getSites()) {
            searchengine.model.Site existingSite = siteRepository.findByUrl(configSite.getUrl());
            if (existingSite != null) {
                siteRepository.delete(existingSite);
            }
            searchengine.model.Site siteEntity = new searchengine.model.Site();
            siteEntity.setName(configSite.getName());
            siteEntity.setUrl(configSite.getUrl());
            siteEntity.setStatus(SiteStatus.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(null);
            siteEntity = siteRepository.save(siteEntity);

            ForkJoinPool pool = new ForkJoinPool();
            activePools.add(pool);
            pool.submit(new PageCrawlerTask(siteEntity, configSite.getUrl()));
        }

        CompletableFuture.runAsync(() -> {
            for (ForkJoinPool pool : activePools) {
                pool.shutdown();
                try {
                    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.error("Ожидание завершения пула прервано", e);
                }
            }
            activePools.clear();
            indexingInProgress.set(false);
        });

        return new GenericResponse(true);
    }

    @Override
    @Transactional
    public GenericResponse stopIndexing() {
        if (!indexingInProgress.get()) {
            return new GenericResponse(false, "Индексация не запущена");
        }
        for (ForkJoinPool pool : activePools) {
            pool.shutdownNow();
        }
        activePools.clear();
        indexingInProgress.set(false);

        List<searchengine.model.Site> indexingSites = siteRepository.findByStatus(SiteStatus.INDEXING);
        for (searchengine.model.Site site : indexingSites) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            siteRepository.save(site);
        }
        return new GenericResponse(true);
    }

    @Override
    @Transactional
    public GenericResponse indexPage(String url) {
        searchengine.config.Site matchingConfig = null;
        for (searchengine.config.Site configSite : sitesList.getSites()) {
            if (url.startsWith(configSite.getUrl())) {
                matchingConfig = configSite;
                break;
            }
        }
        if (matchingConfig == null) {
            return new GenericResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        searchengine.model.Site siteEntity = siteRepository.findByUrl(matchingConfig.getUrl());
        if (siteEntity == null) {
            siteEntity = new searchengine.model.Site();
            siteEntity.setName(matchingConfig.getName());
            siteEntity.setUrl(matchingConfig.getUrl());
            siteEntity.setStatus(SiteStatus.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(null);
            siteEntity = siteRepository.save(siteEntity);
        }

        String pagePath = getPath(url, siteEntity.getUrl());

        pageRepository.findBySiteAndPath(siteEntity, pagePath).ifPresent(existingPage -> {
            List<IndexEntity> indexEntities = indexRepository.findByPage(existingPage);
            for (IndexEntity idx : indexEntities) {
                Lemma lemma = idx.getLemma();
                int newFreq = lemma.getFrequency() - 1;
                if (newFreq <= 0) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemma.setFrequency(newFreq);
                    lemmaRepository.save(lemma);
                }
            }
            indexRepository.deleteAll(indexEntities);
            pageRepository.delete(existingPage);
        });

        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(5000);
            Connection.Response response = connection.execute();
            int statusCode = response.statusCode();
            if (statusCode >= 400 && statusCode < 600) {
                return new GenericResponse(false, "Страница вернула ошибку " + statusCode);
            }
            Document doc = connection.get();

            Page page = new Page();
            page.setSite(siteEntity);
            page.setPath(pagePath);
            page.setCode(statusCode);
            page.setContent(doc.html());
            page = pageRepository.save(page);

            processPageIndexing(siteEntity, page, doc.text());

        } catch (Exception e) {
            return new GenericResponse(false, "Ошибка при индексации страницы: " + e.getMessage());
        }

        return new GenericResponse(true);
    }

    /**
     * Метод для извлечения лемм из текста, обновления таблицы lemma и создания записей в index.
     */
    private void processPageIndexing(searchengine.model.Site site, Page page, String text) {
        // Разбиваем текст на токены (можно заменить на использование LemmaFinder, если потребуется)
        String[] tokens = text.toLowerCase().split("\\W+");
        Map<String, Integer> lemmaCount = new HashMap<>();
        log.info("Начинается обработка страницы {}. Найдено токенов: {}", page.getPath(), tokens.length);

        RussianLuceneMorphology morphology;
        try {
            morphology = new RussianLuceneMorphology();
        } catch (Exception ex) {
            log.error("Ошибка при создании экземпляра RussianLuceneMorphology", ex);
            return;
        }

        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) continue;
            // Фильтрация: оставляем только токены, состоящие из строчных кириллических букв
            if (!token.matches("[а-яёa-z]+")) {
                log.debug("Пропускаем токен '{}' - не соответствует шаблону", token);
                continue;
            }
            try {
                List<String> normalForms = morphology.getNormalForms(token);
                if (normalForms.isEmpty()) continue;
                String lemma = normalForms.get(0);
                lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
            } catch (org.apache.lucene.morphology.WrongCharaterException ex) {
                log.debug("Пропускаем токен '{}' из-за ошибки: {}", token, ex.getMessage());
                continue;
            } catch (Exception ex) {
                log.error("Ошибка обработки токена '{}'", token, ex);
            }
        }
        log.info("Сформирован мап лемм для страницы {}: {}", page.getPath(), lemmaCount);

        if (lemmaCount.isEmpty()) {
            log.warn("Для страницы {} не найдено лемм", page.getPath());
            return;
        }

        // Сохраняем леммы и создаем записи в таблице index
        for (Map.Entry<String, Integer> entry : lemmaCount.entrySet()) {
            String lemmaStr = entry.getKey();
            int rank = entry.getValue();

            Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(site, lemmaStr);
            Lemma lemma;
            if (optionalLemma.isPresent()) {
                lemma = optionalLemma.get();
                lemma.setFrequency(lemma.getFrequency() + 1);
            } else {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaStr);
                lemma.setFrequency(1);
            }
            lemma = lemmaRepository.save(lemma);

            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPage(page);
            indexEntity.setLemma(lemma);
            indexEntity.setRank(rank);
            indexRepository.save(indexEntity);
        }
    }

    /**
     * Вспомогательный метод для вычисления относительного пути страницы.
     */
    private String getPath(String fullUrl, String baseUrl) {
        if (fullUrl.startsWith(baseUrl)) {
            String relative = fullUrl.substring(baseUrl.length());
            if (!relative.startsWith("/")) {
                relative = "/" + relative;
            }
            return relative;
        }
        return fullUrl;
    }

    /**
     * Вложенный класс для обхода страниц с использованием Fork–Join.
     * Расширен вызовом processPageIndexing для каждой страницы.
     */
    private class PageCrawlerTask extends RecursiveAction {
        private final searchengine.model.Site site;
        private final String url;

        public PageCrawlerTask(searchengine.model.Site site, String url) {
            this.site = site;
            this.url = url;
        }

        @Override
        protected void compute() {
            if (!indexingInProgress.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                String path = getPath(url, site.getUrl());
                if (pageRepository.existsBySiteAndPath(site, path)) {
                    return;
                }
                long delay = 500 + (long) (Math.random() * 4500);
                Thread.sleep(delay);

                Connection connection = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .referrer(referrer)
                        .timeout(5000);
                Connection.Response response = connection.execute();
                int statusCode = response.statusCode();
                if (statusCode >= 400 && statusCode < 600) {
                    return;
                }
                Document doc = connection.get();

                Page page = new Page();
                page.setSite(site);
                page.setPath(path);
                page.setCode(statusCode);
                page.setContent(doc.html());
                page = pageRepository.save(page);

                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                processPageIndexing(site, page, doc.text());

                Elements links = doc.select("a[href]");
                List<PageCrawlerTask> tasks = new ArrayList<>();
                for (Element link : links) {
                    String absUrl = link.absUrl("href");
                    if (absUrl.isEmpty()) continue;
                    if (!absUrl.startsWith(site.getUrl())) continue;
                    String linkPath = getPath(absUrl, site.getUrl());
                    if (pageRepository.existsBySiteAndPath(site, linkPath)) continue;
                    tasks.add(new PageCrawlerTask(site, absUrl));
                }
                invokeAll(tasks);
            } catch (Exception e) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError(e.getMessage());
                siteRepository.save(site);
            }
        }
    }
}
