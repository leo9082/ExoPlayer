package com.google.android.exoplayer2.extractor.mp3;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekPoint;
import java.io.IOException;

public class ExactSeeker implements Seeker {

  private final Mp3Parser parser = new Mp3Parser();

  public ExactSeeker(ExtractorInput input) {
    long startPosition = input.getPeekPosition();
    parser.setOffset(startPosition);
    parser.parse(new Mp3Parser.Input() {
      @Override
      public int read(byte[] bytes) {
        try {
          return input.peek(bytes, 0, bytes.length);
        } catch (IOException e) {
          return -1;
        }
      }

      @Override
      public boolean skip(int skip) {
        try {
          return input.advancePeekPosition(skip, true);
        } catch (IOException e) {
          return false;
        }
      }
    });
  }

  @Override
  public boolean isSeekable() {
    return parser.isParseSuccess();
  }

  @Override
  public long getDurationUs() {
    return parser.getDurationUs();
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return new SeekPoints(new SeekPoint(timeUs, parser.positionByTime(timeUs)));
  }

  @Override
  public long getTimeUs(long position) {
    return parser.timeByPosition(position);
  }

  @Override
  public long getDataEndPosition() {
    return parser.getEndOfPosition();
  }
}
