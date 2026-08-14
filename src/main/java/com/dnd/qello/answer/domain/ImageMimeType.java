package com.dnd.qello.answer.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 이미지 포맷별 canonical MIME과 허용 별칭을 한 곳에서 관리한다. */
public enum ImageMimeType {

	JPEG("image/jpeg", Set.of("image/jpeg", "image/jpg")),
	PNG("image/png", Set.of("image/png"));

	private static final Map<String, ImageMimeType> BY_MIME_TYPE = Arrays.stream(values())
		.flatMap(format -> format.aliases().stream().map(alias -> Map.entry(alias, format)))
		.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
	private static final Set<String> SUPPORTED_MIME_TYPES = Arrays.stream(values())
		.map(ImageMimeType::mimeType)
		.collect(Collectors.toUnmodifiableSet());

	private final String mimeType;
	private final Set<String> aliases;

	ImageMimeType(String mimeType, Set<String> aliases) {
		this.mimeType = mimeType;
		this.aliases = aliases;
	}

	public String mimeType() {
		return mimeType;
	}

	public Set<String> aliases() {
		return aliases;
	}

	public static Set<String> supportedMimeTypes() {
		return SUPPORTED_MIME_TYPES;
	}

	public static Optional<ImageMimeType> fromMimeType(String value) {
		if (value == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_MIME_TYPE.get(value.trim().toLowerCase(Locale.ROOT)));
	}

	public static String canonicalMimeType(String value) {
		return fromMimeType(value).map(ImageMimeType::mimeType).orElse(null);
	}
}
