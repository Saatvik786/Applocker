package com.applocker.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "applocker_rule")
class ApplockerRuleEntity {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "collection_type", nullable = false, length = 16)
    private String collectionType;

    @Column(name = "rule_kind", nullable = false, length = 16)
    private String ruleKind;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "user_or_group_sid", nullable = false, length = 64)
    private String userOrGroupSid;

    @Column(length = 260)
    private String path;

    @Column(name = "publisher_name", length = 300)
    private String publisherName;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "binary_name", length = 200)
    private String binaryName;

    @Column(name = "binary_version_low", length = 40)
    private String binaryVersionLow;

    @Column(name = "binary_version_high", length = 40)
    private String binaryVersionHigh;

    @Column(name = "hash_data", length = 200)
    private String hashData;

    @Column(name = "source_file_name", length = 260)
    private String sourceFileName;

    @Column(name = "source_file_length")
    private Long sourceFileLength;

    @Column(nullable = false, length = 8)
    private String action;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    ApplockerRuleEntity() {
    }

    UUID id() {
        return id;
    }

    UUID versionId() {
        return versionId;
    }

    String collectionType() {
        return collectionType;
    }

    String ruleKind() {
        return ruleKind;
    }

    String name() {
        return name;
    }

    String userOrGroupSid() {
        return userOrGroupSid;
    }

    String path() {
        return path;
    }

    String publisherName() {
        return publisherName;
    }

    String productName() {
        return productName;
    }

    String binaryName() {
        return binaryName;
    }

    String binaryVersionLow() {
        return binaryVersionLow;
    }

    String binaryVersionHigh() {
        return binaryVersionHigh;
    }

    String hashData() {
        return hashData;
    }

    String sourceFileName() {
        return sourceFileName;
    }

    Long sourceFileLength() {
        return sourceFileLength;
    }

    String action() {
        return action;
    }

    int sortOrder() {
        return sortOrder;
    }

    void id(UUID id) {
        this.id = id;
    }

    void versionId(UUID versionId) {
        this.versionId = versionId;
    }

    void collectionType(String collectionType) {
        this.collectionType = collectionType;
    }

    void ruleKind(String ruleKind) {
        this.ruleKind = ruleKind;
    }

    void name(String name) {
        this.name = name;
    }

    void userOrGroupSid(String userOrGroupSid) {
        this.userOrGroupSid = userOrGroupSid;
    }

    void path(String path) {
        this.path = path;
    }

    void publisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    void productName(String productName) {
        this.productName = productName;
    }

    void binaryName(String binaryName) {
        this.binaryName = binaryName;
    }

    void binaryVersionLow(String binaryVersionLow) {
        this.binaryVersionLow = binaryVersionLow;
    }

    void binaryVersionHigh(String binaryVersionHigh) {
        this.binaryVersionHigh = binaryVersionHigh;
    }

    void hashData(String hashData) {
        this.hashData = hashData;
    }

    void sourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    void sourceFileLength(Long sourceFileLength) {
        this.sourceFileLength = sourceFileLength;
    }

    void action(String action) {
        this.action = action;
    }

    void sortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
