package at.andrew.squashtap;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit room actions. Only sync/join/retryOutbox use the network. A scorer queues before uploading. */
final class RoomClient {
    interface Vault {
        JSONObject read() throws Exception;
        void write(JSONObject contents) throws Exception;
    }
    interface PendingJoin { Session accept() throws Exception; }
    interface DiscardDraft { void discard(String matchId) throws Exception; }
    interface Transport {
        JSONObject send(String method, String path, String bearer, JSONObject body) throws Exception;
    }
    static final class ServiceError extends IOException {
        final int status;
        ServiceError(int status) { super("Shared match service returned " + status); this.status = status; }
    }

    /** A presentation snapshot. Only the private referee QR contains the writer token in host mode. */
    static final class Session {
        final String id, role, matchId, token, key, joinCode, refereeLink, memberId;
        final boolean ready, watchJoin, watchJoinReady, pending, managed, needsCheckIn;
        final long seenRevision, expiresAt;
        private final String score;

        private Session(JSONObject r) throws Exception {
            id = r.getString("id");
            role = r.getString("role");
            matchId = r.getString("matchId");
            token = r.getString("score".equals(role) ? "writer" : "reader");
            key = r.getString("key");
            joinCode = r.optString("code", "");
            memberId = r.optString("memberId");
            managed = r.has("refereeInvite") || !memberId.isEmpty();
            needsCheckIn = !memberId.isEmpty() && !r.optBoolean("announced");
            ready = r.optBoolean("created") && !r.optBoolean("delegating");
            watchJoin = r.optBoolean("watchJoin");
            watchJoinReady = ready && watchJoin && r.optBoolean("joinApplied");
            seenRevision = r.getLong("seen");
            expiresAt = r.optLong("expires");
            pending = !ready || seenRevision < r.getLong("local") || watchJoin != r.optBoolean("joinApplied");
            refereeLink = "host".equals(role) && ready
                ? BuildConfig.SITE_URL + (managed ? "/referee-join.html#id=" : "/referee.html#id=") + id + (managed ? "&invite="+r.getString("refereeInvite") : "&token="+r.getString("writer")) + "&key=" + key : "";
            score = r.getString("snapshot");
        }

        JSONObject snapshot() {
            try { return new JSONObject(score); }
            catch (Exception corrupted) { throw new IllegalStateException("Invalid saved shared score", corrupted); }
        }
    }

    private static final Object DISK = new Object(), NETWORK = new Object();
    private static final String ACTIVE = "active";
    private static final int MAX_RECORDS = 12, MAX_HTTP_BYTES = 128 * 1024;
    private final Vault vault;
    private final Transport api;

    RoomClient(Context context) { this(new RoomFile(context.getApplicationContext()), RoomClient::request); }
    RoomClient(Vault vault, Transport api) { this.vault = vault; this.api = api; }

    Session current() throws Exception {
        synchronized (DISK) {
            JSONObject contents = vault.read(), room = contents.optJSONObject(contents.optString(ACTIVE));
            return room == null ? null : new Session(room);
        }
    }

    Session prepare(JSONObject snapshot, boolean referee, boolean watchJoin) throws Exception {
        MatchState match = validate(snapshot, null);
        if (snapshot.getBoolean("final") || match.complete()) throw new IllegalStateException("Choose an unfinished match");
        synchronized (DISK) {
            JSONObject contents = vault.read(), existing = contents.optJSONObject(contents.optString(ACTIVE));
            String role = referee ? "host" : "score";
            if (existing != null) {
                Session s = new Session(existing);
                if (s.role.equals(role) && s.matchId.equals(match.id())) return s;
                throw alreadyActive();
            }
            if (contents.length() - (contents.has("deviceSeed") ? 1 : 0) >= MAX_RECORDS) throw new IOException("Reconnect to finish sharing previous matches");
            String id = UUID.randomUUID().toString().replace("-", "");
            String key = TransferCrypto.random(32);
            JSONObject room = new JSONObject().put("id", id).put("role", role).put("matchId", match.id())
                .put("key", key).put("reader", TransferCrypto.random(32)).put("writer", TransferCrypto.random(32))
                .put("admin", TransferCrypto.random(32)).put("refereeInvite", TransferCrypto.random(32)).put("snapshot", snapshot.toString())
                .put("seen", 0).put("local", 0).put("watchJoin", watchJoin).put("seedJoin", watchJoin)
                .put("seed", RoomCrypto.seal(RoomCrypto.roomContext(id), key, snapshot.toString()));
            newPairing(room);
            vault.write(contents.put(id, room).put(ACTIVE, id));
            return new Session(room);
        }
    }

    private static void newPairing(JSONObject r) throws Exception {
        String code = RoomCrypto.newCode();
        JSONObject info = new JSONObject().put("id", r.getString("id")).put("key", r.getString("key"));
        if(r.has("refereeInvite"))info.put("managed",true);else info.put("token",r.getString("reader"));
        r.put("code", code).put("pairing", RoomCrypto.seal(RoomCrypto.pairContext(code), RoomCrypto.pairKey(code), info.toString()));
    }

    Session queue(JSONObject snapshot) throws Exception {
        return editActive(r -> {
            if (!"score".equals(r.getString("role"))) throw new IllegalStateException("This watch follows the score");
            return offer(r, snapshot);
        });
    }

    private static boolean offer(JSONObject r, JSONObject score) throws Exception {
        if (r.getString("snapshot").equals(score.toString())) return false;
        validate(score, r.getString("matchId"));
        if (new JSONObject(r.getString("snapshot")).getBoolean("final"))
            throw new IllegalStateException("The shared result is final");
        long local = r.getLong("local");
        if (local >= Integer.MAX_VALUE) throw new IllegalStateException("Shared match revision limit reached");
        r.put("snapshot", score.toString()).put("local", local + 1);
        return true;
    }

    /** Freeze local edits in the same commit as the last watch state. sync() enables the referee QR after ACK. */
    Session delegate(JSONObject snapshot) throws Exception {
        return editActive(r -> {
            if ("host".equals(r.getString("role"))) return false;
            if (!"score".equals(r.getString("role"))) throw new IllegalStateException("Only the host can invite a referee");
            if (validate(snapshot, r.getString("matchId")).complete()) throw new IllegalStateException("This match is complete");
            offer(r, snapshot);
            r.put("role", "host").put("delegating", true);
            return true;
        });
    }

    Session setWatchJoin(boolean enabled) throws Exception {
        return editActive(r -> {
            if (!r.has("admin")) throw new IllegalStateException("Only the host can share the watch code");
            if (enabled == r.optBoolean("watchJoin")) return false;
            r.put("watchJoin", enabled);
            return true;
        });
    }

    private String deviceToken(String code) throws Exception {
        synchronized(DISK) {
            JSONObject contents=vault.read();
            if(!contents.has("deviceSeed"))vault.write(contents.put("deviceSeed",TransferCrypto.random(32)));
            javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(RoomCrypto.decode(contents.getString("deviceSeed")),"HmacSHA256"));
            return RoomCrypto.encode(mac.doFinal(("KeepScore:watch:"+code).getBytes(StandardCharsets.UTF_8)));
        }
    }

    PendingJoin findJoin(String entry) throws Exception {
        String code=RoomCrypto.normalizeCode(entry);
        synchronized(NETWORK) {
            Session existing=current();
            if(existing!=null) {
                if(!"follow".equals(existing.role)||!code.equals(existing.joinCode))throw alreadyActive();
                sync();
                return ()->{Session active=current();if(active==null||!existing.id.equals(active.id))throw alreadyActive();return active;};
            }
            String token=deviceToken(code);
            JSONObject found=api.send("POST","/join",null,new JSONObject().put("code",code).put("deviceToken",token));
            JSONObject info=new JSONObject(RoomCrypto.open(RoomCrypto.pairContext(code),RoomCrypto.pairKey(code),found.getString("pairing")));
            String id=info.getString("id"), key=RoomCrypto.secret(info.getString("key"));
            boolean managed=info.optBoolean("managed");
            String reader=managed?token:RoomCrypto.secret(info.getString("token"));
            JSONObject reply=managed?found.getJSONObject("score"):api.send("GET","/"+id,reader,null);
            JSONObject score=new JSONObject(RoomCrypto.open(RoomCrypto.roomContext(id),key,reply.getString("cipher")));
            String matchId=validate(score,null).id();
            validateMetadata(reply,score);
            JSONObject room=new JSONObject().put("id",id).put("role","follow").put("matchId",matchId)
                .put("key",key).put("reader",reader).put("code",code).put("created",true);
            if(managed)room.put("memberId",found.getString("memberId"));
            recordReceived(room,reply,score);
            // Only the still-visible join attempt may attach this room. Its first score read checks the watch in.
            return ()->{
                synchronized(DISK) {
                    JSONObject contents=vault.read();
                    if(contents.has(ACTIVE))throw alreadyActive();
                    if(contents.has(id))throw new IllegalStateException("Reconnect to finish leaving this match");
                    vault.write(contents.put(id,room).put(ACTIVE,id));
                    return new Session(room);
                }
            };
        }
    }

    private JSONObject managedHost(String roomId) throws Exception {
        synchronized(DISK) {
            JSONObject contents=vault.read(),room=contents.optJSONObject(roomId);
            if(!roomId.equals(contents.optString(ACTIVE))||room==null||!room.has("admin")||!room.has("refereeInvite"))
                throw new IllegalStateException("Open a managed host room");
            return room;
        }
    }
    JSONObject participants(String roomId) throws Exception {
        return api.send("GET","/"+roomId+"/participants",managedHost(roomId).getString("admin"),null);
    }
    void removeParticipant(String roomId,String memberId) throws Exception {
        if(!memberId.matches("[a-f0-9]{24}"))throw new IllegalArgumentException("Invalid device");
        api.send("DELETE","/"+roomId+"/participants/"+memberId,managedHost(roomId).getString("admin"),null);
    }

    /** One foreground read, or a coalesced event-driven upload. Never holds the local lock during network I/O. */
    Session sync() throws Exception {
        synchronized (NETWORK) {
            Session current = current();
            if (current == null) return null;
            syncRoom(current.id);
            return current();
        }
    }

    private void syncRoom(String id) throws Exception {
        JSONObject r = record(id);
        if (r == null || "revoke".equals(r.optString("closing"))) return;
        if (!r.optBoolean("created")) {
            ensureCreated(id);
            r = record(id);
            if (r == null || "revoke".equals(r.optString("closing"))) return;
        }
        if (r.optBoolean("delegating") || "score".equals(r.getString("role")) && r.getLong("local") > r.getLong("seen")) {
            flush(id);
            r = record(id);
            if (r == null || "revoke".equals(r.optString("closing"))) return;
        }
        if (!r.has("closing") && r.has("admin") && r.optBoolean("watchJoin") != r.optBoolean("joinApplied")) {
            boolean enabled = r.getBoolean("watchJoin");
            api.send("PATCH", "/" + id, r.getString("admin"), new JSONObject().put("watchJoin", enabled));
            edit(id, state -> { state.put("joinApplied", enabled); return true; });
        }
        boolean checkIn=r.has("memberId")&&!r.optBoolean("announced");
        if (!"score".equals(r.getString("role")) && !r.optBoolean("delegating")
            && (checkIn||!new JSONObject(r.getString("snapshot")).getBoolean("final"))) {
            pull(id,r);
            if(checkIn)edit(id,state->{state.put("announced",true);return true;});
        }
    }

    private void ensureCreated(String id) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            JSONObject r = record(id);
            if (r == null || r.optBoolean("created") || "revoke".equals(r.optString("closing"))) return;
            JSONObject body = new JSONObject().put("id", id).put("writer", r.getString("writer"))
                .put("reader", r.getString("reader")).put("admin", r.getString("admin")).put("joinCode", r.getString("code"))
                .put("pairing", r.getString("pairing")).put("watchJoin", r.getBoolean("seedJoin"))
                .put("cipher", r.getString("seed")).put("revision", 0).put("final", false);
            if(r.has("refereeInvite"))body.put("refereeInvite",r.getString("refereeInvite"));
            JSONObject result;
            try {
                result = api.send("POST", "", null, body);
            } catch (ServiceError e) {
                if (e.status != 409) throw e;
                // A lost create reply is recoverable by our own reader credential. A code collision is retried.
                try {
                    result = api.send("GET", "/" + id, r.getString("reader"), null);
                    if (result.getLong("revision") != 0 || !body.getString("cipher").equals(result.getString("cipher")))
                        throw new IOException("The shared match changed during creation");
                } catch (ServiceError lookup) {
                    if (lookup.status != 404) throw lookup;
                    edit(id, state -> { newPairing(state); return true; });
                    continue;
                }
            }
            final long expiry = result.getLong("expiresAt");
            edit(id, state -> {
                state.put("created", true).put("expires", expiry).put("joinApplied", state.getBoolean("seedJoin"))
                    .put("ackHash", TransferCrypto.fingerprint(state.getString("seed")));
                state.remove("seed"); state.remove("seedJoin"); state.remove("pairing");
                return true;
            });
            return;
        }
        throw new IOException("Could not reserve a watch code. Please try again");
    }

    private void flush(String id) throws Exception {
        while (true) {
            JSONObject r = edit(id, state -> {
                if ("revoke".equals(state.optString("closing")) || state.has("flight")) return false;
                if (state.getLong("local") <= state.getLong("seen")) {
                    return state.remove("delegating") != null;
                }
                JSONObject score = new JSONObject(state.getString("snapshot"));
                JSONObject flight = new JSONObject().put("baseRevision", state.getLong("seen"))
                    .put("revision", state.getLong("local")).put("final", score.getBoolean("final"))
                    .put("cipher", RoomCrypto.seal(RoomCrypto.roomContext(id), state.getString("key"), score.toString()));
                state.put("flight", flight);
                return true;
            });
            if (r == null || "revoke".equals(r.optString("closing")) || !r.has("flight")) return;
            JSONObject flight = r.getJSONObject("flight");
            JSONObject reply;
            try {
                reply = api.send("PUT", "/" + id, r.getString("writer"), flight);
            } catch (ServiceError e) {
                if (e.status != 409) throw e;
                reply = api.send("GET", "/" + id, r.getString("reader"), null);
                if (reply.getLong("revision") != flight.getLong("revision")
                    || !reply.getString("cipher").equals(flight.getString("cipher"))
                    || reply.getBoolean("final") != flight.getBoolean("final"))
                    throw e;
            }
            final long revision = flight.getLong("revision"), expiry = reply.getLong("expiresAt");
            if (reply.optLong("revision", revision) != revision) throw new IOException("Unexpected shared match acknowledgement");
            edit(id, state -> {
                state.put("seen", revision).put("expires", expiry)
                    .put("ackHash", TransferCrypto.fingerprint(flight.getString("cipher")));
                state.remove("flight");
                return true;
            });
        }
    }

    private void pull(String id, JSONObject r) throws Exception {
        if (r.has("closing")) return;
        JSONObject reply = api.send("GET", "/" + id, r.getString("reader"), null);
        long revision = reply.getLong("revision"), seen = r.getLong("seen");
        if (revision < seen) return;
        String hash = TransferCrypto.fingerprint(reply.getString("cipher"));
        if (revision == seen && hash.equals(r.optString("ackHash"))) return;
        if (revision == seen) throw new IOException("Conflicting shared match revision");
        JSONObject score = new JSONObject(RoomCrypto.open(RoomCrypto.roomContext(id), r.getString("key"), reply.getString("cipher")));
        validate(score, r.getString("matchId"));
        validateMetadata(reply, score);
        edit(id, state -> {
            if (state.has("closing") || revision <= state.getLong("seen")) return false;
            recordReceived(state, reply, score);
            return true;
        });
    }

    private static void recordReceived(JSONObject r, JSONObject reply, JSONObject score) throws Exception {
        r.put("snapshot", score.toString()).put("seen", reply.getLong("revision")).put("local", reply.getLong("revision"))
            .put("expires", reply.getLong("expiresAt")).put("ackHash", TransferCrypto.fingerprint(reply.getString("cipher")));
    }

    /** Leave a received match, or keep a locally scored final in the outbox until its last PUT is acknowledged. */
    void leave() throws Exception { detach(false,null,null); }

    /** Queue revocation after a confirmed host cancellation. A later retry survives process death and network loss. */
    void revoke() throws Exception { detach(true,null,null); }

    boolean leaveConfirmed(Session shown, DiscardDraft discardDraft) throws Exception {
        return detach(!"follow".equals(shown.role),shown,discardDraft);
    }

    private boolean detach(boolean revoke, Session shown, DiscardDraft discardDraft) throws Exception {
        synchronized (DISK) {
            JSONObject contents = vault.read();
            String id = contents.optString(ACTIVE);
            JSONObject r = contents.optJSONObject(id);
            if (r == null) return false;
            boolean finished = new JSONObject(r.getString("snapshot")).getBoolean("final");
            if (shown != null) {
                // A referee can finish the match while the confirmation is on screen.
                if (!id.equals(shown.id) || !r.getString("role").equals(shown.role)
                    || finished != shown.snapshot().getBoolean("final")) return false;
                if ("host".equals(shown.role)) discardDraft.discard(shown.matchId);
            }
            if (revoke && r.has("admin") && !finished) r.put("closing", "revoke");
            else if ("follow".equals(r.optString("role")) && r.has("memberId")) r.put("closing","leave");
            else if (finished && "score".equals(r.getString("role")) && (!r.optBoolean("created") || r.getLong("local") > r.getLong("seen")))
                r.put("closing", "finish");
            else contents.remove(id);
            contents.remove(ACTIVE);
            vault.write(contents);
            return true;
        }
    }

    void retryOutbox() throws Exception {
        synchronized (NETWORK) {
            List<String> ids = new ArrayList<>();
            synchronized (DISK) {
                JSONObject contents = vault.read();
                for (Iterator<String> it = contents.keys(); it.hasNext();) {
                    String id = it.next();
                    if (contents.optJSONObject(id) != null && contents.getJSONObject(id).has("closing")) ids.add(id);
                }
            }
            Exception lastFailure = null;
            for (String id : ids) {
                JSONObject r = record(id);
                if (r == null) continue;
                try {
                    if ("revoke".equals(r.getString("closing"))) api.send("DELETE", "/" + id, r.getString("admin"), null);
                    else if ("leave".equals(r.optString("closing"))) api.send("DELETE","/"+id+"/participants/me",r.getString("reader"),null);
                    else syncRoom(id);
                } catch (Exception e) {
                    if (!(e instanceof ServiceError) || (((ServiceError)e).status != 404 && ((ServiceError)e).status != 410)) {
                        lastFailure = e;
                        continue;
                    }
                }
                synchronized (DISK) {
                    JSONObject contents = vault.read();
                    if (id.equals(contents.optString(ACTIVE))) continue;
                    contents.remove(id);
                    vault.write(contents);
                }
            }
            if (lastFailure != null) throw lastFailure;
        }
    }

    private static IllegalStateException alreadyActive() { return new IllegalStateException("Leave the current shared match first"); }
    private interface Change { boolean apply(JSONObject room) throws Exception; }

    private Session editActive(Change change) throws Exception {
        synchronized (DISK) {
            JSONObject contents = vault.read(), r = contents.optJSONObject(contents.optString(ACTIVE));
            if (r == null) throw new IllegalStateException("No shared match is open");
            if (change.apply(r)) vault.write(contents);
            return new Session(r);
        }
    }

    private JSONObject edit(String id, Change change) throws Exception {
        synchronized (DISK) {
            JSONObject contents = vault.read(), r = contents.optJSONObject(id);
            if (r != null && change.apply(r)) vault.write(contents);
            return r;
        }
    }

    private JSONObject record(String id) throws Exception {
        synchronized (DISK) { return vault.read().optJSONObject(id); }
    }

    private static MatchState validate(JSONObject score, String expectedId) throws Exception {
        if (score.getInt("schema") != 1 || !(score.get("final") instanceof Boolean)
            || score.toString().getBytes(StandardCharsets.UTF_8).length > RoomCrypto.MAX_PLAIN)
            throw new IllegalArgumentException("Invalid shared match");
        MatchState match = MatchState.decode(score.getString("match"));
        if (expectedId != null && !expectedId.equals(match.id()) || score.getBoolean("final") && !match.complete())
            throw new IllegalArgumentException("Invalid shared match result");
        JSONArray labels = score.getJSONArray("labels");
        if (labels.length() != 4) throw new IllegalArgumentException("Invalid player labels");
        for (int i = 0; i < labels.length(); i++) {
            if (!(labels.get(i) instanceof String)) throw new IllegalArgumentException("Invalid player label");
            String text = labels.getString(i);
            if (!text.isEmpty()) OpponentDirectory.validName(text);
        }
        long start = score.getLong("startedAt"), finish = score.getLong("finishedAt");
        if (start < 0 || finish < 0) throw new IllegalArgumentException("Invalid match timing");
        String zone = score.getString("zone");
        if (!zone.isEmpty() || start > 0 || finish > 0) ZoneId.of(zone);
        return match;
    }

    private static void validateMetadata(JSONObject reply, JSONObject score) throws Exception {
        if (reply.getLong("revision") < 0 || reply.getLong("revision") > Integer.MAX_VALUE
            || reply.getLong("expiresAt") <= 0 || reply.getBoolean("final") != score.getBoolean("final"))
            throw new IllegalArgumentException("Invalid shared match metadata");
    }

    static boolean validPath(String path){
        return path.matches("(?:/join|/[a-f0-9]{32}(?:/participants(?:/(?:me|[a-f0-9]{24}))?)?)?");
    }
    private static JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        if (!validPath(path)) throw new IllegalArgumentException("Invalid room path");
        HttpURLConnection c = (HttpURLConnection)new URL(BuildConfig.SITE_URL + "/api/rooms" + path).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setUseCaches(false); c.setRequestMethod("PATCH".equals(method) ? "POST" : method);
        c.setRequestProperty("Cache-Control", "no-store");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + RoomCrypto.secret(token));
        try {
            if (body != null) {
                byte[] value = body.toString().getBytes(StandardCharsets.UTF_8);
                if (value.length > MAX_HTTP_BYTES) throw new IOException("Shared match is too large");
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(value.length);
                try (OutputStream out = c.getOutputStream()) { out.write(value); }
            }
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new ServiceError(status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] block = new byte[4096];
                int n;
                while ((n = in.read(block)) != -1) {
                    if (bytes.size() + n > MAX_HTTP_BYTES) throw new IOException("Room response is too large");
                    bytes.write(block, 0, n);
                }
            }
            return bytes.size() == 0 ? new JSONObject() : new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
}
