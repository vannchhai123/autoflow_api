package com.autoclip.domain.asset.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class S3StorageService implements StorageService {

    private final String bucket;
    private final String region;

    public S3StorageService(@Value("${autoclip.storage.bucket}") String bucket,
                            @Value("${autoclip.storage.region}") String region) {
        this.bucket = bucket;
        this.region = region;
    }

    @Override
    public String upload(String key, InputStream content, long sizeBytes, String contentType) {
        log.debug("Uploading {} ({} bytes) to bucket {}", key, sizeBytes, bucket);
        
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    @Override
    public InputStream download(String key) {
        
        throw new UnsupportedOperationException("S3 download not implemented yet");
    }

    @Override
    public void delete(String key) {
        
        log.debug("Deleting {} from bucket {}", key, bucket);
    }
}
