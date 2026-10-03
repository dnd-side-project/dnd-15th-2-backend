/**
 * Created at: 2026-10-02T17:05:33+09:00
 * Source scenario: TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-001 through INT-007,
 * INT-009, INT-010 (added 2026-10-02T17:33:49+09:00)
 */
package com.dnd.qello;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Feed170TestClockConfiguration.class)
class FeedMediaViewUrlIntegrationTest extends LocalStackContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final byte[] PNG = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
			0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
	};

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private MediaStorageProperties properties;
	@Autowired
	private Feed170MutableClock clock;

	private Feed170IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;
	private long postId;
	private long postRecipientId;
	private long answerId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		// media_asset은 user_account를 ON DELETE RESTRICT로 참조하므로 계정 정리 전에 지운다.
		// 첨부를 먼저 지워도 질문글·답변에 본문이 있어 ct_media_attachment_preserves_content를 통과한다.
		jdbc.update("""
				DELETE FROM media_attachment WHERE owner_id IN
					(SELECT id FROM user_account WHERE coarse_region_code = 'TEST-FEED170')
				""");
		jdbc.update("""
				DELETE FROM media_asset WHERE owner_id IN
					(SELECT id FROM user_account WHERE coarse_region_code = 'TEST-FEED170')
				""");
		fixtures = new Feed170IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("gh300-sender");
		recipientId = fixtures.account("gh300-recipient");
		postId = fixtures.post(senderId, "gh300-post", NOW.plusSeconds(3600));
		postRecipientId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		answerId = fixtures.answer(postRecipientId, recipientId, "gh300-answer", NOW.minusSeconds(10));
	}

	@Test
	@DisplayName("INT-001 받은 질문 목록은 READY 첨부의 mediaId·조회 URL·만료 시각을 내리고 storage key와 버킷 이름은 내리지 않는다")
	void inboxListingExposesMediaViewUrl() throws Exception {
		Attached media = attachToPost(0);

		assertMediaContract(inboxCard(), media);
	}

	@Test
	@DisplayName("INT-002 보낸 질문 목록은 READY 첨부의 mediaId·조회 URL·만료 시각을 내리고 storage key와 버킷 이름은 내리지 않는다")
	void sentPostListingExposesMediaViewUrl() throws Exception {
		Attached media = attachToPost(0);

		assertMediaContract(sentPostCard(), media);
	}

	@Test
	@DisplayName("INT-003 답변 목록은 READY 첨부의 mediaId·조회 URL·만료 시각을 내리고 storage key와 버킷 이름은 내리지 않는다")
	void answerListingExposesMediaViewUrl() throws Exception {
		Attached media = attachToAnswer(0);

		assertMediaContract(answerItem(), media);
	}

	@Test
	@DisplayName("INT-004 첨부 자산이 DELETED면 세 목록 모두 카드는 남기고 media를 빈 배열로 내린다")
	void nonReadyMediaIsExcludedFromAllListings() throws Exception {
		Attached postMedia = attachToPost(0);
		Attached answerMedia = attachToAnswer(0);
		markDeleted(postMedia.mediaId());
		markDeleted(answerMedia.mediaId());

		assertThat(inboxCard().path("postId").asLong()).isEqualTo(postId);
		assertThat(inboxCard().path("media")).isEmpty();
		assertThat(sentPostCard().path("postId").asLong()).isEqualTo(postId);
		assertThat(sentPostCard().path("media")).isEmpty();
		assertThat(answerItem().path("answerId").asLong()).isEqualTo(answerId);
		assertThat(answerItem().path("media")).isEmpty();
	}

	@Test
	@DisplayName("INT-005 media 순서는 media_attachment.display_order 순서다")
	void mediaFollowsDisplayOrder() throws Exception {
		Attached second = attachToPost(1);
		Attached first = attachToPost(0);

		assertThat(mediaIds(sentPostCard())).containsExactly(first.mediaId(), second.mediaId());
	}

	@Test
	@DisplayName("INT-006 발급한 조회 URL로 받은 객체는 업로드한 바이트와 같다")
	void issuedUrlFetchesUploadedObject() throws Exception {
		Attached media = attachToPost(0);
		try (S3Client client = testS3Client()) {
			client.putObject(PutObjectRequest.builder().bucket(TEST_BUCKET).key(media.storageKey())
					.contentType("image/png").build(), RequestBody.fromBytes(PNG));
		}

		String url = sentPostCard().path("media").get(0).path("url").asText();
		var response = HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
				BodyHandlers.ofByteArray());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(PNG);
	}

	@Test
	@DisplayName("INT-007 첨부가 없으면 세 목록 모두 media를 null이 아닌 빈 배열로 내린다")
	void listingsWithoutMediaReturnEmptyArray() throws Exception {
		assertThat(inboxCard().path("media").isArray()).isTrue();
		assertThat(inboxCard().path("media")).isEmpty();
		assertThat(sentPostCard().path("media").isArray()).isTrue();
		assertThat(sentPostCard().path("media")).isEmpty();
		assertThat(answerItem().path("media").isArray()).isTrue();
		assertThat(answerItem().path("media")).isEmpty();
	}

	@Test
	@DisplayName("INT-009 받은 질문 상세의 card도 READY 첨부의 조회 URL을 내리고, 첨부가 DELETED가 되면 media가 빈 배열이다")
	void inboxDetailExposesMediaViewUrl() throws Exception {
		Attached media = attachToPost(0);

		assertMediaContract(inboxDetailCard(), media);

		markDeleted(media.mediaId());
		assertThat(inboxDetailCard().path("media").isArray()).isTrue();
		assertThat(inboxDetailCard().path("media")).isEmpty();
	}

	@Test
	@DisplayName("INT-010 보낸 질문 상세의 card도 READY 첨부의 조회 URL을 내리고, 첨부가 DELETED가 되면 media가 빈 배열이다")
	void sentPostDetailExposesMediaViewUrl() throws Exception {
		Attached media = attachToPost(0);

		assertMediaContract(sentPostDetailCard(), media);

		markDeleted(media.mediaId());
		assertThat(sentPostDetailCard().path("media").isArray()).isTrue();
		assertThat(sentPostDetailCard().path("media")).isEmpty();
	}

	private void assertMediaContract(JsonNode card, Attached media) {
		assertThat(card.has("mediaIds")).isFalse();
		JsonNode item = card.path("media").get(0);
		assertThat(card.path("media")).hasSize(1);
		assertThat(item.path("mediaId").asLong()).isEqualTo(media.mediaId());
		assertThat(item.path("url").asText()).isNotBlank();
		Instant expiresAt = Instant.parse(item.path("expiresAt").asText());
		assertThat(expiresAt).isBetween(
				media.requestedAt().plus(properties.viewUrlTtl()).minusSeconds(5),
				Instant.now().plus(properties.viewUrlTtl()).plusSeconds(5));
		// presigned URL은 객체 경로를 서명 대상으로 담으므로 url 값 안에는 key가 들어간다. 비노출 계약은
		// url 밖의 필드와 값에 storage key·버킷 이름이 없다는 뜻이다.
		ObjectNode withoutUrl = card.deepCopy();
		withoutUrl.withArray("media").forEach(node -> ((ObjectNode) node).remove("url"));
		assertThat(withoutUrl.toString()).doesNotContain(media.storageKey()).doesNotContain(TEST_BUCKET);
		assertThat(fieldNames(card)).doesNotContain("storageKey", "bucket");
	}

	private JsonNode inboxCard() throws Exception {
		return data(get("/api/v1/direction/inbox"), recipientId).path("cards").get(0);
	}

	private JsonNode sentPostCard() throws Exception {
		return data(get("/api/v1/direction/posts"), senderId).path("cards").get(0);
	}

	private JsonNode inboxDetailCard() throws Exception {
		return data(get("/api/v1/direction/inbox/{postRecipientId}", postRecipientId), recipientId).path("card");
	}

	private JsonNode sentPostDetailCard() throws Exception {
		return data(get("/api/v1/direction/posts/{postId}", postId), senderId).path("card");
	}

	private JsonNode answerItem() throws Exception {
		return data(get("/api/v1/direction/posts/{postId}/answers", postId), senderId).path("answers").get(0);
	}

	private JsonNode data(MockHttpServletRequestBuilder request, long userId) throws Exception {
		String response = mockMvc.perform(request.with(jwt().jwt(token -> token.subject(String.valueOf(userId)))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).path("data");
	}

	private Attached attachToPost(int displayOrder) {
		long mediaId = readyMedia(senderId);
		jdbc.update("INSERT INTO media_attachment (media_id, owner_id, post_id, display_order) VALUES (?, ?, ?, ?)",
				mediaId, senderId, postId, displayOrder);
		return attached(mediaId);
	}

	private Attached attachToAnswer(int displayOrder) {
		long mediaId = readyMedia(recipientId);
		jdbc.update("INSERT INTO media_attachment (media_id, owner_id, answer_id, display_order) VALUES (?, ?, ?, ?)",
				mediaId, recipientId, answerId, displayOrder);
		return attached(mediaId);
	}

	private long readyMedia(long ownerId) {
		return jdbc.queryForObject("""
				INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum)
				VALUES (?, 'READY', ?, 'image/png', ?, 'gh300-checksum')
				RETURNING id
				""", Long.class, ownerId, "media/" + ownerId + "/gh300-" + System.nanoTime(), PNG.length);
	}

	private Attached attached(long mediaId) {
		String storageKey = jdbc.queryForObject("SELECT storage_key FROM media_asset WHERE id = ?", String.class,
				mediaId);
		return new Attached(mediaId, storageKey, Instant.now());
	}

	private void markDeleted(long mediaId) {
		jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = clock_timestamp() WHERE id = ?", mediaId);
	}

	private static List<Long> mediaIds(JsonNode card) {
		List<Long> ids = new ArrayList<>();
		card.path("media").forEach(item -> ids.add(item.path("mediaId").asLong()));
		return ids;
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		node.path("media").forEach(item -> item.fieldNames().forEachRemaining(names::add));
		return names;
	}

	private record Attached(long mediaId, String storageKey, Instant requestedAt) {
	}
}
