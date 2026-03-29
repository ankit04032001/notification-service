package com.notificationservice.notification_service.provider;

import com.notificationservice.notification_service.config.NotificationMetrics;
import com.notificationservice.notification_service.dto.NotificationEvent;
import com.notificationservice.notification_service.entity.ProviderConfig;
import com.notificationservice.notification_service.enums.ChannelType;
import com.notificationservice.notification_service.repository.ProviderConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes notifications to providers using primary -> fallback strategy.
 * Provider ordering comes from provider_config table. Resilience (CB + retry)
 * is handled by Resilience4j AOP on each provider's send() method.
 */
@Component
@Slf4j
public class ProviderStrategyFactory {

    private final Map<String, NotificationProvider> providersByName;
    private final ProviderConfigRepository providerConfigRepository;
    private final NotificationMetrics metrics;

    public ProviderStrategyFactory(List<NotificationProvider> providers,
                                   ProviderConfigRepository providerConfigRepository,
                                   NotificationMetrics metrics) {
        this.providersByName = providers.stream()
                .collect(Collectors.toMap(NotificationProvider::getProviderName, Function.identity()));
        this.providerConfigRepository = providerConfigRepository;
        this.metrics = metrics;
        log.info("Registered notification providers: {}", providersByName.keySet());
    }

    public ProviderResult dispatch(NotificationEvent event) {
        ChannelType channel = event.getChannel();
        List<ProviderConfig> configs = providerConfigRepository
                .findByChannelAndIsActiveTrueOrderByPriorityAsc(channel);

        if (configs.isEmpty()) {
            throw new IllegalStateException("No active providers configured for channel: " + channel);
        }

        ProviderResult lastResult = null;

        for (ProviderConfig config : configs) {
            NotificationProvider provider = providersByName.get(config.getProviderName());
            if (provider == null) {
                log.warn("Provider bean not found for configured provider: {}", config.getProviderName());
                continue;
            }

            log.debug("Attempting delivery via {}: notificationId={}", config.getProviderName(), event.getNotificationId());
            ProviderResult result = provider.send(event);

            if (result.isSuccess()) {
                metrics.incrementProviderSuccess(config.getProviderName(), channel.name());
                return result;
            }

            metrics.incrementProviderFailure(config.getProviderName(), channel.name());
            log.warn("Provider {} failed: notificationId={}, error={}",
                    config.getProviderName(), event.getNotificationId(), result.getErrorMessage());
            lastResult = result;
        }

        return lastResult != null ? lastResult : ProviderResult.failure("No providers could deliver for channel: " + channel);
    }

    public String   resolveProviderName(NotificationEvent event) {
        List<ProviderConfig> configs = providerConfigRepository
                .findByChannelAndIsActiveTrueOrderByPriorityAsc(event.getChannel());
        return configs.isEmpty() ? "UNKNOWN" : configs.get(0).getProviderName();
    }
}
