package com.notificationservice.notification_service.repository;

import com.notificationservice.notification_service.entity.ProviderConfig;
import com.notificationservice.notification_service.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {

    List<ProviderConfig> findByChannelAndIsActiveTrueOrderByPriorityAsc(ChannelType channel);

    List<ProviderConfig> findByChannelOrderByPriorityAsc(ChannelType channel);
}
