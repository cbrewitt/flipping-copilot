package com.flippingcopilot.manager;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@AllArgsConstructor
public class ByteRecordArray {

   public ByteBuffer data;
   private final int bytesPerRecord;
   private final int recordTimeBytePosition;
   private final int recordUpdatedTimeBytePosition;

   public int totalRecords() {
      return data.limit() / bytesPerRecord;
   }

   public int findInsertionIndex(ByteRecord r) {
      if(data.limit() == 0) {
         return -1;
      }
      int totalRecords = data.limit() / bytesPerRecord;
      int left = 0;
      int right = totalRecords;
      while (left < right) {
         int mid = left + (right - left) / 2;
         ByteRecord midRecord = getRecordAtIndex(mid);
         if (r.compareTo(midRecord) <= 0) {
            right = mid;
         } else {
            left = mid + 1;
         }
      }
      if (left < totalRecords && r.compareTo(getRecordAtIndex(left)) == 0) {
         return left;
      }
      // no exact match, return -(insertion point + 1)
      return -(left+1);
   }

   public ByteRecord getRecordAtIndex(int index) {
      int bytePosition = index * bytesPerRecord;
      byte[] bs = new byte[bytesPerRecord];
      data.position(bytePosition);
      data.get(bs);
      ByteBuffer b = ByteBuffer.wrap(bs);
      b.order(ByteOrder.BIG_ENDIAN);
      return new ByteRecord(b, recordTimeBytePosition, recordUpdatedTimeBytePosition);

   }

   /**
    * To support paginated retrieval (time, id) DESC. endTimeIncl, endIdExcl is from the last item on the previous
    * page. We return any records below that in the order (time, id) DESC.
    */
   public List<ByteRecord> descBetween(int startTimeIncl, int endTimeIncl, UUID endIdExcl, int limit) {
      if (data.limit() == 0) {
         return Collections.emptyList();
      }
      // use MIN UUID since we want all records with time >= startIncl
      ByteRecord startBoundary = orderingRecord(startTimeIncl, new UUID(Long.MIN_VALUE, Long.MIN_VALUE));
      int startIndex = findInsertionIndex(startBoundary);
      startIndex = startIndex < 0 ? -(startIndex + 1) : startIndex;

      ByteRecord endBoundary = orderingRecord(endTimeIncl, endIdExcl);
      int endIndex = findInsertionIndex(endBoundary);
      endIndex = endIndex < 0 ? -(endIndex + 1) : endIndex;

      List<ByteRecord> result = new ArrayList<>();
      for(int i=endIndex-1; i >= startIndex && result.size() < limit; i--) {
         result.add(getRecordAtIndex(i));
      }
      return result;
   }

   public void insertAtIndex(ByteRecord r, int index) {
      byte[] arr = data.array();
      ByteBuffer newData = ByteBuffer.allocate(data.limit() + bytesPerRecord);
      newData.order(ByteOrder.BIG_ENDIAN);
      int cut = index* bytesPerRecord;

      newData.put(arr, 0, cut); // left side
      newData.put(r.data.array()); // inserted record
      newData.put(arr, cut, arr.length -cut); // right side

      data = newData;
   }

   public void replaceAtIndex(ByteRecord r, int index) {
      data.position(index* bytesPerRecord);
      data.put(r.data.array());
   }

   public static ByteRecordArray fromFile(RandomAccessFile raf, int bytesPerRecord, int recordTimeBytePosition, int recordUpdatedTimeBytePosition) throws IOException {
      FileChannel channel = raf.getChannel();
      long fileSize = channel.size();
      ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
      buffer.order(ByteOrder.BIG_ENDIAN);
      channel.read(buffer);
      return new ByteRecordArray(buffer, bytesPerRecord, recordTimeBytePosition, recordUpdatedTimeBytePosition);
   }

   public byte[] bytesAfterIndex(int index) {
      int totalRecords = data.limit() / bytesPerRecord;
      int bytesToShift = (totalRecords - index) * bytesPerRecord;
      byte[] b = new byte[bytesToShift];
      data.position(index* bytesPerRecord);
      data.get(b);
      return b;
   }


   public ByteRecord orderingRecord(int time, UUID id) {
      ByteBuffer buffer = ByteBuffer.allocate(bytesPerRecord);
      buffer.order(ByteOrder.BIG_ENDIAN);
      buffer.putLong(0, id.getMostSignificantBits());
      buffer.putLong(8, id.getLeastSignificantBits());
      buffer.putInt(recordTimeBytePosition, time);
      return new ByteRecord(buffer, recordTimeBytePosition, recordUpdatedTimeBytePosition);
   }
}
