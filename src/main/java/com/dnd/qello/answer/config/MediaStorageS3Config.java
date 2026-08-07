package com.dnd.qello.answer.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * endpoint-override/access-key는 LocalStack 통합 테스트 전용이다. 값이 비어 있으면(운영/local
 * 프로필) 실제 AWS 엔드포인트와 SDK 기본 자격 증명 체인을 그대로 사용한다. 애플리케이션
 * 런타임 IAM Role은 아직 없으므로(별도 이슈) local 프로필에서 이 빈이 실제로 호출되는
 * 경로는 없다 — presigned URL 발급 자체는 이번 이슈 범위 밖의 controller에서만 호출된다.
 */
@Configuration
public class MediaStorageS3Config {

	@Bean
	public S3Client mediaS3Client(
		@Value("${qello.media.s3.region}") String region,
		@Value("${qello.media.s3.endpoint-override:}") String endpointOverride,
		@Value("${qello.media.s3.access-key:}") String accessKey,
		@Value("${qello.media.s3.secret-key:}") String secretKey) {
		return configure(S3Client.builder(), region, endpointOverride, accessKey, secretKey).build();
	}

	@Bean
	public S3Presigner mediaS3Presigner(
		@Value("${qello.media.s3.region}") String region,
		@Value("${qello.media.s3.endpoint-override:}") String endpointOverride,
		@Value("${qello.media.s3.access-key:}") String accessKey,
		@Value("${qello.media.s3.secret-key:}") String secretKey) {
		S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
		if (!endpointOverride.isBlank()) {
			builder.endpointOverride(URI.create(endpointOverride));
		}
		if (!accessKey.isBlank()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
		}
		return builder.build();
	}

	private S3ClientBuilder configure(
		S3ClientBuilder builder, String region, String endpointOverride, String accessKey, String secretKey) {
		builder.region(Region.of(region));
		if (!endpointOverride.isBlank()) {
			// LocalStack은 virtual-hosted-style DNS 해석을 지원하지 않아 path-style이 필요하다.
			builder.endpointOverride(URI.create(endpointOverride)).forcePathStyle(true);
		}
		if (!accessKey.isBlank()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
		}
		return builder;
	}
}
