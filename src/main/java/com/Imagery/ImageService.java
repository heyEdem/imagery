package com.Imagery;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageService {

    @Autowired
    private  S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    private String BUCKET_NAME = "imagery-app";

    // Map to store pagination state
    private Map<Integer, String> pageTokenMap = new HashMap<>();

    public Map<String, Object> getImages(int page, int size) {
        Map<String, Object> result = new HashMap<>();
        List<String> imageUrls = new ArrayList<>();

        // Get continuation token for the requested page
        String continuationToken = page > 0 ? pageTokenMap.get(page - 1) : null;

        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .maxKeys(size);

        if (continuationToken != null && !continuationToken.isEmpty()) {
            requestBuilder.continuationToken(continuationToken);
        }

        ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

        // Process images and generate presigned URLs
        List<S3Object> objects = response.contents();
        imageUrls = objects.stream()
                .filter(s3Object -> isImage(s3Object.key()))
                .map(obj -> generatePresignedUrl(obj.key()))
                .collect(Collectors.toList());

        // Store the next continuation token for subsequent pages
        if (response.isTruncated()) {
            pageTokenMap.put(page, response.nextContinuationToken());
            result.put("hasNextPage", true);
        } else {
            result.put("hasNextPage", false);
        }

        // Calculate total count for proper pagination display
        long totalCount = countTotalImages();
        int totalPages = (int) Math.ceil((double) totalCount / size);

        result.put("images", imageUrls);
        result.put("totalPages", Math.max(1, totalPages));
        result.put("currentPage", page);

        return result;
    }


    private long countTotalImages() {
        long count = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME);

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            count += response.contents().stream()
                    .filter(s3Object -> isImage(s3Object.key()))
                    .count();

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        return count;
    }

    public String generatePresignedUrl(String objectKey) {
        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(objectKey)
                        .build())
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedGetObjectRequest.url().toString();
    }

    private boolean isImage(String key) {
        return key.toLowerCase().endsWith(".jpg") ||
                key.toLowerCase().endsWith(".jpeg") ||
                key.toLowerCase().endsWith(".png");
    }

    public String uploadMultipleFiles(MultipartFile[] files) throws IOException {
        // Filter out empty files
        List<MultipartFile> nonEmptyFiles = Arrays.stream(files)
                .filter(file -> !file.isEmpty())
                .collect(Collectors.toList());

        // If all files were empty, return an appropriate response
        if (nonEmptyFiles.isEmpty()) {
            return "empty";
        }

        if (nonEmptyFiles.size() > 5) {
            return "max";
        }

        for (MultipartFile file : nonEmptyFiles) {
            // check file size
            if (file.getSize() > 1000000) {
                return "size";
            }
            String key = "image_" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        }

        return "success";
    }

    public boolean deleteImage(String imageKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(imageKey)
                    .build();

            System.out.println(s3Client.deleteObject(deleteObjectRequest));
            return true;
        } catch (S3Exception e) {
            e.printStackTrace();
            System.out.println("false");
            return false;
        }
    }


}

