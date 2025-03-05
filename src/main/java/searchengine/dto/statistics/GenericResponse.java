package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenericResponse {
    private boolean result;
    private String error;

    public GenericResponse(boolean result) {
        this.result = result;
    }
}
