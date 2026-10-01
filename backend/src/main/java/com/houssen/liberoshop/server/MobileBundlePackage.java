package com.houssen.liberoshop.server;

/**
 * The mobile pages as a phone downloads them, and what it needs to decide whether to.
 *
 * @param version        the first 16 hex digits of the checksum: short enough to read in a log
 * @param checksum       SHA-256 of the zip, hex -- the phone checks it after download
 * @param minNativeBuild the oldest APK {@code versionCode} these pages run in
 * @param fileCount      how many files the zip holds, for the log
 */
public record MobileBundlePackage(String version, String checksum, int minNativeBuild, int fileCount, byte[] zip) {
}
