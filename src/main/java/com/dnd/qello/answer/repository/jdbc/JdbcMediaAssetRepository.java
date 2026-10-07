package com.dnd.qello.answer.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.repository.MediaAssetRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcMediaAssetRepository implements MediaAssetRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public MediaAsset save(MediaAsset asset) {
		String sql = asset.getId() == null ? """
				INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum, exif_stripped,
				                         created_at, deleted_at)
				VALUES (:ownerId, :status, :storageKey, :mimeType, :byteSize, :checksum, :exifStripped, :createdAt,
				        :deletedAt)
				RETURNING id
				""" : """
				UPDATE media_asset
				SET owner_id = :ownerId, status = :status, storage_key = :storageKey, mime_type = :mimeType,
				    byte_size = :byteSize, checksum = :checksum, exif_stripped = :exifStripped, created_at = :createdAt,
				    deleted_at = :deletedAt
				WHERE id = :id
				RETURNING id
				""";
		Long id = jdbc.queryForObject(sql, params(asset), Long.class);
		return MediaAsset.restore(id, asset.getOwnerId(), asset.getStatus(), asset.getStorageKey(),
				asset.getMimeType(), asset.getByteSize(), asset.getChecksum(), asset.isExifStripped(),
				asset.getCreatedAt(),
				asset.getDeletedAt());
	}

	@Override
	public Optional<MediaAsset> findById(long id) {
		return one("SELECT * FROM media_asset WHERE id = :id", new MapSqlParameterSource("id", id));
	}

	@Override
	public Optional<MediaAsset> findByIdAndOwnerId(long id, long ownerId) {
		return one("SELECT * FROM media_asset WHERE id = :id AND owner_id = :ownerId",
				new MapSqlParameterSource().addValue("id", id).addValue("ownerId", ownerId));
	}

	/**
	 * READY 전이는 처리본 key와 exif_stripped를 상태와 같은 UPDATE로 바꾼다. 원본 key가 조회에 쓰이는 순간을
	 * 없앤다.
	 */
	@Override
	public Optional<MediaAsset> transitionFromUploading(MediaAsset next) {
		return jdbc.query("""
				UPDATE media_asset
				SET status = :status, storage_key = :storageKey, exif_stripped = :exifStripped, deleted_at = :deletedAt
				WHERE id = :id AND status = :previousStatus
				RETURNING *
				""", new MapSqlParameterSource().addValue("id", next.getId())
				.addValue("status", next.getStatus().name())
				.addValue("storageKey", next.getStorageKey())
				.addValue("exifStripped", next.isExifStripped())
				.addValue("deletedAt", timestamp(next.getDeletedAt()))
				.addValue("previousStatus", MediaAssetStatus.UPLOADING.name()),
				rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private Optional<MediaAsset> one(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private static MapSqlParameterSource params(MediaAsset asset) {
		return new MapSqlParameterSource().addValue("id", asset.getId()).addValue("ownerId", asset.getOwnerId())
				.addValue("status", asset.getStatus().name()).addValue("storageKey", asset.getStorageKey())
				.addValue("mimeType", asset.getMimeType()).addValue("byteSize", asset.getByteSize())
				.addValue("checksum", asset.getChecksum()).addValue("exifStripped", asset.isExifStripped())
				.addValue("createdAt", timestamp(asset.getCreatedAt()))
				.addValue("deletedAt", timestamp(asset.getDeletedAt()));
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static MediaAsset map(ResultSet rs) throws SQLException {
		return MediaAsset.restore(rs.getLong("id"), rs.getLong("owner_id"),
				MediaAssetStatus.valueOf(rs.getString("status")), rs.getString("storage_key"),
				rs.getString("mime_type"),
				rs.getLong("byte_size"), rs.getString("checksum"), rs.getBoolean("exif_stripped"),
				rs.getTimestamp("created_at").toInstant(), instant(rs, "deleted_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
