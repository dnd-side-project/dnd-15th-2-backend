package com.dnd.qello.answer.service.port;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3ObjectStoragePort implements ObjectStoragePort {

	private static final int NOT_FOUND = 404;

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final MediaStorageProperties properties;

	@Override
	public PresignedUpload issuePutUrl(String storageKey, String contentType, Duration ttl) {
		try {
			PutObjectRequest objectRequest = PutObjectRequest.builder()
				.bucket(properties.bucket()).key(storageKey).contentType(contentType).build();
			PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(ttl).putObjectRequest(objectRequest).build();
			PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
			return new PresignedUpload(presigned.url(), presigned.expiration());
		} catch (SdkException exception) {
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "presigned URL 발급에 실패했습니다", exception);
		}
	}

	@Override
	public PresignedView issueGetUrl(String storageKey, Duration ttl) {
		try {
			GetObjectRequest objectRequest = GetObjectRequest.builder()
				.bucket(properties.bucket()).key(storageKey).build();
			GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(ttl).getObjectRequest(objectRequest).build();
			PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
			return new PresignedView(presigned.url(), presigned.expiration());
		} catch (SdkException exception) {
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "조회 URL 발급에 실패했습니다", exception);
		}
	}

	@Override
	public Optional<StoredObjectMetadata> headObject(String storageKey) {
		try {
			HeadObjectResponse response = s3Client.headObject(
				HeadObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
			return Optional.of(new StoredObjectMetadata(response.contentLength(), response.contentType()));
		} catch (NoSuchKeyException exception) {
			return Optional.empty();
		} catch (S3Exception exception) {
			if (exception.statusCode() == NOT_FOUND) {
				return Optional.empty();
			}
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 조회에 실패했습니다", exception);
		} catch (SdkException exception) {
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 조회에 실패했습니다", exception);
		}
	}

	@Override
	public Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes) {
		if (maxBytes <= 0) {
			throw new IllegalArgumentException("maxBytes는 양수여야 합니다");
		}
		try {
			ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(properties.bucket()).key(storageKey).range("bytes=0-" + (maxBytes - 1)).build());
			return Optional.of(response.asByteArray());
		} catch (NoSuchKeyException exception) {
			return Optional.empty();
		} catch (S3Exception exception) {
			if (exception.statusCode() == NOT_FOUND) {
				return Optional.empty();
			}
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 본문 조회에 실패했습니다", exception);
		} catch (SdkException exception) {
			throw new AnswerException(
				AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 본문 조회에 실패했습니다", exception);
		}
	}
}
