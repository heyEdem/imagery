package com.Imagery;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageService {
    private final AmazonS3 s3Client;

    public void uploadImage(MultipartFile file) throws IOException{
        s3Client.putObject("imagery-app", file.getOriginalFilename(), file.getInputStream(), null);
    }

    public List<String> listImages(int page, int size) {
        ObjectListing objectListing = s3Client.listObjects("imagery-app");
        List<String> imageUrls = new ArrayList<>();
        String bucketName = "imagery-app";


        int start = page * size;  // Calculate starting index
        int end = start + size;   // Calculate end index
        int index = 0;

        while (objectListing != null) {
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                if (index >= start && index < end) {
                    imageUrls.add(s3Client.getUrl(bucketName, objectSummary.getKey()).toString());
                }
                index++;
                if (index >= end) return imageUrls;  // Stop when page is full
            }
            if (objectListing.isTruncated()) {
                objectListing = s3Client.listNextBatchOfObjects(objectListing);
            } else {
                break;
            }
        }
        return imageUrls;
        //todo Implement pagination logic
        // Return only the items for the requested page
//        return imageUrls; // Adjust this for pagination
    }
}
