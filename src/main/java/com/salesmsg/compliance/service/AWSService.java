package com.salesmsg.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;

/**
 * Service for interacting with AWS services, particularly S3 for file storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AWSService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    /**
     * Upload a file to S3.
     *
     * @param file The file to upload
     * @param key The S3 key (path)
     * @return The URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String key) {

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Uploaded file to S3: {} ({})", key, file.getSize());

            // Generate the URL
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);

        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Delete a file from S3.
     *
     * @param key The S3 key (path)
     */
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);

            log.info("Deleted file from S3: {}", key);

        } catch (Exception e) {
            log.error("Error deleting file from S3", e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * Generate a pre-signed URL for temporary access to a file.
     *
     * @param key The S3 key (path)
     * @param expirationMinutes The expiration time in minutes
     * @return The pre-signed URL
     */
    public URL generatePresignedUrl(String key, int expirationMinutes) {
        // Note: Using presigned URLs requires the S3Presigner client,
        // which is different from the standard S3Client.
        // This is a placeholder for demonstration purposes.

        throw new UnsupportedOperationException("Presigned URL generation not implemented");
    }

    /**
     * Generate a unique file key for S3.
     *
     * @param folder The folder path
     * @param originalFilename The original filename
     * @return A unique S3 key
     */
    public String generateUniqueFileKey(String folder, String originalFilename) {
        String extension = getFileExtension(originalFilename);
        return folder + "/" + UUID.randomUUID() + extension;
    }

    /**
     * Get the file extension from a filename.
     *
     * @param filename The filename
     * @return The file extension (with dot)
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}