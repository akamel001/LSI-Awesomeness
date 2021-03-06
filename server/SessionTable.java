package server;

import identifiers.FormData.Location;
import identifiers.SID;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import rpc.message.RpcMessageCall.ReadResult;

public class SessionTable extends Thread {
    public static SessionTable table = new SessionTable();
    
    public HashMap<SID, Entry> sessionTable = new HashMap<SID, Entry>();
    public CacheTable cacheTable = new CacheTable();
    
    final public static int MAX_CACHE_SIZE = 3;

	private static final boolean DEBUG = true;

    public static class Entry {
      public int version;
      public String message;
      public long expiration;

      /**
       * Constructor, for convenience
       * @param version
       * @param message
       * @param expiration
       */
      public Entry(int version, String message, long expiration) {
          this.version = version;
          this.message = message;
          this.expiration = expiration;
      }

      @Override
      public String toString() {
          return this.version + ":" + this.message + ":" + this.expiration;
      }
    };

    private SessionTable() {};

    /**
     * Looks up a session
     * @param sessionID
     * @return
     */
    public synchronized Entry get(SID sessionID, int changeCount) {
        if(sessionTable.containsKey(sessionID)) {
            Entry entry = sessionTable.get(sessionID);
            if(entry != null && entry.version >= changeCount)
                return sessionTable.get(sessionID);
        }
        
        if(cacheTable.containsKey(sessionID)) {
            Entry entry = sessionTable.get(sessionID);
            if(entry != null && entry.version >= changeCount) {
            	FormManager.getInstance().getData().setLoc(Location.cache);
            	return cacheTable.get(sessionID);
            }
        }
        return null;
    }

    public synchronized void cache(SID sessionID, ReadResult result, int changeCount) {
        cacheTable.put(sessionID, new Entry(changeCount, result.getData(), result.getDiscardTime())); 
    }
    
    /**
     * Adds a new session to the table
     * @param sessionID
     * @param entry
     */
    public synchronized void put(SID sessionID, Entry entry) {
        sessionTable.put(sessionID, entry);
    }

    public synchronized void destroySession(SID sessionID, int version) {
        Entry session = sessionTable.get(sessionID);
        if(session != null && session.version <= version)
            sessionTable.remove(session);
        session = cacheTable.get(sessionID);
        if(session != null &&session.version <= version)
            cacheTable.remove(session);
    }

    /**
     * Destroys the session with the given ID
     * @param sessionID
     */
    public synchronized void destroySession(SID sessionID) {
        sessionTable.remove(sessionID);
        cacheTable.remove(sessionID);
    }

    /**
     * Removes all sessions that have expired from this table
     */
    public synchronized void cleanExpiredSessions() {
        Date now = Calendar.getInstance().getTime();
        Iterator<Map.Entry<SID, Entry>> iter = sessionTable.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<SID, Entry> next = iter.next();
            if(new Date(next.getValue().expiration).before(now)) {
                iter.remove();
            }
        }
    }

    public static SessionTable getInstance() {
        return table;
    }

    @Override
    public String toString() {
        return sessionTable.toString();
    }
    
    public void run() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        cleanExpiredSessions();
    }
    
    private class CacheTable extends LinkedHashMap<SID, Entry> {
        private static final long serialVersionUID = -7317831993274795114L;
        protected boolean removeEldestEntry(Map.Entry<SID, Entry> eldest) {
            if(size() > MAX_CACHE_SIZE){
            	FormManager.getInstance().getData().setEviction(eldest.getKey());
                return true;
            }
            return false;
        }
    }
}
