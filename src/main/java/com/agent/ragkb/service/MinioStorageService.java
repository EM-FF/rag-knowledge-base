package com.agent.ragkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    public byte[] download(String objectPath) {
        throw new UnsupportedOperationException("MinIO 完整实现见下一节");
    }

    public void delete(String objectPath) {
        throw new UnsupportedOperationException("MinIO 完整实现见下一节");
    }
}
