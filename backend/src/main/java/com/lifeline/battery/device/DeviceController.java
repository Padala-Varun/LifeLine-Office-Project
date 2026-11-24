package com.lifeline.battery.device;

import com.lifeline.battery.config.AttemptLimiter;
import com.lifeline.battery.config.LifelineProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    public record RegisterRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 60) String platform,
                                  @Size(max = 40) String joinCode) {
    }

    public record ReadingRequest(@NotNull UUID deviceId, @NotNull @Min(0) @Max(100) Integer level,
                                 @NotNull Boolean charging) {
    }

    public record ReadingResponse(boolean alertOpen) {
    }

    private static final String BEARER = "Bearer ";

    private final DeviceService service;
    private final LifelineProperties properties;
    private final AttemptLimiter limiter;

    public DeviceController(DeviceService service, LifelineProperties properties, AttemptLimiter limiter) {
        this.service = service;
        this.properties = properties;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceService.Registration register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        checkJoinCode(request.joinCode(), http.getRemoteAddr());
        return service.register(request.name(), request.platform());
    }

    @PostMapping("/readings")
    public ReadingResponse reading(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ReadingRequest request) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER.length());
        return new ReadingResponse(service.record(request.deviceId(), token, request.level(), request.charging()));
    }

    /** Without lifeline.join-code, any device may join. */
    private void checkJoinCode(String code, String address) {
        String expected = properties.joinCode();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String key = "join " + address;
        if (!limiter.allow(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        if (!MessageDigest.isEqual(normalize(code), normalize(expected))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        limiter.succeeded(key);
    }

    private static byte[] normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
