package com.flippingcopilot.manager;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Singleton
public class ByteRecordSafeWriter {

    private static final int WAL_HEADER_SIZE = 16;

    public ByteRecordArray load(File f, int bytesPerRecord, int timeBytePosition, int updatedTimeBytePosition) throws IOException {
        String lockFile = f.getPath() + ".lock";
        try (RandomAccessFile l = new RandomAccessFile(lockFile, "rw");
             FileChannel channel = l.getChannel();
             FileLock lock = channel.lock()) {
            Path walFile = Paths.get(f.getPath() + ".wal");
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                ensureRecovered(raf, walFile);
                return ByteRecordArray.fromFile(raf, bytesPerRecord, timeBytePosition, updatedTimeBytePosition);
            }
        }
    }

    public void upsertRecords(File f, ByteRecord[] updatedRecords) throws IOException {
        if (updatedRecords == null || updatedRecords.length == 0) {
            return;
        }

        int bytesPerRecord = updatedRecords[0].data.capacity();
        int timeBytePosition = updatedRecords[0].timeBytePosition;
        int updatedTimeBytePosition = updatedRecords[0].updatedTimeBytePosition;

        String lockFile = f.getPath() + ".lock";
        try (RandomAccessFile l = new RandomAccessFile(lockFile, "rw");
            FileChannel channel = l.getChannel();
            FileLock lock = channel.lock()) {
            Path walFile = Paths.get(f.getPath() + ".wal");
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                ensureRecovered(raf, walFile);
                ByteRecordArray data = ByteRecordArray.fromFile(raf, bytesPerRecord, timeBytePosition, updatedTimeBytePosition);
                for (ByteRecord r : updatedRecords) {
                    int matchIndex = data.findInsertionIndex(r);
                    if (matchIndex < 0) {
                        int insertIndex = -matchIndex - 1;
                        data.insertAtIndex(r, insertIndex);
                        write(raf, walFile, data.bytesAfterIndex(insertIndex), insertIndex*bytesPerRecord, false);
                    } else {
                        ByteRecord existing = data.getRecordAtIndex(matchIndex);
                        if(existing.getUpdatedTime() >= r.getUpdatedTime()) {
                            log.debug("skip record update as existing record newer");
                            continue;
                        }
                        data.replaceAtIndex(r, matchIndex);
                        write(raf, walFile, r.data.array(), matchIndex*bytesPerRecord, false);
                    }
                }
            }
        }
    }

    /**
     * A basic WAL implementation to prevent file corruption on crashes:
     * - Any change is first written to the WAL file then applied to the actual file then the WAL file deleted.
     * - If a previous valid WAL file exists, the program crashed during the previous write, and we replay to recover.
     * - The only operation is to write bytes forward from any location with an optional truncation afterward. This is
     *   idempotent and re-playable regardless of when the program might have crashed.
     */
    public void write(RandomAccessFile f, Path walFile, byte[] b, int start, boolean truncate) throws IOException {

        ByteBuffer walBuffer = ByteBuffer.allocate(WAL_HEADER_SIZE + b.length);
        walBuffer.putInt(WAL_HEADER_SIZE + b.length);   // Total WAL size (header + data)
        walBuffer.putInt(truncate ? 0 : 1);                   // whether to truncate at the end of the write
        walBuffer.putInt(start);                              // Write position
        walBuffer.putInt(b.length);                           // Data length
        walBuffer.put(b);                                     // Actual data

        Files.write(walFile, walBuffer.array(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC);  // Force to disk

        applyUpdate(f, walBuffer);
        Files.delete(walFile);
    }

    public void ensureRecovered(RandomAccessFile f, Path walFile) throws IOException  {
        if (Files.exists(walFile)) {
            byte[] wal = Files.readAllBytes(walFile);
            if(!walFileCorrupt(wal)) {
                applyUpdate(f, ByteBuffer.wrap(wal));
            }
            Files.delete(walFile);
        }
    }

    private void applyUpdate(RandomAccessFile raf, ByteBuffer buffer) throws IOException {
        buffer.position(0);
        int totalSize = buffer.getInt();
        boolean truncate = buffer.getInt() == 0;
        int writeStart = buffer.getInt();
        int dataLength = buffer.getInt();

        byte[] data = new byte[dataLength];
        buffer.get(data);

        raf.seek(writeStart);
        raf.write(data);
        if(truncate) {
            raf.setLength(writeStart + dataLength);
        }
        raf.getFD().sync();
    }

    private boolean walFileCorrupt(byte[] b) {
        return b.length < WAL_HEADER_SIZE || ByteBuffer.wrap(b, 0, 4).getInt() != b.length;
    }
}