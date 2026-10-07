package com.dnd.qello.answer.domain.exif;

/**
 * EXIF(TIFF) 구조에서 Orientation 하나만 읽고 쓴다.
 *
 * <p>
 * 읽기가 실패해도 예외를 던지지 않는다. EXIF가 깨졌다고 멀쩡한 픽셀까지 거절하지 않고, 회전 정보만 포기한다.
 */
final class ExifOrientation {

	static final int NONE = 0;

	private static final int ORIENTATION_TAG = 0x0112;
	private static final int SHORT_TYPE = 3;
	private static final int TIFF_HEADER_LENGTH = 8;
	private static final int IFD_ENTRY_LENGTH = 12;
	private static final int MIN_ROTATED = 2;
	private static final int MAX_ROTATED = 8;

	private ExifOrientation() {
	}

	/**
	 * IFD0의 Orientation을 읽는다. 회전·반전이 필요 없는 값(1), 범위 밖 값, 읽을 수 없는 구조면 {@link #NONE}.
	 */
	static int read(byte[] data, int offset, int length) {
		if (length < TIFF_HEADER_LENGTH) {
			return NONE;
		}
		Boolean bigEndian = byteOrder(data, offset);
		if (bigEndian == null || u16(data, offset + 2, bigEndian) != 42) {
			return NONE;
		}
		long ifdOffset = u32(data, offset + 4, bigEndian);
		if (ifdOffset < TIFF_HEADER_LENGTH || ifdOffset + 2 > length) {
			return NONE;
		}
		int ifd = offset + (int) ifdOffset;
		int entryCount = u16(data, ifd, bigEndian);
		// 다음 IFD offset(4바이트)까지 있어야 온전한 IFD다. 이 조건 덕분에 다시 쓴 EXIF가 원본보다 길어지지 않는다.
		if (ifdOffset + 2 + (long) entryCount * IFD_ENTRY_LENGTH + 4 > length) {
			return NONE;
		}
		for (int index = 0; index < entryCount; index++) {
			int entry = ifd + 2 + index * IFD_ENTRY_LENGTH;
			if (u16(data, entry, bigEndian) == ORIENTATION_TAG) {
				return orientationValue(data, entry, bigEndian);
			}
		}
		return NONE;
	}

	/** Orientation 태그 하나만 담은 big-endian TIFF. GPS IFD와 썸네일(IFD1)이 없다. */
	static byte[] tiffWith(int orientation) {
		return new byte[]{
				'M', 'M', 0, 42, 0, 0, 0, 8,
				0, 1,
				0x01, 0x12, 0, SHORT_TYPE, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
				0, 0, 0, 0
		};
	}

	private static int orientationValue(byte[] data, int entry, boolean bigEndian) {
		if (u16(data, entry + 2, bigEndian) != SHORT_TYPE || u32(data, entry + 4, bigEndian) != 1) {
			return NONE;
		}
		int value = u16(data, entry + 8, bigEndian);
		return value >= MIN_ROTATED && value <= MAX_ROTATED ? value : NONE;
	}

	private static Boolean byteOrder(byte[] data, int offset) {
		if (data[offset] == 'M' && data[offset + 1] == 'M') {
			return true;
		}
		if (data[offset] == 'I' && data[offset + 1] == 'I') {
			return false;
		}
		return null;
	}

	private static int u16(byte[] data, int index, boolean bigEndian) {
		int first = data[index] & 0xFF;
		int second = data[index + 1] & 0xFF;
		return bigEndian ? first << 8 | second : second << 8 | first;
	}

	private static long u32(byte[] data, int index, boolean bigEndian) {
		long high = u16(data, bigEndian ? index : index + 2, bigEndian);
		long low = u16(data, bigEndian ? index + 2 : index, bigEndian);
		return high << 16 | low;
	}
}
