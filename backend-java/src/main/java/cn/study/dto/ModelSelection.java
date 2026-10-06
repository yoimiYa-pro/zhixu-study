package cn.study.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ModelSelection {
    private ModelSelection() {}
    public record Catalog(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String providerId,
                          @NotNull @Size(max=200) String defaultModel,
                          @NotNull @Size(max=1000) List<@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") String> models,
                          String message) {}
    public record Choice(@Size(max=200) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") String model,
                         UUID connectionId, @NotNull @PositiveOrZero Long expectedRevision) {
        public Choice(String model, Long expectedRevision) { this(model, null, expectedRevision); }
    }
    public record ConnectionInput(@NotBlank @Size(max=100) String provider,
                                  @NotBlank @Size(max=2048) String baseUrl,
                                  @Size(max=4096) @Pattern(regexp="[\\x21-\\x7E]*") String apiKey,
                                  @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") String model,
                                  Boolean jsonMode, @NotNull @PositiveOrZero Long expectedRevision) {
        @Override public String toString() { return "ConnectionInput[credentials=REDACTED]"; }
    }
    public record Revision(@NotNull @PositiveOrZero Long expectedRevision) {}
    public record Connection(UUID id, String provider, String baseUrl, String model, boolean jsonMode, boolean hasApiKey) {}
    public record Probe(@AssertTrue boolean available,
                        @NotBlank @Pattern(regexp="[a-f0-9]{64}") String providerId,
                        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") String model) {}
    public record View(String activeModel, String selectedModel, String defaultModel,
                       List<String> models, long revision, UUID activeConnectionId,
                       List<Connection> connections, String catalogMessage) {}
}
