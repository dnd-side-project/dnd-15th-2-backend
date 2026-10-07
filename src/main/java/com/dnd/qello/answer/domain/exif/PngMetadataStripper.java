package com.dnd.qello.answer.domain.exif;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * PNG 청크를 순서대로 복사하면서 허용 목록 밖의 청크를 뺀다.
 *
 * <p>
 * 남기는 청크는 픽셀·팔레트·투명도·색 정보·애니메이션 청크다. 첫 eXIf는 Orientation만 담은 eXIf로 바꾸고, 텍스트
 * 청크(tEXt, iTXt, zTXt)와 시각(tIME)을 포함한 나머지는 지운다. IEND 뒤의 데이터도 버린다.
 */
final class PngMetadataStripper {

	private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
	private static final Set<String> KEPT_CHUNKS = Set.of(
			"IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "bKGD", "pHYs",
			"acTL", "fcTL", "fdAT");
	private static final String HEADER = "IHDR";
	private static final String END = "IEND";
	private static final String EXIF = "eXIf";
	private static final String DATA = "IDAT";
	private static final int CHUNK_OVERHEAD = 12;

	private PngMetadataStripper() {
	}

	static byte[] strip(byte[] image) {
		if (!startsWithSignature(image)) {
			throw new UnsupportedImageException("PNG 시그니처가 없습니다");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(image.length);
		out.write(image, 0, SIGNATURE.length);
		boolean exifSeen = false;
		boolean dataSeen = false;
		int pos = SIGNATURE.length;
		while (pos < image.length) {
			int end = chunkEnd(image, pos);
			String type = new String(image, pos + 4, 4, StandardCharsets.US_ASCII);
			if (pos == SIGNATURE.length && !HEADER.equals(type)) {
				throw new UnsupportedImageException("PNG 첫 청크가 IHDR이 아닙니다");
			}
			if (END.equals(type)) {
				if (!dataSeen) {
					throw new UnsupportedImageException("PNG 이미지 데이터(IDAT)가 없습니다");
				}
				out.write(image, pos, end - pos);
				return out.toByteArray();
			}
			dataSeen |= DATA.equals(type);
			if (EXIF.equals(type) && !exifSeen) {
				exifSeen = true;
				writeOrientationOnly(out, image, pos + 8, end - CHUNK_OVERHEAD - pos);
			} else if (KEPT_CHUNKS.contains(type)) {
				out.write(image, pos, end - pos);
			}
			pos = end;
		}
		throw new UnsupportedImageException("PNG IEND 청크가 없습니다");
	}

	private static boolean startsWithSignature(byte[] image) {
		if (image.length < SIGNATURE.length) {
			return false;
		}
		for (int index = 0; index < SIGNATURE.length; index++) {
			if (image[index] != SIGNATURE[index]) {
				return false;
			}
		}
		return true;
	}

	private static int chunkEnd(byte[] image, int pos) {
		if (pos + CHUNK_OVERHEAD > image.length) {
			throw new UnsupportedImageException("PNG 청크 헤더를 읽을 수 없습니다");
		}
		long length = (long) (image[pos] & 0xFF) << 24 | (image[pos + 1] & 0xFF) << 16
				| (image[pos + 2] & 0xFF) << 8 | image[pos + 3] & 0xFF;
		if (length > image.length - pos - CHUNK_OVERHEAD) {
			throw new UnsupportedImageException("PNG 청크 길이가 파일 범위를 벗어났습니다");
		}
		return pos + CHUNK_OVERHEAD + (int) length;
	}

	private static void writeOrientationOnly(ByteArrayOutputStream out, byte[] image, int dataStart, int length) {
		int orientation = ExifOrientation.read(image, dataStart, length);
		if (orientation == ExifOrientation.NONE) {
			return;
		}
		byte[] tiff = ExifOrientation.tiffWith(orientation);
		byte[] type = EXIF.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(tiff);
		writeInt(out, tiff.length);
		out.writeBytes(type);
		out.writeBytes(tiff);
		writeInt(out, crc.getValue());
	}

	private static void writeInt(ByteArrayOutputStream out, long value) {
		out.write((int) (value >>> 24) & 0xFF);
		out.write((int) (value >>> 16) & 0xFF);
		out.write((int) (value >>> 8) & 0xFF);
		out.write((int) value & 0xFF);
	}
}
