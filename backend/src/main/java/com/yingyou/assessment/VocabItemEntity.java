package com.yingyou.assessment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocab_item_version")
public class VocabItemEntity {

    @Id
    private String id;

    protected VocabItemEntity() {
    }
}
