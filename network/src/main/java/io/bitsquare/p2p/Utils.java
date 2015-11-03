package io.bitsquare.p2p;

import io.bitsquare.common.ByteArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static int findFreeSystemPort() {
        try {
            ServerSocket server = new ServerSocket(0);
            int port = server.getLocalPort();
            server.close();
            return port;
        } catch (IOException ignored) {
        } finally {
            return new Random().nextInt(10000) + 50000;
        }
    }

    public static void shutDownExecutorService(ExecutorService executorService) {
        shutDownExecutorService(executorService, 200);
    }

    public static void shutDownExecutorService(ExecutorService executorService, long waitBeforeShutDown) {
        executorService.shutdown();
        try {
            boolean done = executorService.awaitTermination(waitBeforeShutDown, TimeUnit.MILLISECONDS);
            if (!done) log.trace("Not all tasks completed at shutdown.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        final List<Runnable> rejected = executorService.shutdownNow();
        log.debug("Rejected tasks: {}", rejected.size());
    }

    public static byte[] compress(Serializable input) {
        return compress(ByteArrayUtils.objectToByteArray(input));
    }

    public static byte[] compress(byte[] input) {
        Deflater compressor = new Deflater();
        compressor.setLevel(Deflater.BEST_SPEED);
        compressor.setInput(input);
        compressor.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
        byte[] buf = new byte[8192];
        while (!compressor.finished()) {
            int count = compressor.deflate(buf);
            bos.write(buf, 0, count);
        }
        try {
            bos.close();
        } catch (IOException e) {
        }
        return bos.toByteArray();
    }

    public static byte[] decompress(byte[] compressedData, int offset, int length) {
        Inflater decompressor = new Inflater();
        decompressor.setInput(compressedData, offset, length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
        byte[] buf = new byte[8192];
        while (!decompressor.finished()) {
            try {
                int count = decompressor.inflate(buf);
                bos.write(buf, 0, count);
            } catch (DataFormatException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        try {
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return bos.toByteArray();
    }

    public static Serializable decompress(byte[] compressedData) {
        return (Serializable) ByteArrayUtils.byteArrayToObject(decompress(compressedData, 0, compressedData.length));
    }

}