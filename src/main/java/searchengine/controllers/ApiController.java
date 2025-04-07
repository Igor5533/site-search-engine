package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.GenericResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<GenericResponse> startIndexing() {
        GenericResponse response = indexingService.startIndexing();
        if (!response.isResult()) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            String error = response.getError();
            if (error != null) {
                if (error.startsWith("Индексация уже запущена") || error.startsWith("Индексация не запущена")
                        || error.startsWith("Данная страница находится за пределами")) {
                    status = HttpStatus.BAD_REQUEST;
                }
                if (error.startsWith("Ошибка базы данных") || error.startsWith("Ошибка при работе с базой данных")) {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
            }
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<GenericResponse> stopIndexing() {
        GenericResponse response = indexingService.stopIndexing();
        if (!response.isResult()) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            String error = response.getError();
            if (error != null) {
                if (error.startsWith("Индексация не запущена")) {
                    status = HttpStatus.BAD_REQUEST;
                }
                if (error.startsWith("Ошибка базы данных") || error.startsWith("Ошибка при работе с базой данных")) {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
            }
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<GenericResponse> indexPage(@RequestParam("url") String url) {
        GenericResponse response = indexingService.indexPage(url);
        if (!response.isResult()) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            String error = response.getError();
            if (error != null) {
                if (error.startsWith("Данная страница находится за пределами")) {
                    status = HttpStatus.BAD_REQUEST;
                }
                if (error.startsWith("Ошибка базы данных") || error.startsWith("Ошибка при работе с базой данных")
                        || error.startsWith("Ошибка при индексации страницы")) {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
            }
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new GenericResponse(false, "Задан пустой поисковый запрос")
            );
        }
        SearchResponse response = searchService.search(query, site, offset, limit);
        if (!response.isResult()) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            String error = response.getError();
            if (error != null) {
                if (error.startsWith("Сайт не найден") || error.startsWith("Нет доступных проиндексированных сайтов")
                        || error.startsWith("Не удалось выделить леммы")) {
                    status = HttpStatus.BAD_REQUEST;
                }
                if (error.startsWith("Ошибка лемматизации") || error.startsWith("Ошибка при работе с базой данных")) {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
            }
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }
}