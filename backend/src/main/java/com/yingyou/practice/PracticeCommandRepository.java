package com.yingyou.practice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeCommandRepository extends JpaRepository<PracticeCommand, PracticeCommandId> {
}
