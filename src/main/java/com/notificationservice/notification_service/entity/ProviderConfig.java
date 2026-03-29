package com.notificationservice.notification_service.entity;

import com.notificationservice.notification_service.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "provider_config", uniqueConstraints = {
        @UniqueConstraint(name = "uq_provider_config_channel_name", columnNames = {"channel", "provider_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private ChannelType channel;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "priority", nullable = false)
    private int priority;  // 1 = primary, 2 = fallback

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "weight", nullable = false)
    @Builder.Default
    private int weight = 100;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "rate_limit_rps")
    private Integer rateLimitRps;
}
