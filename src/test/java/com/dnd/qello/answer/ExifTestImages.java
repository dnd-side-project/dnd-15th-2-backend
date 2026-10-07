/**
 * Created at: 2026-10-07T23:20:12+09:00
 * Source scenario: TEST-PLAN-GH-325-EXIF-STRIP-UNIT-001 through UNIT-012,
 * UNIT-023 through UNIT-027 (added 2026-10-07T23:42:12+09:00)
 */
package com.dnd.qello.answer;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * EXIF 제거 테스트용 이미지를 코드로 만들고, 결과를 production 코드와 독립된 파서로 읽는다.
 *
 * <p>
 * GPS 값은 실제 장소가 아닌 더미 값(위도 1도, 경도 2도)이다.
 */
final class ExifTestImages {

	static final int APP0 = 0xE0;
	static final int APP1 = 0xE1;
	static final int APP2 = 0xE2;
	static final int APP13 = 0xED;
	static final int APP14 = 0xEE;
	static final int COM = 0xFE;
	static final int SOS = 0xDA;

	static final int TAG_MAKE = 0x010F;
	static final int TAG_MODEL = 0x0110;
	static final int TAG_ORIENTATION = 0x0112;
	static final int TAG_DATE_TIME = 0x0132;
	static final int TAG_GPS_IFD = 0x8825;

	static final String XMP_ID = "http://ns.adobe.com/xap/1.0/\0";
	static final String XMP_GPS = "<x:xmpmeta><rdf:Description exif:GPSLatitude=\"1,0.0N\"/></x:xmpmeta>";

	private static final int SHORT = 3;
	private static final int LONG = 4;
	private static final int RATIONAL = 5;
	private static final int ASCII = 2;

	private ExifTestImages() {
	}

	// ---------- base images ----------

	static byte[] baseJpeg() {
		return encode(gradient(16, 8), "jpg");
	}

	static byte[] basePng() {
		return encode(gradient(16, 8), "png");
	}

	static BufferedImage decode(byte[] image) {
		try {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
			if (decoded == null) {
				throw new IllegalStateException("ImageIO가 이미지를 읽지 못했습니다");
			}
			return decoded;
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	static int[] pixels(BufferedImage image) {
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static BufferedImage gradient(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x40);
			}
		}
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, format, out)) {
				throw new IllegalStateException(format + " writer가 없습니다");
			}
			return out.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	// ---------- TIFF (EXIF) ----------

	/** 휴대폰 사진처럼 기기 정보, 촬영 시각, GPS IFD, 썸네일 IFD(IFD1)를 모두 담은 TIFF. */
	static byte[] phoneTiff(ByteOrder order, Integer orientation) {
		return tiff(order, orientation, true, true, true);
	}

	static byte[] tiff(ByteOrder order, Integer orientation, boolean device, boolean gps, boolean thumbnail) {
		List<Entry> ifd0 = new ArrayList<>();
		if (device) {
			ifd0.add(ascii(TAG_MAKE, "TestMake"));
			ifd0.add(ascii(TAG_MODEL, "TestModel"));
		}
		if (orientation != null) {
			ifd0.add(new Entry(TAG_ORIENTATION, SHORT, 1, shortValue(order, orientation)));
		}
		if (device) {
			ifd0.add(ascii(TAG_DATE_TIME, "2026:01:01 00:00:00"));
		}
		List<Entry> gpsIfd = gps
				? List.of(ascii(0x0001, "N"), rational3(order, 0x0002, 1), ascii(0x0003, "E"),
						rational3(order, 0x0004, 2))
				: List.of();
		if (gps) {
			ifd0.add(new Entry(TAG_GPS_IFD, LONG, 1, null));
		}
		byte[] thumbnailBytes = thumbnail ? baseJpeg() : new byte[0];
		List<Entry> ifd1 = thumbnail
				? List.of(new Entry(0x0201, LONG, 1, null),
						new Entry(0x0202, LONG, 1, intValue(order, thumbnailBytes.length)))
				: List.of();

		int ifd0Offset = 8;
		int gpsOffset = ifd0Offset + ifdSize(ifd0);
		int ifd1Offset = gpsOffset + (gps ? ifdSize(gpsIfd) : 0);
		int thumbnailOffset = ifd1Offset + (thumbnail ? ifdSize(ifd1) : 0);
		int total = thumbnailOffset + thumbnailBytes.length;

		ByteBuffer buffer = ByteBuffer.allocate(total).order(order);
		buffer.put((order == ByteOrder.BIG_ENDIAN ? "MM" : "II").getBytes(StandardCharsets.US_ASCII));
		buffer.putShort((short) 42);
		buffer.putInt(ifd0Offset);
		List<Entry> resolvedIfd0 = ifd0.stream()
				.map(entry -> entry.tag() == TAG_GPS_IFD
						? new Entry(TAG_GPS_IFD, LONG, 1, intValue(order, gpsOffset))
						: entry)
				.toList();
		writeIfd(buffer, ifd0Offset, resolvedIfd0, thumbnail ? ifd1Offset : 0);
		if (gps) {
			writeIfd(buffer, gpsOffset, gpsIfd, 0);
		}
		if (thumbnail) {
			List<Entry> resolvedIfd1 = List.of(new Entry(0x0201, LONG, 1, intValue(order, thumbnailOffset)),
					ifd1.get(1));
			writeIfd(buffer, ifd1Offset, resolvedIfd1, 0);
			buffer.position(thumbnailOffset);
			buffer.put(thumbnailBytes);
		}
		return buffer.array();
	}

	private record Entry(int tag, int type, int count, byte[] value) {
	}

	private static Entry ascii(int tag, String text) {
		byte[] value = (text + "\0").getBytes(StandardCharsets.US_ASCII);
		return new Entry(tag, ASCII, value.length, value);
	}

	private static Entry rational3(ByteOrder order, int tag, int degrees) {
		ByteBuffer value = ByteBuffer.allocate(24).order(order);
		value.putInt(degrees).putInt(1).putInt(0).putInt(1).putInt(0).putInt(1);
		return new Entry(tag, RATIONAL, 3, value.array());
	}

	private static byte[] shortValue(ByteOrder order, int value) {
		return ByteBuffer.allocate(4).order(order).putShort((short) value).array();
	}

	private static byte[] intValue(ByteOrder order, int value) {
		return ByteBuffer.allocate(4).order(order).putInt(value).array();
	}

	private static int ifdSize(List<Entry> entries) {
		int size = 2 + entries.size() * 12 + 4;
		for (Entry entry : entries) {
			if (entry.value() != null && entry.value().length > 4) {
				size += entry.value().length;
			}
		}
		return size;
	}

	private static void writeIfd(ByteBuffer buffer, int offset, List<Entry> entries, int nextIfdOffset) {
		int dataOffset = offset + 2 + entries.size() * 12 + 4;
		buffer.position(offset);
		buffer.putShort((short) entries.size());
		for (Entry entry : entries) {
			buffer.putShort((short) entry.tag()).putShort((short) entry.type()).putInt(entry.count());
			if (entry.value().length > 4) {
				buffer.putInt(dataOffset);
				int resume = buffer.position();
				buffer.position(dataOffset);
				buffer.put(entry.value());
				dataOffset += entry.value().length;
				buffer.position(resume);
			} else {
				buffer.put(entry.value());
				buffer.position(buffer.position() + 4 - entry.value().length);
			}
		}
		buffer.putInt(nextIfdOffset);
	}

	// ---------- JPEG builder ----------

	static JpegBuilder jpeg() {
		return new JpegBuilder(baseJpeg());
	}

	static final class JpegBuilder {
		private final byte[] base;
		private final List<byte[]> segments = new ArrayList<>();
		private byte[] trailer = new byte[0];

		private JpegBuilder(byte[] base) {
			this.base = base;
		}

		JpegBuilder segment(int marker, byte[] payload) {
			byte[] segment = new byte[4 + payload.length];
			segment[0] = (byte) 0xFF;
			segment[1] = (byte) marker;
			segment[2] = (byte) ((payload.length + 2) >> 8);
			segment[3] = (byte) (payload.length + 2);
			System.arraycopy(payload, 0, segment, 4, payload.length);
			segments.add(segment);
			return this;
		}

		JpegBuilder exif(byte[] tiff) {
			return segment(APP1, concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff));
		}

		JpegBuilder xmp(String xml) {
			return segment(APP1, concat(XMP_ID.getBytes(StandardCharsets.US_ASCII),
					xml.getBytes(StandardCharsets.UTF_8)));
		}

		JpegBuilder icc() {
			byte[] header = concat("ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1, 1});
			return segment(APP2, concat(header, srgbProfile()));
		}

		JpegBuilder mpf() {
			return segment(APP2, concat("MPF\0".getBytes(StandardCharsets.US_ASCII), new byte[16]));
		}

		JpegBuilder adobe() {
			// transform=1(YCbCr): JFIF 기본 색 공간과 같아 디코드 결과가 바뀌지 않는다.
			return segment(APP14, concat("Adobe".getBytes(StandardCharsets.US_ASCII),
					new byte[]{0, 100, 0, 0, 0, 0, 1}));
		}

		JpegBuilder comment(String text) {
			return segment(COM, text.getBytes(StandardCharsets.US_ASCII));
		}

		JpegBuilder trailer(byte[] bytes) {
			this.trailer = bytes.clone();
			return this;
		}

		/** APP0(JFIF)가 있으면 그 바로 뒤, 없으면 SOI 바로 뒤에 추가한 세그먼트를 순서대로 넣는다. */
		byte[] build() {
			boolean hasApp0 = (base[3] & 0xFF) == APP0;
			int afterApp0 = hasApp0 ? 2 + 2 + ((base[4] & 0xFF) << 8 | base[5] & 0xFF) : 2;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			out.write(base, 0, afterApp0);
			segments.forEach(out::writeBytes);
			out.write(base, afterApp0, base.length - afterApp0);
			out.writeBytes(trailer);
			return out.toByteArray();
		}
	}

	// ---------- PNG builder ----------

	static PngBuilder png() {
		return new PngBuilder(basePng());
	}

	static final class PngBuilder {
		private final byte[] base;
		private final List<byte[]> chunks = new ArrayList<>();
		private byte[] trailer = new byte[0];

		private PngBuilder(byte[] base) {
			this.base = base;
		}

		PngBuilder chunk(String type, byte[] data) {
			chunks.add(chunkBytes(type, data));
			return this;
		}

		PngBuilder exif(byte[] tiff) {
			return chunk("eXIf", tiff);
		}

		PngBuilder text(String type, String text) {
			byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
			if ("zTXt".equals(type)) {
				data = concat("Comment\0\0".getBytes(StandardCharsets.ISO_8859_1), deflate(data));
			}
			return chunk(type, data);
		}

		PngBuilder iccp() {
			byte[] header = "sRGB\0\0".getBytes(StandardCharsets.ISO_8859_1);
			return chunk("iCCP", concat(header, deflate(srgbProfile())));
		}

		PngBuilder trailer(byte[] bytes) {
			this.trailer = bytes.clone();
			return this;
		}

		/** IHDR 바로 뒤에 추가한 청크를 순서대로 넣는다. */
		byte[] build() {
			int afterIhdr = 8 + 12 + 13;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			out.write(base, 0, afterIhdr);
			chunks.forEach(out::writeBytes);
			out.write(base, afterIhdr, base.length - afterIhdr);
			out.writeBytes(trailer);
			return out.toByteArray();
		}
	}

	static byte[] chunkBytes(String type, byte[] data) {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
				.putInt((int) crc.getValue()).array();
	}

	// ---------- JPEG variants ----------

	/** 여러 스캔(SOS)과 스캔 사이 테이블을 가진 progressive JPEG. */
	static byte[] progressiveJpeg() {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
		return write(writer, new IIOImage(gradient(64, 32), null, null), param);
	}

	/** DRI 세그먼트와 압축 데이터 속 RST 마커를 가진 JPEG. MCU마다 재시작한다. */
	static byte[] restartIntervalJpeg() {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		BufferedImage image = gradient(64, 32);
		try {
			IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
			String format = "javax_imageio_jpeg_image_1.0";
			IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
			IIOMetadataNode markers = (IIOMetadataNode) root.getElementsByTagName("markerSequence").item(0);
			IIOMetadataNode dri = new IIOMetadataNode("dri");
			dri.setAttribute("interval", "1");
			markers.insertBefore(dri, markers.getFirstChild());
			metadata.setFromTree(format, root);
			return write(writer, new IIOImage(image, null, metadata), param);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** 첫 DQT 마커 앞에 fill 바이트(0xFF) 두 개를 끼운다. */
	static byte[] withFillBytesBeforeTables(byte[] jpeg) {
		int dqt = allMarkers(jpeg).stream().filter(marker -> marker.code() == 0xDB).findFirst().orElseThrow()
				.offset();
		return concat(concat(Arrays.copyOf(jpeg, dqt), new byte[]{(byte) 0xFF, (byte) 0xFF}),
				Arrays.copyOfRange(jpeg, dqt, jpeg.length));
	}

	/** {@code scanIndex}번째(0부터) SOS 바로 앞에 세그먼트를 끼운다. */
	static byte[] insertBeforeScan(byte[] jpeg, int scanIndex, byte[] segments) {
		int sos = allMarkers(jpeg).stream().filter(marker -> marker.code() == SOS).skip(scanIndex).findFirst()
				.orElseThrow().offset();
		return concat(concat(Arrays.copyOf(jpeg, sos), segments), Arrays.copyOfRange(jpeg, sos, jpeg.length));
	}

	static byte[] segmentBytes(int marker, byte[] payload) {
		return concat(new byte[]{(byte) 0xFF, (byte) marker, (byte) ((payload.length + 2) >> 8),
				(byte) (payload.length + 2)}, payload);
	}

	private static byte[] write(ImageWriter writer, IIOImage image, ImageWriteParam param) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			writer.write(null, image, param);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	record Marker(int code, int offset) {
	}

	/**
	 * 파일 전체의 마커를 순서대로 찾는다. 압축 데이터 안의 RST 마커도 포함한다. 결과 파일 어디에도 지워야 할 세그먼트가 남지 않았는지
	 * 확인할 때 쓴다(첫 스캔 뒤까지 본다).
	 */
	static List<Marker> allMarkers(byte[] jpeg) {
		List<Marker> markers = new ArrayList<>();
		markers.add(new Marker(0xD8, 0));
		int pos = 2;
		while (pos + 1 < jpeg.length) {
			while ((jpeg[pos + 1] & 0xFF) == 0xFF) {
				pos++;
			}
			int code = jpeg[pos + 1] & 0xFF;
			markers.add(new Marker(code, pos));
			if (code == 0xD9) {
				return markers;
			}
			int next = pos + 2 + ((jpeg[pos + 2] & 0xFF) << 8 | jpeg[pos + 3] & 0xFF);
			if (code == SOS) {
				next = skipScanData(jpeg, next, markers);
			}
			pos = next;
		}
		throw new IllegalStateException("EOI를 찾지 못했습니다");
	}

	private static int skipScanData(byte[] jpeg, int from, List<Marker> markers) {
		int index = from;
		while (index + 1 < jpeg.length) {
			int next = jpeg[index + 1] & 0xFF;
			if ((jpeg[index] & 0xFF) != 0xFF || next == 0x00 || next == 0xFF) {
				index++;
			} else if (next >= 0xD0 && next <= 0xD7) {
				markers.add(new Marker(next, index));
				index += 2;
			} else {
				return index;
			}
		}
		throw new IllegalStateException("스캔 데이터가 끝나지 않았습니다");
	}

	// ---------- independent parsers ----------

	record JpegSegment(int marker, byte[] payload) {

		boolean hasId(String id) {
			byte[] expected = id.getBytes(StandardCharsets.US_ASCII);
			if (payload.length < expected.length) {
				return false;
			}
			for (int index = 0; index < expected.length; index++) {
				if (payload[index] != expected[index]) {
					return false;
				}
			}
			return true;
		}
	}

	/** SOI부터 첫 SOS 헤더까지의 세그먼트. SOS 세그먼트 자체도 마지막에 포함한다. */
	static List<JpegSegment> headerSegments(byte[] jpeg) {
		List<JpegSegment> result = new ArrayList<>();
		int pos = 2;
		while (pos + 4 <= jpeg.length) {
			int marker = jpeg[pos + 1] & 0xFF;
			int length = (jpeg[pos + 2] & 0xFF) << 8 | jpeg[pos + 3] & 0xFF;
			byte[] payload = new byte[length - 2];
			System.arraycopy(jpeg, pos + 4, payload, 0, payload.length);
			result.add(new JpegSegment(marker, payload));
			if (marker == SOS) {
				return result;
			}
			pos += 2 + length;
		}
		throw new IllegalStateException("SOS를 찾지 못했습니다");
	}

	/** 첫 SOS 세그먼트 시작부터 파일 끝까지. 원본과 처리본의 압축 데이터 비교에 쓴다. */
	static byte[] fromFirstScan(byte[] jpeg) {
		int pos = 2;
		while (true) {
			int marker = jpeg[pos + 1] & 0xFF;
			if (marker == SOS) {
				byte[] rest = new byte[jpeg.length - pos];
				System.arraycopy(jpeg, pos, rest, 0, rest.length);
				return rest;
			}
			pos += 2 + ((jpeg[pos + 2] & 0xFF) << 8 | jpeg[pos + 3] & 0xFF);
		}
	}

	/**
	 * EXIF APP1 payload("Exif\0\0" 뒤 TIFF)의 IFD0 태그와 값(SHORT는 값, 그 외는 -1), 다음 IFD
	 * offset.
	 */
	static Ifd0 ifd0(byte[] tiff) {
		ByteOrder order = tiff[0] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
		ByteBuffer buffer = ByteBuffer.wrap(tiff).order(order);
		int offset = buffer.getInt(4);
		int count = buffer.getShort(offset) & 0xFFFF;
		Map<Integer, Integer> tags = new LinkedHashMap<>();
		for (int index = 0; index < count; index++) {
			int entry = offset + 2 + index * 12;
			int tag = buffer.getShort(entry) & 0xFFFF;
			int type = buffer.getShort(entry + 2) & 0xFFFF;
			tags.put(tag, type == SHORT ? buffer.getShort(entry + 8) & 0xFFFF : -1);
		}
		return new Ifd0(tags, buffer.getInt(offset + 2 + count * 12));
	}

	record Ifd0(Map<Integer, Integer> tags, int nextIfdOffset) {
	}

	static byte[] tiffOf(JpegSegment exifSegment) {
		byte[] tiff = new byte[exifSegment.payload().length - 6];
		System.arraycopy(exifSegment.payload(), 6, tiff, 0, tiff.length);
		return tiff;
	}

	record PngChunk(String type, byte[] data, boolean crcValid) {
	}

	record PngLayout(List<PngChunk> chunks, int bytesAfterIend) {

		List<String> types() {
			return chunks.stream().map(PngChunk::type).toList();
		}

		PngChunk first(String type) {
			return chunks.stream().filter(chunk -> chunk.type().equals(type)).findFirst().orElseThrow();
		}
	}

	static PngLayout pngLayout(byte[] png) {
		List<PngChunk> chunks = new ArrayList<>();
		int pos = 8;
		while (pos < png.length) {
			ByteBuffer buffer = ByteBuffer.wrap(png);
			int length = buffer.getInt(pos);
			String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
			byte[] data = new byte[length];
			System.arraycopy(png, pos + 8, data, 0, length);
			CRC32 crc = new CRC32();
			crc.update(png, pos + 4, 4 + length);
			boolean valid = (int) crc.getValue() == buffer.getInt(pos + 8 + length);
			chunks.add(new PngChunk(type, data, valid));
			pos += 12 + length;
			if ("IEND".equals(type)) {
				return new PngLayout(chunks, png.length - pos);
			}
		}
		throw new IllegalStateException("IEND를 찾지 못했습니다");
	}

	// ---------- helpers ----------

	static byte[] concat(byte[] first, byte[] second) {
		byte[] result = new byte[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	static boolean contains(byte[] haystack, String needle) {
		return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
	}

	private static byte[] srgbProfile() {
		return ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
	}

	private static byte[] deflate(byte[] data) {
		Deflater deflater = new Deflater();
		deflater.setInput(data);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			out.write(buffer, 0, deflater.deflate(buffer));
		}
		deflater.end();
		return out.toByteArray();
	}
}
