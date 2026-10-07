package com.dnd.qello.answer.domain.exif;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * JPEG 세그먼트를 순서대로 복사하면서 허용 목록 밖의 메타데이터 세그먼트를 뺀다.
 *
 * <p>
 * 남기는 세그먼트는 디코딩에 필요한 프레임 헤더·테이블과 APP0 중 JFIF, APP2 중 ICC 프로파일, APP14(Adobe 색
 * 변환)뿐이다. 첫 EXIF APP1은 Orientation만 담은 APP1로 바꾼다. 나머지 APP 세그먼트, COM, 예약 마커는
 * 지운다. 첫 EOI 뒤의 데이터도 버린다 — 일부 기기는 그 자리에 GPS가 든 보조 이미지나 영상을 붙인다.
 */
final class JpegMetadataStripper {

	private static final int SOI = 0xD8;
	private static final int EOI = 0xD9;
	private static final int SOS = 0xDA;
	private static final int TEM = 0x01;
	private static final int RST0 = 0xD0;
	private static final int RST7 = 0xD7;
	private static final int SOF0 = 0xC0;
	private static final int SOF15 = 0xCF;
	private static final int JPG = 0xC8;
	private static final int DQT = 0xDB;
	private static final int EXP = 0xDF;
	private static final int APP0 = 0xE0;
	private static final int APP1 = 0xE1;
	private static final int APP2 = 0xE2;
	private static final int APP14 = 0xEE;
	private static final int APP15 = 0xEF;
	private static final int COM = 0xFE;
	private static final int STUFFED_ZERO = 0x00;
	private static final int FILL = 0xFF;

	private static final byte[] JFIF_ID = "JFIF\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] EXIF_ID = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] ICC_ID = "ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] ADOBE_ID = "Adobe".getBytes(StandardCharsets.US_ASCII);

	private JpegMetadataStripper() {
	}

	static byte[] strip(byte[] image) {
		if (image.length < 4 || u8(image, 0) != FILL || u8(image, 1) != SOI) {
			throw new UnsupportedImageException("JPEG 시작 마커가 없습니다");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(image.length);
		out.write(image, 0, 2);
		boolean exifSeen = false;
		boolean scanSeen = false;
		int pos = 2;
		while (true) {
			pos = markerStart(image, pos);
			int marker = u8(image, pos + 1);
			if (marker == EOI) {
				if (!scanSeen) {
					throw new UnsupportedImageException("JPEG 이미지 데이터(SOS)가 없습니다");
				}
				out.write(image, pos, 2);
				return out.toByteArray();
			}
			if (isStandalone(marker)) {
				out.write(image, pos, 2);
				pos += 2;
				continue;
			}
			int end = segmentEnd(image, pos);
			if (marker == SOS) {
				scanSeen = true;
				end = scanEnd(image, end);
				out.write(image, pos, end - pos);
			} else if (marker == APP1 && !exifSeen && hasId(image, pos, end, EXIF_ID)) {
				exifSeen = true;
				writeOrientationOnly(out, image, pos, end);
			} else if (keeps(marker, image, pos, end)) {
				out.write(image, pos, end - pos);
			}
			pos = end;
		}
	}

	/** 마커 앞의 fill 바이트(0xFF 반복)를 건너뛰고 마커의 0xFF 위치를 돌려준다. */
	private static int markerStart(byte[] image, int pos) {
		if (pos >= image.length || u8(image, pos) != FILL) {
			throw new UnsupportedImageException("JPEG 세그먼트 경계가 맞지 않습니다");
		}
		int start = pos;
		while (start + 1 < image.length && u8(image, start + 1) == FILL) {
			start++;
		}
		if (start + 1 >= image.length) {
			throw new UnsupportedImageException("JPEG 끝 마커(EOI)가 없습니다");
		}
		int marker = u8(image, start + 1);
		if (marker == STUFFED_ZERO || marker == SOI) {
			throw new UnsupportedImageException("JPEG 세그먼트 마커가 올바르지 않습니다");
		}
		return start;
	}

	private static boolean isStandalone(int marker) {
		return marker == TEM || marker >= RST0 && marker <= RST7;
	}

	private static int segmentEnd(byte[] image, int pos) {
		if (pos + 4 > image.length) {
			throw new UnsupportedImageException("JPEG 세그먼트 길이를 읽을 수 없습니다");
		}
		int length = u8(image, pos + 2) << 8 | u8(image, pos + 3);
		if (length < 2 || pos + 2 + length > image.length) {
			throw new UnsupportedImageException("JPEG 세그먼트 길이가 파일 범위를 벗어났습니다");
		}
		return pos + 2 + length;
	}

	/** SOS 헤더 뒤의 압축 데이터를 지나 다음 마커의 0xFF 위치를 돌려준다. */
	private static int scanEnd(byte[] image, int from) {
		int index = from;
		while (index + 1 < image.length) {
			if (u8(image, index) != FILL) {
				index++;
				continue;
			}
			int next = u8(image, index + 1);
			if (next == STUFFED_ZERO || next >= RST0 && next <= RST7) {
				index += 2;
			} else if (next == FILL) {
				index++;
			} else {
				return index;
			}
		}
		throw new UnsupportedImageException("JPEG 끝 마커(EOI)가 없습니다");
	}

	private static boolean keeps(int marker, byte[] image, int pos, int end) {
		if (marker >= APP0 && marker <= APP15) {
			// JFIF 확장(JFXX)도 APP0을 쓰는데, 그 안의 썸네일 JPEG에 EXIF가 들어 있을 수 있다.
			return marker == APP0 && hasId(image, pos, end, JFIF_ID)
					|| marker == APP2 && hasId(image, pos, end, ICC_ID)
					|| marker == APP14 && hasId(image, pos, end, ADOBE_ID);
		}
		return isFrameOrTable(marker);
	}

	/**
	 * SOFn, DHT, DAC(0xC0~0xCF, JPG 제외)와 DQT, DNL, DRI, DHP, EXP(0xDB~0xDF). 예약 마커와
	 * JPGn은 남기지 않는다.
	 */
	private static boolean isFrameOrTable(int marker) {
		return marker >= SOF0 && marker <= SOF15 && marker != JPG || marker >= DQT && marker <= EXP;
	}

	private static void writeOrientationOnly(ByteArrayOutputStream out, byte[] image, int pos, int end) {
		int tiffStart = pos + 4 + EXIF_ID.length;
		int orientation = ExifOrientation.read(image, tiffStart, end - tiffStart);
		if (orientation == ExifOrientation.NONE) {
			return;
		}
		byte[] tiff = ExifOrientation.tiffWith(orientation);
		int length = 2 + EXIF_ID.length + tiff.length;
		out.write(FILL);
		out.write(APP1);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.writeBytes(EXIF_ID);
		out.writeBytes(tiff);
	}

	private static boolean hasId(byte[] image, int pos, int end, byte[] id) {
		int payload = pos + 4;
		if (payload + id.length > end) {
			return false;
		}
		for (int index = 0; index < id.length; index++) {
			if (image[payload + index] != id[index]) {
				return false;
			}
		}
		return true;
	}

	private static int u8(byte[] image, int index) {
		return image[index] & 0xFF;
	}
}
