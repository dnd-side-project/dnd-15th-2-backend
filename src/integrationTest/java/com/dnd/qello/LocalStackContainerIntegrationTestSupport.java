package com.dnd.qello;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Created at: 2026-08-07T03:20:00+09:00
 * Source scenario: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE (LocalStack 지원 클래스)
 */
abstract class LocalStackContainerIntegrationTestSupport extends PostgisContainerIntegrationTestSupport {

	static final String TEST_BUCKET = "qello-test-media";

	@Container
	static final LocalStackContainer localstack = new LocalStackContainer(
		DockerImageName.parse("localstack/localstack:3.8"))
		.withServices(LocalStackContainer.Service.S3);

	@DynamicPropertySource
	static void registerMediaProperties(DynamicPropertyRegistry registry) {
		registry.add("qello.media.bucket", () -> TEST_BUCKET);
		registry.add("qello.media.s3.region", localstack::getRegion);
		registry.add("qello.media.s3.endpoint-override", () -> localstack.getEndpoint().toString());
		registry.add("qello.media.s3.access-key", localstack::getAccessKey);
		registry.add("qello.media.s3.secret-key", localstack::getSecretKey);
	}

	@BeforeAll
	static void createTestBucket() {
		try (S3Client client = testS3Client()) {
			boolean exists = client.listBuckets().buckets().stream()
				.anyMatch(bucket -> bucket.name().equals(TEST_BUCKET));
			if (!exists) {
				client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
			}
		}
	}

	static S3Client testS3Client() {
		return S3Client.builder()
			.endpointOverride(localstack.getEndpoint())
			.region(Region.of(localstack.getRegion()))
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.forcePathStyle(true)
			.build();
	}
}
