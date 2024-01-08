package com.google.android.exoplayer2.extractor.mp3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mp3Parser {

    private static final Map<Integer, int[]> mBitRates = new HashMap<>(6);
    private static final Map<Integer, int[]> mSampleRates = new HashMap<>(3);

    static {
        mBitRates.put(11, new int[]{
                0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0
        });
        mBitRates.put(12, new int[]{
                0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0
        });
        mBitRates.put(13, new int[]{
                0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
        });
        mBitRates.put(21, new int[]{
                0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0
        });
        mBitRates.put(22, new int[]{
                0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0
        });
        mBitRates.put(23, new int[]{
                0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
        });

        mSampleRates.put(3, new int[]{
                44100, 48000, 32000, 0
        });
        mSampleRates.put(2, new int[]{
                22050, 24000, 16000, 0
        });
        mSampleRates.put(0, new int[]{
                11025, 12000, 8000, 0
        });
    }

    private long currentTimeUs;
    private long currentHeaderPosition;

    private final Map<Long, Long> timeToPosition = new HashMap<>();
    private final List<Long> timeList = new ArrayList<>(4096);

    private long durationUs;
    private long endOfPosition;
    private long offset;

    private boolean parseSuccess = false;

    public void parse(Input inputStream) {
        reset();
        byte[] header = findFirstHeader(inputStream);
        if (header == null) {
            return;
        }
        while (true) {
            timeToPosition.put(currentTimeUs, currentHeaderPosition);
            timeList.add(currentTimeUs);
            int skip = parseHeader(header) - 4;
            if (!inputStream.skip(skip)) {
                break;
            }
            currentHeaderPosition += skip + 4;
            if (inputStream.read(header) == -1) {
                break;
            }
        }
        durationUs = currentTimeUs;
        endOfPosition = currentHeaderPosition;
        parseSuccess = true;
    }

    private void reset() {
        currentTimeUs = 0;
        durationUs = currentTimeUs;
        currentHeaderPosition = offset;
        endOfPosition = currentHeaderPosition;
        timeToPosition.clear();
        timeList.clear();
        parseSuccess = false;
    }

    public long positionByTime(long timeUs) {
        long targetTime = Math.min(timeUs, durationUs);
        int targetIndex = (int) (targetTime * timeList.size() / durationUs);
        targetIndex = Math.max(targetIndex - 1, 0);
        long maybeTime = timeList.get(targetIndex);
        if (maybeTime == targetTime) {
            return timeToPosition.get(maybeTime);
        }
        if (maybeTime > targetTime) {
            long left;
            long right = maybeTime;
            for (int i = targetIndex - 1; i >= 0; i--) {
                long tempTime = timeList.get(i);
                if (tempTime == targetTime) {
                    return timeToPosition.get(tempTime);
                }
                if (tempTime > targetTime) {
                    right = tempTime;
                    continue;
                }
                left = tempTime;
                if ((targetTime - left) <= (right - targetTime)) {
                    return timeToPosition.get(left);
                }
                if ((targetTime - left) > (right - targetTime)) {
                    return timeToPosition.get(right);
                }
            }
        }
        if (maybeTime < targetTime) {
            long left = maybeTime;
            long right;
            for (int i = targetIndex + 1; i < timeList.size(); i++) {
                long tempTime = timeList.get(i);
                if (tempTime == targetTime) {
                    return timeToPosition.get(tempTime);
                }
                if (tempTime < targetTime) {
                    left = tempTime;
                    continue;
                }
                right = tempTime;
                if ((targetTime - left) <= (right - targetTime)) {
                    return timeToPosition.get(left);
                }
                if ((targetTime - left) > (right - targetTime)) {
                    return timeToPosition.get(right);
                }
            }
        }
        return timeToPosition.get(maybeTime);
    }

    public long timeByPosition(long position) {
        long targetPosition = Math.min(position, endOfPosition);
        int targetIndex = (int) (targetPosition * timeList.size() / endOfPosition);
        targetIndex = Math.max(targetIndex - 1, 0);
        return timeList.get(targetIndex);
    }

    private byte[] findFirstHeader(Input inputStream) {
        byte[] b1 = new byte[1];
        byte[] b2 = new byte[1];
        byte[] b3 = new byte[2];
        while (true) {
            if (inputStream.read(b1) == -1) {
                return null;
            }
            currentHeaderPosition++;
            if (b1[0] != (byte) 0xFF) {
                continue;
            }
            if (inputStream.read(b2) == -1) {
                return null;
            }
            currentHeaderPosition++;
            if ((b2[0] & 0xE0) != 0xE0) {
                continue;
            }
            if (inputStream.read(b3) == -1) {
                return null;
            }
            currentHeaderPosition += 2;
            final byte[] header = new byte[4];
            header[0] = b1[0];
            header[1] = b2[0];
            header[2] = b3[0];
            header[3] = b3[1];
            return header;
        }
    }

    private int parseHeader(byte[] header) {
        int version = (header[1] & 0x18) >> 3;
        int v = version == 3 ? 1 : 2;
        int layer = (header[1] & 0x06) >> 1;
        int l = layer == 3 ? 1 : layer == 2 ? 2 : layer == 1 ? 3 : 0;
        int bitrateIndex = (header[2] & 0xF0) >> 4;
        int sampleRateIndex = (header[2] & 0x0C) >> 2;
        int hasPadding = (header[2] & 0x02) >> 1;

        int bitrate = mBitRates.get(v * 10 + l)[bitrateIndex];
        int sampleRate = mSampleRates.get(version)[sampleRateIndex];
        int samples = l == 1 ? 383 : l == 2 ? 1152 : v == 1 ? 1152 : 576;
        int padding = hasPadding == 0 ? 0 : l == 1 ? 4 : 1;

        int frameSize = (samples * bitrate * 1000 / 8) / sampleRate + padding;
        int frameLength = samples * 1000 * 1000 / sampleRate;

        currentTimeUs += frameLength;
        return frameSize;
    }

    public boolean isParseSuccess() {
        return parseSuccess;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public long getEndOfPosition() {
        return endOfPosition;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public interface Input {

        int read(byte[] bytes);

        boolean skip(int skip);
    }
}
