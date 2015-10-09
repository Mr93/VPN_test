package com.example.dendimon.vpn_test;

import java.util.LinkedHashMap;

/**
 * Created by Dendimon on 10/9/2015.
 */
public class LRUCache <K,V> extends LinkedHashMap<K,V> {
    private int maxSize;
    private CleanupCallback callback;

    public LRUCache(int maxSize,CleanupCallback callback){
        super(maxSize+1,1,true);
        this.maxSize=maxSize;
        this.callback=callback;
    }

    @Override
    protected boolean removeEldestEntry (Entry<K,V> eldest){
        if(size()>maxSize){
            callback.cleanup(eldest);
            return true;
        }
        return false;
    }

    public static interface CleanupCallback<K, V>{
        public void cleanup(Entry<K,V> eldest);
    }
}
