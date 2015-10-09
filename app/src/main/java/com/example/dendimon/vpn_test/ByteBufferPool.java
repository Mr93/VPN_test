package com.example.dendimon.vpn_test;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Dendimon on 10/9/2015.
 */
public class ByteBufferPool {
    private static final int BUFFER_SIZE = 16384;
    private static ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquire(){
        ByteBuffer buffer = pool.poll();
        if (buffer == null)
            buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);//Using directbuffer for zero-copy
        return buffer;
    }

    public static void release (ByteBuffer buffer){
        buffer.clear();
        pool.offer(buffer);
    }

    public static void clear(){pool.clear();}
}
