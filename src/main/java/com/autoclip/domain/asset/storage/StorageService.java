package com.autoclip.domain.asset.storage;

import java.io.InputStream;

public interface StorageService {

    String upload(String key, InputStream content, long sizeBytes, String contentType);

    InputStream download(String key);

    void delete(String key);
}
