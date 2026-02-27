package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.entity.SystemConfig;
import com.lamb.springaiknowledgeserver.repository.SystemConfigRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final Object cacheLock = new Object();

    @Value("${app.config.cache-ttl-ms:2000}")
    private long cacheTtlMs;

    private volatile long lastLoadedAt;
    private volatile Map<String, SystemConfig> cache = Collections.emptyMap();

    public String getString(String key, String defaultValue) {
        SystemConfig config = loadCache().get(key);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return defaultValue;
        }
        return config.getConfigValue();
    }

    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> listAll() {
        return systemConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<SystemConfig> findByKey(String key) {
        return systemConfigRepository.findByConfigKey(key);
    }

    @Transactional
    public SystemConfig upsert(String key, String value, String description) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
            .orElseGet(SystemConfig::new);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        SystemConfig saved = systemConfigRepository.save(config);
        refresh();
        return saved;
    }

    public void refresh() {
        lastLoadedAt = 0;
    }

    private Map<String, SystemConfig> loadCache() {
        long now = System.currentTimeMillis();
        if (now - lastLoadedAt < cacheTtlMs && !cache.isEmpty()) {
            return cache;
        }
        synchronized (cacheLock) {
            now = System.currentTimeMillis();
            if (now - lastLoadedAt < cacheTtlMs && !cache.isEmpty()) {
                return cache;
            }
            List<SystemConfig> all = systemConfigRepository.findAll();
            Map<String, SystemConfig> map = new HashMap<>();
            for (SystemConfig config : all) {
                map.put(config.getConfigKey(), config);
            }
            cache = Collections.unmodifiableMap(map);
            lastLoadedAt = now;
            return cache;
        }
    }
}
