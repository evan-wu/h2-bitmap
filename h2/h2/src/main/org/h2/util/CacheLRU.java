/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import java.sql.SQLException;

import org.h2.constant.SysProperties;
import org.h2.engine.Constants;
import org.h2.message.Message;

/**
 * Special behavior of the cache: You are not allowed to add the same record
 * twice.
 *
 * @author Thomas
 */
public class CacheLRU implements Cache {
    
    public static final String TYPE_NAME = "LRU";

    private final CacheWriter writer;
    private int len;
    private int maxSize;
    private CacheObject[] values;
    private int mask;
    private int recordCount;
    private int sizeMemory;
    private CacheObject head = new CacheHead();

    public CacheLRU(CacheWriter writer, int maxKb) {
        int maxSize = maxKb * 1024 / 4;
        this.writer = writer;
        this.maxSize = maxSize;
        this.len = MathUtils.nextPowerOf2(maxSize / 64);
        this.mask = len - 1;
        MathUtils.checkPowerOf2(len);
        clear();
    }

    public void clear() {
        head.next = head.previous = head;
        values = new CacheObject[len];
        recordCount = 0;
        sizeMemory = 0;
    }

    public void put(CacheObject rec) throws SQLException {
        if (SysProperties.CHECK) {
            for (int i = 0; i < rec.getBlockCount(); i++) {
                CacheObject old = find(rec.getPos() + i);
                if (old != null) {
                    throw Message.getInternalError("try to add a record twice i=" + i);
                }
            }
        }
        int index = rec.getPos() & mask;
        rec.chained = values[index];
        values[index] = rec;
        recordCount++;
        sizeMemory += rec.getMemorySize();
        addToFront(rec);
        removeOldIfRequired();
    }

    public CacheObject update(int pos, CacheObject rec) throws SQLException {
        CacheObject old = find(pos);
        if (old == null) {
            put(rec);
        } else {
            if (SysProperties.CHECK) {
                if (old != rec) {
                    throw Message.getInternalError("old != record old=" + old + " new=" + rec);
                }
            }
            removeFromLinkedList(rec);
            addToFront(rec);
        }
        return old;
    }

    private void removeOldIfRequired() throws SQLException {
        // a small method, to allow inlining
        if (sizeMemory >= maxSize) {
            removeOld();
        }
    }

    private void removeOld() throws SQLException {
        int i = 0;
        ObjectArray changed = new ObjectArray();
        while (sizeMemory * 4 > maxSize * 3 && recordCount > Constants.CACHE_MIN_RECORDS) {
            i++;
            if (i == recordCount) {
                writer.flushLog();
            }
            if (i >= recordCount * 2) {
                // can't remove any record, because the log is not written yet
                // hopefully this does not happen too much, but it could happen
                // theoretically
                // TODO log this
                break;
            }
            CacheObject last = head.next;
            if (SysProperties.CHECK && last == head) {
                throw Message.getInternalError("try to remove head");
            }            
            // we are not allowed to remove it if the log is not yet written 
            // (because we need to log before writing the data)
            // also, can't write it if the record is pinned
            if (!last.canRemove()) {
                removeFromLinkedList(last);
                addToFront(last);
                continue;
            }
            remove(last.getPos());
            if (last.isChanged()) {
                changed.add(last);
            }
        }
        if (changed.size() > 0) {
            CacheObject.sort(changed);
            for (i = 0; i < changed.size(); i++) {
                CacheObject rec = (CacheObject) changed.get(i);
                writer.writeBack(rec);
            }
        }
    }

    private void addToFront(CacheObject rec) {
        if (SysProperties.CHECK && rec == head) {
            throw Message.getInternalError("try to move head");
        }
        rec.next = head;
        rec.previous = head.previous;
        rec.previous.next = rec;
        head.previous = rec;
    }

    private void removeFromLinkedList(CacheObject rec) {
        if (SysProperties.CHECK && rec == head) {
            throw Message.getInternalError("try to remove head");
        }
        rec.previous.next = rec.next;
        rec.next.previous = rec.previous;
        // TODO cache: mystery: why is this required? needs more memory if we
        // don't do this
        rec.next = null;
        rec.previous = null;
    }

    public void remove(int pos) {
        int index = pos & mask;
        CacheObject rec = values[index];
        if (rec == null) {
            return;
        }
        if (rec.getPos() == pos) {
            values[index] = rec.chained;
        } else {
            CacheObject last;
            do {
                last = rec;
                rec = rec.chained;
                if (rec == null) {
                    return;
                }
            } while (rec.getPos() != pos);
            last.chained = rec.chained;
        }
        recordCount--;
        sizeMemory -= rec.getMemorySize();
        removeFromLinkedList(rec);
        if (SysProperties.CHECK) {
            rec.chained = null;
            if (find(pos) != null) {
                throw Message.getInternalError("not removed!");
            }
        }
    }

    public CacheObject find(int pos) {
        CacheObject rec = values[pos & mask];
        while (rec != null && rec.getPos() != pos) {
            rec = rec.chained;
        }
        return rec;
    }

    public CacheObject get(int pos) {
        CacheObject rec = find(pos);
        if (rec != null) {
            removeFromLinkedList(rec);
            addToFront(rec);
        }
        return rec;
    }
    
//    private void testConsistency() {
//        int s = size;
//        HashSet set = new HashSet();
//        for(int i=0; i<values.length; i++) {
//            Record rec = values[i];
//            if(rec == null) {
//                continue;
//            }
//            set.add(rec);
//            while(rec.chained != null) {
//                rec = rec.chained;
//                set.add(rec);
//            }
//        }
//        Record rec = head.next;
//        while(rec != head) {
//            set.add(rec);
//            rec = rec.next;
//        }
//        rec = head.previous;
//        while(rec != head) {
//            set.add(rec);
//            rec = rec.previous;
//        }
//        if(set.size() != size) {
//            System.out.println("size="+size+" but el.size="+set.size());
//        }
//    }

    public ObjectArray getAllChanged() {
//        if(Database.CHECK) {
//            testConsistency();
//        }
        // TODO cache: should probably use the LRU list
        ObjectArray list = new ObjectArray();         
        for (int i = 0; i < len; i++) {
            CacheObject rec = values[i];
            while (rec != null) {
                if (rec.isChanged()) {
                    list.add(rec);
                    if (list.size() >= recordCount) {
                        if (SysProperties.CHECK) {
                            if (list.size() > recordCount) {
                                throw Message.getInternalError("cache chain error");
                            }
                        } else {
                            break;
                        }
                    }
                }
                rec = rec.chained;
            }
        }
        return list;
    }
    
    public void setMaxSize(int maxKb) throws SQLException {
        int newSize = maxKb * 1024 / 4;        
        maxSize = newSize < 0 ? 0 : newSize;
        // can not resize, otherwise existing records are lost
        // resize(maxSize);
        removeOldIfRequired();
    }
    
    public String getTypeName() {
        return TYPE_NAME;
    }

}

// Unmaintained reference code (very old)
//import java.util.Iterator;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//public class Cache extends LinkedHashMap {
//
//    final static int MAX_SIZE = 1 << 10;
//    private CacheWriter writer;
//
//    public Cache(CacheWriter writer) {
//        super(16, (float) 0.75, true);
//        this.writer = writer;
//    }
//
//    protected boolean removeEldestEntry(Map.Entry eldest) {
//        if(size() <= MAX_SIZE) {
//            return false;
//        }
//        Record entry = (Record) eldest.getValue();
//        if(entry.getDeleted()) {
//            return true;
//        }
//        if(entry.isChanged()) {
//            try {
////System.out.println("cache write "+entry.getPos());
//                writer.writeBack(entry);
//            } catch(SQLException e) {
//                // TODO cache: printStackTrace not needed if we use our own hashtable
//                e.printStackTrace();
//            }
//        }
//        return true;
//    }
//
//    public void put(Record rec) {
//        put(new Integer(rec.getPos()), rec);
//    }
//
//    public Record get(int pos) {
//        return (Record)get(new Integer(pos));
//    }
//
//    public void remove(int pos) {
//        remove(new Integer(pos));
//    }
//
//    public ObjectArray getAllChanged() {
//        Iterator it = values().iterator();
//        ObjectArray list = new ObjectArray();
//        while(it.hasNext()) {
//            Record rec = (Record)it.next();
//            if(rec.isChanged()) {
//                list.add(rec);
//            }
//        }
//        return list;
//    }
//}

