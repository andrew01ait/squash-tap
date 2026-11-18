package at.andrew.squashtap;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RoomClientUiTest {
    private static final class MemoryVault implements RoomClient.Vault {
        private String encoded = "{}";
        public JSONObject read() throws Exception { return new JSONObject(encoded); }
        public void write(JSONObject contents) { encoded = contents.toString(); }
    }

    /** Drops successful replies and rejects all duplicate revisions, like a strict compare-and-swap relay. */
    private static final class Relay implements RoomClient.Transport {
        final Map<String,JSONObject> rooms = new LinkedHashMap<>();
        final Map<String,String> codes = new LinkedHashMap<>();
        boolean offline, loseCreate, losePut, collide;
        int acceptedWrites, reads;

        private JSONObject result(JSONObject r) throws Exception {
            return new JSONObject().put("revision", r.getInt("revision")).put("cipher", r.getString("cipher"))
                .put("final", r.getBoolean("final")).put("expiresAt", 9999999999999L);
        }
        public JSONObject send(String method, String path, String bearer, JSONObject body) throws Exception {
            if (offline) throw new IOException("offline");
            if (path.isEmpty()) {
                if (collide) { collide = false; throw new RoomClient.ServiceError(409); }
                String id = body.getString("id");
                if (rooms.containsKey(id) || codes.containsKey(body.getString("joinCode"))) throw new RoomClient.ServiceError(409);
                JSONObject r = new JSONObject(body.toString());
                rooms.put(id, r); codes.put(r.getString("joinCode"), id);
                if (loseCreate) { loseCreate = false; throw new IOException("create reply lost"); }
                return result(r);
            }
            if (path.equals("/join")) {
                JSONObject r = rooms.get(codes.get(body.getString("code")));
                if (r == null || !r.getBoolean("watchJoin")) throw new RoomClient.ServiceError(404);
                return new JSONObject().put("pairing", r.getString("pairing"));
            }
            String id = path.substring(1);
            JSONObject r = rooms.get(id);
            if (r == null) throw new RoomClient.ServiceError(404);
            if (method.equals("GET")) {
                if (!bearer.equals(r.getString("reader")) && !bearer.equals(r.getString("writer"))) throw new RoomClient.ServiceError(403);
                reads++;
                return result(r);
            }
            if (method.equals("PATCH") || method.equals("DELETE")) {
                if (!bearer.equals(r.getString("admin"))) throw new RoomClient.ServiceError(403);
                if (method.equals("DELETE")) rooms.remove(id); else r.put("watchJoin", body.getBoolean("watchJoin"));
                return new JSONObject();
            }
            if (!bearer.equals(r.getString("writer"))) throw new RoomClient.ServiceError(403);
            if (r.getBoolean("final") || body.getInt("baseRevision") != r.getInt("revision")
                || body.getInt("revision") <= r.getInt("revision")) throw new RoomClient.ServiceError(409);
            r.put("revision", body.getInt("revision")).put("cipher", body.getString("cipher")).put("final", body.getBoolean("final"));
            acceptedWrites++;
            if (losePut) { losePut = false; throw new IOException("write reply lost"); }
            return result(r);
        }

        void refereeFinal(String id, JSONObject score, String key) throws Exception {
            JSONObject r = rooms.get(id);
            send("PUT", "/" + id, r.getString("writer"), new JSONObject().put("baseRevision", r.getInt("revision"))
                .put("revision", r.getInt("revision") + 1).put("final", true)
                .put("cipher", RoomCrypto.seal(RoomCrypto.roomContext(id), key, score.toString())));
        }
    }

    private static JSONObject snapshot(MatchState match, boolean finished) throws Exception {
        return new JSONObject().put("schema", 1).put("match", match.encode()).put("labels", new JSONArray().put("Équipe").put("Alex").put("").put(""))
            .put("startedAt", match.hasPoints() ? 1000L : 0L).put("finishedAt", finished ? 2000L : 0L).put("zone", "Europe/Vienna").put("final", finished);
    }

    @Test public void createAndLastQueuedScoreSurviveOfflineRestartAndLostAcknowledgements() throws Exception {
        MemoryVault storage = new MemoryVault(); Relay relay = new Relay(); RoomClient client = new RoomClient(storage, relay);
        MatchState match = new MatchState("offline-id", 3);
        RoomClient.Session prepared = client.prepare(snapshot(match, false), false, true);
        assertFalse(prepared.ready);
        relay.loseCreate = true;
        assertThrows(IOException.class, client::sync);
        match.add(0); client.queue(snapshot(match, false));
        client = new RoomClient(storage, relay);
        relay.losePut = true;
        assertThrows(IOException.class, client::sync);
        match.add(1); match.add(0); client.queue(snapshot(match, false));
        client = new RoomClient(storage, relay);
        RoomClient.Session ready = client.sync();
        assertEquals(prepared.id, ready.id); assertEquals(2, ready.seenRevision);
        assertTrue(ready.ready); assertTrue(ready.watchJoinReady); assertFalse(ready.pending);
        assertEquals(2, relay.acceptedWrites);
        MatchState received = MatchState.decode(ready.snapshot().getString("match"));
        assertEquals(2, received.game().blue()); assertEquals(1, received.game().red());
        int reads = relay.reads; client.sync();
        assertEquals("the scorer does not poll after an ACK", reads, relay.reads);
    }

    @Test public void handoffRetainsReadersAndCodeAndFreezesWatchBeforeShowingRefereeCredentials() throws Exception {
        MemoryVault storage = new MemoryVault(); Relay relay = new Relay();
        RoomClient host = new RoomClient(storage, relay), follower = new RoomClient(new MemoryVault(), relay);
        MatchState match = new MatchState("handoff-id", 3); host.prepare(snapshot(match, false), false, true);
        RoomClient.Session scoring = host.sync();
        RoomClient.Session following = follower.join(scoring.joinCode.substring(0,5) + "-" + scoring.joinCode.substring(5));
        assertEquals("", following.refereeLink); assertEquals("", scoring.refereeLink);
        assertNotEquals(scoring.token, following.token);
        assertThrows(IllegalStateException.class, () -> follower.queue(snapshot(match, false)));
        assertThrows(IllegalStateException.class, () -> follower.setWatchJoin(true));
        relay.offline = true; match.add(0);
        RoomClient.Session frozen = host.delegate(snapshot(match, false));
        assertEquals("host", frozen.role); assertEquals("", frozen.refereeLink); assertFalse(frozen.ready);
        assertThrows(IllegalStateException.class, () -> host.queue(snapshot(match, false)));
        assertThrows(IOException.class, host::sync);
        RoomClient restarted = new RoomClient(storage, relay); assertFalse(restarted.current().ready);
        relay.offline = false; RoomClient.Session delegated = restarted.sync();
        assertTrue(delegated.refereeLink.contains("#id=" + scoring.id + "&token=" + scoring.token));
        assertEquals(following.token, delegated.token); assertEquals(scoring.joinCode, delegated.joinCode);
        assertEquals(1, MatchState.decode(follower.sync().snapshot().getString("match")).game().blue());
        match.retire(1); relay.refereeFinal(scoring.id, snapshot(match, true), scoring.key);
        assertTrue(restarted.sync().snapshot().getBoolean("final")); assertTrue(follower.sync().snapshot().getBoolean("final"));
        relay.offline = true;
        assertTrue("cached final remains available for explicit save", restarted.sync().snapshot().getBoolean("final"));
        assertTrue(follower.current().snapshot().getBoolean("final"));
    }

    @Test public void togglingJoiningUsesAdminAndOnlyControlsFuturePairs() throws Exception {
        Relay relay = new Relay(); RoomClient host = new RoomClient(new MemoryVault(), relay);
        MatchState m = new MatchState("secret-id", 3); RoomClient.Session s = host.prepare(snapshot(m, false), true, false);
        s = host.sync(); RoomClient guest = new RoomClient(new MemoryVault(), relay); final String code = s.joinCode;
        assertThrows(RoomClient.ServiceError.class, () -> guest.join(code));
        assertFalse(host.setWatchJoin(true).watchJoinReady); assertTrue(host.sync().watchJoinReady);
        assertEquals("follow", guest.join(code).role);
        host.setWatchJoin(false); host.sync();
        assertThrows(RoomClient.ServiceError.class, () -> new RoomClient(new MemoryVault(), relay).join(code));
        assertEquals(s.matchId, guest.sync().matchId);
    }

    @Test public void detachedFinalSurvivesNewMatchAndOfflineRevocationRetries() throws Exception {
        MemoryVault storage = new MemoryVault(); Relay relay = new Relay(); RoomClient host = new RoomClient(storage, relay);
        MatchState m = new MatchState("first", 3); host.prepare(snapshot(m, false), false, true);
        RoomClient.Session initial = host.sync(); relay.offline = true; m.retire(0); host.queue(snapshot(m, true));
        host.leave(); assertNull(host.current());
        m = new MatchState("second", 3); RoomClient.Session next = host.prepare(snapshot(m, false), false, true);
        host = new RoomClient(storage, relay); assertThrows(IOException.class, host::retryOutbox);
        relay.offline = false; host.retryOutbox();
        assertEquals(next.id, host.current().id); assertTrue(relay.rooms.get(initial.id).getBoolean("final"));
        host.sync(); relay.offline = true; host.revoke(); assertNull(host.current());
        relay.offline = false; new RoomClient(storage, relay).retryOutbox();
        assertFalse(relay.rooms.containsKey(next.id)); assertTrue(relay.rooms.containsKey(initial.id));
    }

    @Test public void codeCollisionRetriesAndCredentialsStayOutOfPairing() throws Exception {
        Relay relay = new Relay(); relay.collide = true; RoomClient host = new RoomClient(new MemoryVault(), relay);
        MatchState m = new MatchState("code-collision", 3);
        RoomClient.Session pending = host.prepare(snapshot(m, false), true, true), ready = host.sync();
        assertEquals(pending.id, ready.id); assertNotEquals(pending.joinCode, ready.joinCode);
        JSONObject row = relay.rooms.get(ready.id);
        JSONObject pairing = new JSONObject(RoomCrypto.open(RoomCrypto.pairContext(ready.joinCode),
            RoomCrypto.pairKey(ready.joinCode), row.getString("pairing")));
        assertEquals(3, pairing.length()); assertEquals(row.getString("reader"), pairing.getString("token"));
        assertFalse(pairing.toString().contains(row.getString("writer"))); assertFalse(pairing.toString().contains(row.getString("admin")));
        assertFalse(ready.refereeLink.contains(row.getString("admin")));
    }

    @Test public void foregroundScoreCanCommitWhileAnOlderPointIsUploading() throws Exception {
        MemoryVault storage = new MemoryVault(); Relay relay = new Relay();
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        RoomClient client = new RoomClient(storage, (method, path, token, body) -> {
            if ("PUT".equals(method) && first.getAndSet(false)) {
                started.countDown();
                if (!finish.await(8, TimeUnit.SECONDS)) throw new IOException("test upload timeout");
            }
            return relay.send(method, path, token, body);
        });
        MatchState match = new MatchState("concurrent", 3);
        client.prepare(snapshot(match, false), false, true); client.sync();
        match.add(0); client.queue(snapshot(match, false));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<RoomClient.Session> uploaded = worker.submit(client::sync);
            assertTrue(started.await(8, TimeUnit.SECONDS));
            match.add(1); new RoomClient(storage, relay).queue(snapshot(match, false));
            finish.countDown();
            RoomClient.Session ack = uploaded.get(8, TimeUnit.SECONDS);
            assertEquals(2, ack.seenRevision); assertFalse(ack.pending);
            assertEquals(match.encode(), ack.snapshot().getString("match"));
            assertEquals(2, relay.acceptedWrites);
        } finally {
            finish.countDown(); worker.shutdownNow();
        }
    }

    @Test public void malformedFinalCannotReplaceTheLastValidCachedScore() throws Exception {
        Relay relay = new Relay(); RoomClient host = new RoomClient(new MemoryVault(), relay);
        MatchState match = new MatchState("still-playing", 3); match.add(1);
        host.prepare(snapshot(match, false), true, true); RoomClient.Session before = host.sync();
        RoomClient guest = new RoomClient(new MemoryVault(), relay); guest.join(before.joinCode);
        relay.refereeFinal(before.id, snapshot(match, true), before.key);
        assertThrows(IllegalArgumentException.class, guest::sync);
        assertEquals(0, guest.current().seenRevision); assertFalse(guest.current().snapshot().getBoolean("final"));
        assertEquals(match.encode(), guest.current().snapshot().getString("match"));
    }

    @Test public void storedEnvelopeCrossesRuntimeAndRejectsWrongRoomOrCode() throws Exception {
        String id = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", key = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc";
        String sample = "1.BAQEBAQEBAQEBAQE.LhFbqw6MFmkqmJziLx-8CWeV8GHmG8p8JuYXZ7Y9Mp1xBIveOWHHYuQyNIjd";
        assertEquals("{\"schema\":1,\"note\":\"Équipe\"}", RoomCrypto.open(RoomCrypto.roomContext(id), key, sample));
        assertThrows(Exception.class, () -> RoomCrypto.open(RoomCrypto.roomContext("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), key, sample));
        assertEquals("01ABCDEF23", RoomCrypto.normalizeCode("OlAbc-def23"));
        assertThrows(IllegalArgumentException.class, () -> RoomCrypto.normalizeCode("bad"));
    }

    @Test public void fileIsDeviceEncryptedAndIdleWatchesDoNotOpenKeystore() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File f = new File(context.getNoBackupFilesDir(), "room-file-test");
        SecretKeySpec secret = new SecretKeySpec(new byte[32], "AES"); int[] calls = {0};
        RoomFile file = new RoomFile(f, create -> { calls[0]++; return secret; });
        assertEquals(0, file.read().length()); assertEquals(0, calls[0]);
        file.write(new JSONObject().put("private", "SamplePrivateName"));
        String disk = new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(disk.contains("SamplePrivateName")); assertFalse(disk.contains("private"));
        assertEquals("SamplePrivateName", new RoomFile(f, create -> secret).read().getString("private"));
        byte[] damaged = Files.readAllBytes(f.toPath()); damaged[damaged.length-1] ^= 1; Files.write(f.toPath(), damaged);
        assertThrows(Exception.class, file::read);
        file.write(new JSONObject()); assertFalse(f.exists());
    }
}
