/**
 * Created at: 2026-10-07T23:21:30+09:00
 * Source scenario: TEST-PLAN-GH-325-EXIF-STRIP-UNIT-001 through UNIT-013,
 * UNIT-023 through UNIT-027 (added 2026-10-07T23:42:12+09:00)
 */
package com.dnd.qello.answer;

import java.awt.image.BufferedImage;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.dnd.qello.answer.ExifTestImages.JpegSegment;
import com.dnd.qello.answer.ExifTestImages.PngLayout;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.domain.exif.ExifStripper;
import com.dnd.qello.answer.domain.exif.UnsupportedImageException;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.dnd.qello.answer.ExifTestImages.APP0;
import static com.dnd.qello.answer.ExifTestImages.APP1;
import static com.dnd.qello.answer.ExifTestImages.APP13;
import static com.dnd.qello.answer.ExifTestImages.APP14;
import static com.dnd.qello.answer.ExifTestImages.APP2;
import static com.dnd.qello.answer.ExifTestImages.COM;
import static com.dnd.qello.answer.ExifTestImages.TAG_GPS_IFD;
import static com.dnd.qello.answer.ExifTestImages.TAG_ORIENTATION;
import static com.dnd.qello.answer.ExifTestImages.XMP_GPS;
import static com.dnd.qello.answer.ExifTestImages.concat;
import static com.dnd.qello.answer.ExifTestImages.contains;
import static com.dnd.qello.answer.ExifTestImages.headerSegments;
import static com.dnd.qello.answer.ExifTestImages.ifd0;
import static com.dnd.qello.answer.ExifTestImages.phoneTiff;
import static com.dnd.qello.answer.ExifTestImages.pngLayout;
import static com.dnd.qello.answer.ExifTestImages.tiffOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class ExifStripperTest {

	private static final String SECRET = "SECRET-PAYLOAD";
	private static final Predicate<JpegSegment> EXIF_SEGMENT = segment -> segment.marker() == APP1
			&& segment.hasId("Exif\0\0");

	@Test
	@DisplayName("휴대폰 사진의 EXIF는 Orientation 하나만 남기고 GPS·기기 정보·촬영 시각·썸네일을 지운다")
	void keepsOnlyOrientationFromPhoneExif() {
		byte[] original = ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6)).build();
		assertThat(ifd0(tiffOf(exifSegments(original).get(0))).tags()).containsKey(TAG_GPS_IFD);

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		List<JpegSegment> exif = exifSegments(stripped);
		assertThat(exif).hasSize(1);
		ExifTestImages.Ifd0 ifd0 = ifd0(tiffOf(exif.get(0)));
		assertThat(ifd0.tags()).containsExactly(entry(TAG_ORIENTATION, 6));
		assertThat(ifd0.nextIfdOffset()).isZero();
		assertThat(contains(stripped, "TestMake")).isFalse();
		assertThat(contains(stripped, "2026:01:01")).isFalse();
	}

	@Test
	@DisplayName("XMP, APP13, COM과 그 밖의 APP3~APP15 세그먼트를 모두 지운다")
	void removesXmpPhotoshopCommentAndOtherAppSegments() {
		ExifTestImages.JpegBuilder builder = ExifTestImages.jpeg().xmp(XMP_GPS)
				.segment(APP13, "Photoshop 3.0\0GPS".getBytes(StandardCharsets.US_ASCII))
				.comment(SECRET);
		for (int marker = 0xE3; marker <= 0xEF; marker++) {
			if (marker != APP13 && marker != APP14) {
				builder.segment(marker, ("APP-" + marker).getBytes(StandardCharsets.US_ASCII));
			}
		}
		byte[] stripped = strip(ImageMimeType.JPEG, builder.build());

		assertThat(headerSegments(stripped)).extracting(JpegSegment::marker)
				.doesNotContain(APP1, APP13, COM, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xEF);
		assertThat(contains(stripped, "GPSLatitude")).isFalse();
		assertThat(contains(stripped, SECRET)).isFalse();
	}

	@Test
	@DisplayName("APP0(JFIF), ICC 프로파일, APP14(Adobe)는 원래 바이트 그대로 남기고 MPF는 지운다")
	void keepsJfifIccAndAdobeButRemovesMpf() {
		byte[] original = ExifTestImages.jpeg().icc().mpf().adobe().build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		List<JpegSegment> before = headerSegments(original);
		List<JpegSegment> after = headerSegments(stripped);
		assertThat(payloadOf(after, APP0, "JFIF")).isEqualTo(payloadOf(before, APP0, "JFIF"));
		assertThat(payloadOf(after, APP2, "ICC_PROFILE")).isEqualTo(payloadOf(before, APP2, "ICC_PROFILE"));
		assertThat(payloadOf(after, APP14, "Adobe")).isEqualTo(payloadOf(before, APP14, "Adobe"));
		assertThat(after).noneMatch(segment -> segment.marker() == APP2 && segment.hasId("MPF"));
	}

	@Test
	@DisplayName("EOI 뒤에 붙은 데이터(GPS EXIF가 든 보조 이미지)를 버리고 첫 EOI에서 끝낸다")
	void dropsDataAfterEndOfImage() {
		byte[] attached = ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6)).build();
		byte[] original = ExifTestImages.jpeg().trailer(attached).build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(stripped).isEqualTo(ExifTestImages.jpeg().build());
		assertThat(contains(stripped, "TestMake")).isFalse();
	}

	@Test
	@DisplayName("메타데이터를 지워도 JPEG·PNG의 크기와 모든 픽셀 값, JPEG 압축 데이터가 그대로다")
	void keepsPixelsAndCompressedDataUnchanged() {
		byte[] jpeg = fullJpeg(false);
		byte[] strippedJpeg = strip(ImageMimeType.JPEG, jpeg);
		byte[] png = fullPng();
		byte[] strippedPng = strip(ImageMimeType.PNG, png);

		assertSamePixels(jpeg, strippedJpeg);
		assertSamePixels(png, strippedPng);
		assertThat(ExifTestImages.fromFirstScan(strippedJpeg)).isEqualTo(ExifTestImages.fromFirstScan(jpeg));
	}

	@Test
	@DisplayName("메타데이터가 없는 JPEG은 바이트가 그대로이고 두 번 지워도 결과가 같다")
	void leavesCleanJpegUntouchedAndIsIdempotent() {
		byte[] clean = ExifTestImages.baseJpeg();

		byte[] once = strip(ImageMimeType.JPEG, clean);
		byte[] twice = strip(ImageMimeType.JPEG, once);

		assertThat(once).isEqualTo(clean);
		assertThat(twice).isEqualTo(once);
		assertThat(strip(ImageMimeType.JPEG, fullJpeg(true))).isEqualTo(
				strip(ImageMimeType.JPEG, strip(ImageMimeType.JPEG, fullJpeg(true))));
	}

	@ParameterizedTest(name = "{0} 바이트 순서, Orientation {1}")
	@CsvSource({"BIG, 3", "LITTLE, 3", "BIG, 6", "LITTLE, 6", "BIG, 8", "LITTLE, 8", "LITTLE, 2"})
	@DisplayName("회전·반전이 필요한 Orientation은 바이트 순서와 관계없이 같은 값으로 남는다")
	void keepsRotatingOrientationForBothByteOrders(String order, int orientation) {
		byte[] original = ExifTestImages.jpeg()
				.exif(ExifTestImages.tiff(byteOrder(order), orientation, true, true, false)).build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(ifd0(tiffOf(exifSegments(stripped).get(0))).tags())
				.containsExactly(entry(TAG_ORIENTATION, orientation));
	}

	@ParameterizedTest(name = "{0} 바이트 순서, Orientation {1}")
	@CsvSource({"BIG, -1", "LITTLE, -1", "BIG, 0", "LITTLE, 0", "BIG, 1", "LITTLE, 1", "BIG, 9", "LITTLE, 9"})
	@DisplayName("Orientation이 없거나 1이거나 범위를 벗어나면 EXIF 세그먼트 자체를 남기지 않는다")
	void dropsExifWhenOrientationNeedsNoRotation(String order, int orientation) {
		Integer value = orientation < 0 ? null : orientation;
		byte[] original = ExifTestImages.jpeg()
				.exif(ExifTestImages.tiff(byteOrder(order), value, true, true, false)).build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(exifSegments(stripped)).isEmpty();
	}

	@Test
	@DisplayName("PNG는 eXIf를 Orientation만 남겨 다시 쓰고 텍스트 청크와 tIME을 지우며 색 정보는 남긴다")
	void rewritesPngExifAndRemovesTextChunks() {
		byte[] stripped = strip(ImageMimeType.PNG, fullPng());

		PngLayout layout = pngLayout(stripped);
		List<String> types = layout.types();
		assertThat(types.get(0)).isEqualTo("IHDR");
		assertThat(types.get(types.size() - 1)).isEqualTo("IEND");
		assertThat(types).contains("IDAT", "iCCP", "pHYs").doesNotContain("tEXt", "iTXt", "zTXt", "tIME");
		assertThat(types).filteredOn("eXIf"::equals).hasSize(1);
		ExifTestImages.Ifd0 ifd0 = ifd0(layout.first("eXIf").data());
		assertThat(ifd0.tags()).containsExactly(entry(TAG_ORIENTATION, 6));
		assertThat(ifd0.nextIfdOffset()).isZero();
		assertThat(layout.chunks()).allMatch(ExifTestImages.PngChunk::crcValid);
		assertThat(contains(stripped, SECRET)).isFalse();
		assertThat(contains(stripped, "GPSLatitude")).isFalse();
	}

	@Test
	@DisplayName("PNG IEND 뒤의 데이터를 버리고, 메타데이터가 없는 PNG는 바이트가 그대로다")
	void dropsDataAfterPngEndAndLeavesCleanPngUntouched() {
		byte[] clean = ExifTestImages.basePng();
		byte[] withTrailer = ExifTestImages.png().trailer(SECRET.getBytes(StandardCharsets.US_ASCII)).build();

		assertThat(strip(ImageMimeType.PNG, clean)).isEqualTo(clean);
		byte[] stripped = strip(ImageMimeType.PNG, withTrailer);
		assertThat(pngLayout(stripped).bytesAfterIend()).isZero();
		assertThat(stripped).isEqualTo(clean);
	}

	@Test
	@DisplayName("구조가 깨진 JPEG은 UnsupportedImageException이고 메시지에 입력 바이트가 들어가지 않는다")
	void rejectsMalformedJpegWithoutLeakingPayload() {
		byte[] head = concat(new byte[]{(byte) 0xFF, (byte) 0xD8}, comSegment());
		byte[] beyondEnd = concat(head, new byte[]{(byte) 0xFF, (byte) APP1, (byte) 0xFF, (byte) 0xFF, 1, 2, 3});
		byte[] tooShort = concat(head, new byte[]{(byte) 0xFF, (byte) APP1, 0, 1, 1, 2, 3});
		byte[] noScan = concat(head, new byte[]{(byte) 0xFF, (byte) 0xD9});
		byte[] withComment = ExifTestImages.jpeg().comment(SECRET).build();
		byte[] noEnd = Arrays.copyOf(withComment, withComment.length - 2);

		for (byte[] malformed : List.of(beyondEnd, tooShort, noScan, noEnd)) {
			assertThatThrownBy(() -> ExifStripper.strip(ImageMimeType.JPEG, malformed))
					.isInstanceOf(UnsupportedImageException.class)
					.satisfies(exception -> assertThat(exception.getMessage()).isNotBlank().doesNotContain(SECRET));
		}
	}

	@Test
	@DisplayName("구조가 깨진 PNG는 UnsupportedImageException이다")
	void rejectsMalformedPng() {
		byte[] clean = ExifTestImages.basePng();
		byte[] signature = Arrays.copyOf(clean, 8);
		byte[] beyondEnd = concat(signature, new byte[]{0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 'I', 'H', 'D',
				'R', 0, 0, 0, 0});
		byte[] textFirst = concat(concat(signature, ExifTestImages.chunkBytes("tEXt",
				SECRET.getBytes(StandardCharsets.US_ASCII))), Arrays.copyOfRange(clean, 8, clean.length));
		byte[] noEnd = Arrays.copyOf(clean, clean.length - 12);
		byte[] noData = concat(Arrays.copyOf(clean, 8 + 12 + 13), ExifTestImages.chunkBytes("IEND", new byte[0]));

		for (byte[] malformed : List.of(beyondEnd, textFirst, noEnd, noData)) {
			assertThatThrownBy(() -> ExifStripper.strip(ImageMimeType.PNG, malformed))
					.isInstanceOf(UnsupportedImageException.class)
					.satisfies(exception -> assertThat(exception.getMessage()).isNotBlank().doesNotContain(SECRET));
		}
	}

	@Test
	@DisplayName("정상 입력은 처리 후 길이가 원본 이하다")
	void neverGrowsOutput() {
		Map<ImageMimeType, List<byte[]>> inputs = Map.of(
				ImageMimeType.JPEG, List.of(fullJpeg(true), ExifTestImages.baseJpeg(),
						ExifTestImages.jpeg().exif(ExifTestImages.tiff(ByteOrder.BIG_ENDIAN, 6, false, false, false))
								.build()),
				ImageMimeType.PNG, List.of(fullPng(), ExifTestImages.basePng(),
						ExifTestImages.png().exif(ExifTestImages.tiff(ByteOrder.BIG_ENDIAN, 6, false, false, false))
								.build()));

		inputs.forEach((format, images) -> images.forEach(
				image -> assertThat(strip(format, image).length).isLessThanOrEqualTo(image.length)));

		// 다음 IFD offset이 빠진 TIFF는 다시 쓴 EXIF(36바이트)보다 짧을 수 있다. 회전 정보를 포기하고 늘어나지 않는다.
		byte[] truncatedTiff = {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0};
		byte[] truncated = ExifTestImages.jpeg().exif(truncatedTiff).build();
		byte[] stripped = strip(ImageMimeType.JPEG, truncated);
		assertThat(stripped.length).isLessThanOrEqualTo(truncated.length);
		assertThat(exifSegments(stripped)).isEmpty();
	}

	@Test
	@DisplayName("UNIT-023 progressive JPEG의 스캔 사이에 끼운 XMP·COM도 지우고 스캔 수와 픽셀은 그대로다")
	void removesMetadataBetweenProgressiveScans() {
		byte[] progressive = ExifTestImages.progressiveJpeg();
		byte[] between = concat(ExifTestImages.segmentBytes(APP1,
				concat(ExifTestImages.XMP_ID.getBytes(StandardCharsets.US_ASCII),
						XMP_GPS.getBytes(StandardCharsets.UTF_8))),
				ExifTestImages.segmentBytes(COM, SECRET.getBytes(StandardCharsets.US_ASCII)));
		byte[] original = ExifTestImages.insertBeforeScan(progressive, 1, between);
		assertThat(scanCount(progressive)).isGreaterThan(1);

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(ExifTestImages.allMarkers(stripped)).extracting(ExifTestImages.Marker::code)
				.doesNotContain(APP1, COM);
		assertThat(scanCount(stripped)).isEqualTo(scanCount(original));
		assertThat(contains(stripped, "GPSLatitude")).isFalse();
		assertThat(contains(stripped, SECRET)).isFalse();
		assertSamePixels(original, stripped);
		assertThat(stripped).isEqualTo(progressive);
	}

	@Test
	@DisplayName("UNIT-024 재시작 간격(DRI)과 압축 데이터 속 RST 마커가 있는 JPEG은 바이트가 그대로 나온다")
	void keepsRestartMarkersUntouched() {
		byte[] original = ExifTestImages.restartIntervalJpeg();
		List<Integer> codes = ExifTestImages.allMarkers(original).stream().map(ExifTestImages.Marker::code).toList();
		assertThat(codes).contains(0xDD).anyMatch(code -> code >= 0xD0 && code <= 0xD7);

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(stripped).isEqualTo(original);
		assertSamePixels(original, stripped);
	}

	@Test
	@DisplayName("UNIT-025 마커 앞의 fill 바이트(0xFF)를 건너뛰고 처리한다")
	void skipsFillBytesBeforeMarkers() {
		byte[] clean = ExifTestImages.baseJpeg();
		byte[] original = ExifTestImages.withFillBytesBeforeTables(clean);

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		assertThat(stripped).isEqualTo(clean);
		assertSamePixels(original, stripped);
	}

	@Test
	@DisplayName("UNIT-026 EXIF APP1이 두 개면 첫 번째의 Orientation만 남기고 두 번째는 통째로 지운다")
	void keepsOnlyFirstExifOrientation() {
		byte[] original = ExifTestImages.jpeg().exif(ExifTestImages.tiff(ByteOrder.BIG_ENDIAN, 6, false, false, false))
				.exif(phoneTiff(ByteOrder.LITTLE_ENDIAN, 3)).build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		List<JpegSegment> exif = exifSegments(stripped);
		assertThat(exif).hasSize(1);
		assertThat(ifd0(tiffOf(exif.get(0))).tags()).containsExactly(entry(TAG_ORIENTATION, 6));
		assertThat(contains(stripped, "TestMake")).isFalse();
	}

	@Test
	@DisplayName("UNIT-027 JFIF가 아닌 APP0(JFXX 썸네일)과 예약 마커 세그먼트는 지우고 JFIF APP0은 남긴다")
	void removesJfxxAndReservedMarkers() {
		byte[] thumbnail = ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6)).build();
		byte[] original = ExifTestImages.jpeg()
				.segment(APP0,
						concat("JFXX\0".getBytes(StandardCharsets.US_ASCII), concat(new byte[]{0x10}, thumbnail)))
				.segment(0xF1, SECRET.getBytes(StandardCharsets.US_ASCII))
				.build();

		byte[] stripped = strip(ImageMimeType.JPEG, original);

		List<JpegSegment> segments = headerSegments(stripped);
		assertThat(segments).filteredOn(segment -> segment.marker() == APP0).hasSize(1)
				.allMatch(segment -> segment.hasId("JFIF\0"));
		assertThat(ExifTestImages.allMarkers(stripped)).extracting(ExifTestImages.Marker::code).doesNotContain(0xF1);
		assertThat(contains(stripped, "TestMake")).isFalse();
		assertThat(contains(stripped, SECRET)).isFalse();
	}

	@Test
	@DisplayName("EXIF 제거 모듈은 Spring과 AWS SDK에 의존하지 않는다")
	void stripperDoesNotDependOnSpringOrAws() {
		JavaClasses classes = new ClassFileImporter().importPackages("com.dnd.qello.answer.domain.exif");
		assertThat(classes.contain(ExifStripper.class)).isTrue();

		noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "software.amazon..")
				.check(classes);
	}

	private static byte[] strip(ImageMimeType format, byte[] image) {
		return ExifStripper.strip(format, image);
	}

	private static long scanCount(byte[] jpeg) {
		return ExifTestImages.allMarkers(jpeg).stream().filter(marker -> marker.code() == ExifTestImages.SOS).count();
	}

	private static List<JpegSegment> exifSegments(byte[] jpeg) {
		return headerSegments(jpeg).stream().filter(EXIF_SEGMENT).toList();
	}

	private static byte[] payloadOf(List<JpegSegment> segments, int marker, String id) {
		return segments.stream().filter(segment -> segment.marker() == marker && segment.hasId(id)).findFirst()
				.orElseThrow(() -> new AssertionError("세그먼트가 없습니다: " + id)).payload();
	}

	private static byte[] fullJpeg(boolean withTrailer) {
		ExifTestImages.JpegBuilder builder = ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6))
				.xmp(XMP_GPS).icc().mpf().adobe().comment(SECRET);
		if (withTrailer) {
			builder.trailer(ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.LITTLE_ENDIAN, 3)).build());
		}
		return builder.build();
	}

	private static byte[] fullPng() {
		return ExifTestImages.png().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6))
				.text("tEXt", "Comment\0" + SECRET)
				.text("iTXt", "XML:com.adobe.xmp\0\0\0\0\0" + XMP_GPS)
				.text("zTXt", SECRET)
				.chunk("tIME", new byte[]{0x07, (byte) 0xEA, 1, 1, 0, 0, 0})
				.iccp()
				.chunk("pHYs", new byte[]{0, 0, 0x0B, 0x13, 0, 0, 0x0B, 0x13, 1})
				.build();
	}

	private static byte[] comSegment() {
		byte[] payload = SECRET.getBytes(StandardCharsets.US_ASCII);
		return concat(new byte[]{(byte) 0xFF, (byte) COM, 0, (byte) (payload.length + 2)}, payload);
	}

	private static void assertSamePixels(byte[] original, byte[] stripped) {
		BufferedImage before = ExifTestImages.decode(original);
		BufferedImage after = ExifTestImages.decode(stripped);
		assertThat(after.getWidth()).isEqualTo(before.getWidth());
		assertThat(after.getHeight()).isEqualTo(before.getHeight());
		assertThat(ExifTestImages.pixels(after)).isEqualTo(ExifTestImages.pixels(before));
	}

	private static ByteOrder byteOrder(String name) {
		return "BIG".equals(name) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
	}
}
