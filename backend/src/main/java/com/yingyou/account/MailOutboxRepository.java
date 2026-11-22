package com.yingyou.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, UUID> {
}
