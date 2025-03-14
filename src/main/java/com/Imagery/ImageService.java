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

        List<S3Object> allObjects = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .maxKeys(size * (page + 1)); // Fetch more than needed to ensure proper sorting

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            allObjects.addAll(response.contents());

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null && allObjects.size() < size * (page + 1));

        // Sort images by latest upload
        List<S3Object> sortedObjects = allObjects.stream()
                .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                .collect(Collectors.toList());

        // Paginate properly
        int start = page * size;
        int end = Math.min(start + size, sortedObjects.size());

        imageUrls = sortedObjects.subList(start, end).stream()
                .filter(s3Object -> isImage(s3Object.key()))
                .map(obj -> generatePresignedUrl(obj.key()))
                .collect(Collectors.toList());

        result.put("images", imageUrls);
        result.put("totalPages", (int) Math.ceil((double) countTotalImages() / size));
        result.put("currentPage", page);
        result.put("hasNextPage", end < sortedObjects.size());

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
    public String deleteImage(String objectKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            return "deleted";
        } catch (S3Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

}

