package at.andrew.squashtap;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Watch-side room navigation and visible-only synchronization. Local book commits never wait for HTTP. */
final class MatchRooms {
    private final MainActivity a;
    private final RoomClient client;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::sync;
    private final ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback network;
    private RoomClient.Session s;
    private MatchState match;
    private JSONObject data;
    private String lastLocal = "", error = "";
    private TextView connection, summary;
    private boolean visible, destroyed, syncing, joining, askQr, blocked, terminalError, outboxRequested;
    private int failures;
    private long generation;

    MatchRooms(MainActivity activity) {
        a = activity;
        client = new RoomClient(a);
        connectivity = (ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE);
        try { apply(client.current()); }
        catch (Exception failure) { blocked = true; error = text("The saved connection could not be opened.","Die gespeicherte Verbindung lässt sich nicht öffnen.","Impossible d’ouvrir la connexion enregistrée.","No se pudo abrir la conexión guardada."); }
    }
    String text(String en, String de, String fr, String es) { return WatchLanguage.get(a).pick(en,de,fr,es); }
    String title() { return text("Referee & watches","Schiedsrichter & Uhren","Arbitre et montres","Árbitro y relojes"); }
    String joinTitle() { return text("Follow with a code","Per Code mitlesen","Suivre avec un code","Seguir con un código"); }
    boolean readOnly() { return blocked || joining || s != null && !"score".equals(s.role); }
    boolean isHost() { return s != null && "host".equals(s.role); }
    boolean finalScore() { return data != null && data.optBoolean("final"); }
    String widgetRevision() {
        return s==null||"score".equals(s.role)?"":s.id+"|"+s.role+"|"+s.seenRevision;
    }
    MatchState displayed() { return readOnly() ? match : null; }
    long time(String name) { return data == null ? 0 : data.optLong(name); }
    private void apply(RoomClient.Session session) {
        s = session;
        if (s == null) { data=null; match=null; lastLocal=""; return; }
        JSONObject value = s.snapshot();
        if (data==null || !data.optString("match").equals(value.optString("match")))
            match=MatchState.decode(value.optString("match"));
        data=value;
        if ("score".equals(s.role)) lastLocal=value.toString();
    }
    private String label(int i) {
        String name=data==null ? "" : data.optJSONArray("labels").optString(i);
        if (!name.isEmpty()) return name;
        if (i==0 && isHost()) return text("Me","Ich","Moi","Yo");
        return text("Player ","Spieler ","Joueur ","Jugador ")+(i+1);
    }
    String team(int side) {
        return label(side) + (match!=null && match.format().doubles ? " & "+label(side+2) : "");
    }
    String serveName(int slot) {
        if (slot < 0) return "";
        return slot < 4 ? label(slot) : team(slot-4);
    }
    void decorate(ScoreView counter) {
        if (!readOnly() || match==null) {
            counter.remote(readOnly() ? status() : null,false);
            return;
        }
        counter.match(match); counter.ownName(label(0)); counter.opponent(team(1));
        counter.players(label(2),label(1),label(3)); counter.session(true,false);
        counter.remote(status(),finalScore());
    }
    private String status() {
        if (!error.isEmpty()) return error;
        if (joining) return text("Connecting…","Verbindung wird hergestellt…","Connexion…","Conectando…");
        if (s==null) return "";
        if (!s.ready) return text("Waiting for connection","Warte auf Verbindung","En attente de connexion","Esperando conexión");
        if (finalScore()) return text("Final result","Endergebnis","Résultat final","Resultado final");
        if (s.pending) return text("Sync pending","Abgleich ausstehend","Synchronisation en attente","Sincronización pendiente");
        if (isHost()) return text("Referee is scoring","Schiedsrichter zählt","L’arbitre compte","El árbitro anota");
        return readOnly() ? text("Live · read only","Live · zum Mitlesen","Direct · lecture seule","En directo · solo lectura")
            : text("Sharing with watches","Mit Uhren geteilt","Partagé avec les montres","Compartido con relojes");
    }
    private JSONObject local() throws Exception { return a.roomSnapshot(); }

    // Runs immediately after native score persistence. A distinct local commit retains the outbox even when offline.
    void localChanged() {
        if (s==null || !"score".equals(s.role)) return;
        try {
            JSONObject score=local();
            if (!s.matchId.equals(a.roomMatchId()) || finalScore() && !score.getBoolean("final")) {
                if (finalScore()) client.leave(); else client.revoke();
                ++generation; apply(null); requestOutbox(); return;
            }
            if (lastLocal.equals(score.toString())) return;
            apply(client.queue(score)); schedule(0);
        } catch (Exception failure) { error=problem(failure,false); }
    }
    void resume() {
        visible=true;localChanged();
        if (network==null && connectivity!=null) {
            network=new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network ignored) { main.post(()->{if(visible){failures=0;requestOutbox();schedule(0);}}); }
                @Override public void onLost(Network ignored) { main.post(()->{if(visible && readOnly() && !finalScore() && !blocked){schedule(0);}}); }
            };
            try { connectivity.registerDefaultNetworkCallback(network); } catch(RuntimeException unavailable) { network=null; }
        }
        requestOutbox(); schedule(0);
    }
    void pause() {
        visible=false; askQr=false; main.removeCallbacks(tick);
        if (network!=null) { try { connectivity.unregisterNetworkCallback(network); } catch(RuntimeException ignored){} network=null; }
    }
    void destroy() { destroyed=true; pause(); io.shutdownNow(); }
    private void requestOutbox() {
        if (destroyed || outboxRequested) return;
        outboxRequested=true;
        io.execute(()->{boolean failed=false;try { client.retryOutbox(); } catch(Exception deferred){failed=true;}
            final boolean retry=failed;main.post(()->{outboxRequested=false;if(retry && visible && !destroyed)main.postDelayed(()->{if(visible)requestOutbox();},30000);});});
    }
    private void schedule(long delay) {
        main.removeCallbacks(tick);
        if (visible && !destroyed && s!=null && !terminalError) main.postDelayed(tick,delay);
    }
    private void sync() {
        if (!visible || destroyed || syncing || joining || s==null || terminalError) return;
        if (s.ready && !s.pending && ("score".equals(s.role) || finalScore())) return;
        syncing=true;
        final long attempt=generation;
        io.execute(()->{
            Exception issue=null;
            try { client.sync(); } catch(Exception failure) { issue=failure; }
            final Exception failure=issue;
            main.post(()->{
                syncing=false; if(destroyed) return;
                if(attempt!=generation){schedule(0);return;}
                String old=shape(); boolean wasFinal=finalScore();
                try { apply(client.current()); } catch(Exception invalid) { blocked=true; }
                if (failure==null) {error=""; failures=0;}
                else {
                    failures=Math.min(5,failures+1);
                    error=problem(failure,false);terminalError=gone(failure);
                }
                a.refreshRoomScore();
                if (visible && a.roomHubVisible()) {
                    if (askQr && isHost() && s.ready) qr();
                    else if (!old.equals(shape())) hub(); else refreshText();
                } else if (visible && !wasFinal && finalScore() && readOnly() && a.roomScoreVisible()) hub();
                schedule(failures>0 ? Math.min(30000,2000L<<failures) : 3000);
            });
        });
    }
    private static boolean gone(Exception e) {return e instanceof RoomClient.ServiceError && (((RoomClient.ServiceError)e).status==404 || ((RoomClient.ServiceError)e).status==410);}
    private String problem(Exception e, boolean code) {
        if(gone(e)) return code ? text("Code not found. Check the host watch.","Code nicht gefunden. Prüfe die Gastgeberuhr.","Code introuvable. Vérifiez la montre hôte.","Código no encontrado. Revisa el reloj anfitrión.")
            : text("Sharing ended or expired","Freigabe beendet oder abgelaufen","Partage terminé ou expiré","El enlace terminó o caducó");
        if(e instanceof RoomClient.ServiceError && ((RoomClient.ServiceError)e).status==429)
            return text("Too many attempts. Try later.","Zu viele Versuche. Später erneut versuchen.","Trop de tentatives. Réessayez plus tard.","Demasiados intentos. Inténtalo más tarde.");
        if(e instanceof IllegalArgumentException && code)
            return text("Enter the 10-character code.","Gib den zehnstelligen Code ein.","Saisissez les 10 caractères du code.","Introduce el código de 10 caracteres.");
        return text("Offline · waiting to sync","Offline · warte auf Abgleich","Hors ligne · en attente","Sin conexión · esperando");
    }
    private String shape() { return (s==null?"":s.role+s.ready+s.watchJoin+s.watchJoinReady)+finalScore()+terminalError; }
    private void refreshText() {
        if(connection!=null) connection.setText(status());
        if(summary!=null && match!=null) summary.setText(team(0)+"  "+match.gamesWon(0)+"–"+match.gamesWon(1)+"  "+team(1)+"\n"+a.roomSport(match.format().sport));
    }
    void hub() {
        LinearLayout page=a.roomPanel(title(),"room-hub");summary=null;
        connection=a.paragraph(page,status()); connection.setVisibility(s==null && !blocked && !joining ? android.view.View.GONE : android.view.View.VISIBLE);
        if(blocked){a.backButton(page,text("Close app","App schließen","Fermer","Cerrar"),a::finish);return;}
        if(joining){a.backButton(page,text("Close","Schließen","Fermer","Cerrar"),a::finish);return;}
        if(s==null) {
            a.paragraph(page,text("Let a referee score on a phone, or share your watch score with another watch.",
                "Lass einen Schiedsrichter am Handy zählen oder teile den Spielstand deiner Uhr mit weiteren Uhren.",
                "Confiez le score à un arbitre sur téléphone ou partagez-le avec une autre montre.",
                "Deja que un árbitro anote en su móvil o comparte el marcador con otro reloj."));
            if(!a.roomMatchComplete()) {
                a.button(page,text("Referee on phone","Schiedsrichter am Handy","Arbitre sur téléphone","Árbitro en el móvil"),()->start(true));
                a.button(page,text("Share with watches","Mit Uhren teilen","Partager avec des montres","Compartir con relojes"),()->start(false));
            }
            a.backButton(page,text("Back to match","Zurück zur Partie","Retour au match","Volver al partido"),a::showMatchControls);
            return;
        }
        summary=a.paragraph(page,""); refreshText();
        if(finalScore() && readOnly()) {
            a.button(page,text("Save to my results","In meine Ergebnisse speichern","Enregistrer dans mes résultats","Guardar en mis resultados"),this::pickSeat);
        } else if(readOnly()) {
            a.button(page,text("Show score","Spielstand anzeigen","Afficher le score","Ver marcador"),a::closePanel);
        }
        if(isHost() && s.ready && !finalScore() && !terminalError)
            a.navigationButton(page,text("Referee QR","Schiedsrichter-QR","QR de l’arbitre","QR del árbitro"),this::qr);
        if("score".equals(s.role) && !finalScore() && !terminalError)
            a.navigationButton(page,text("Hand over to referee","An Schiedsrichter übergeben","Confier à l’arbitre","Pasar al árbitro"),()->start(true));
        if(!"follow".equals(s.role) && !terminalError) {
            if(s.watchJoinReady) {
                TextView code=a.paragraph(page,s.joinCode.substring(0,5)+" "+s.joinCode.substring(5)); code.setTextSize(22);
                a.paragraph(page,text("On the other watch, open Follow with a code.","Auf der anderen Uhr „Per Code mitlesen“ öffnen.","Sur l’autre montre, ouvrez « Suivre avec un code ».","En el otro reloj abre « Seguir con un código »."));
            }
            a.navigationButton(page,s.watchJoin ? text("Hide joining code","Beitrittscode deaktivieren","Désactiver le code","Desactivar el código")
                : text("Invite other watches","Weitere Uhren einladen","Inviter d’autres montres","Invitar a otros relojes"),this::toggleJoin);
        }
        if(s.pending || !error.isEmpty()) a.navigationButton(page,text("Sync now","Jetzt abgleichen","Synchroniser","Sincronizar"),()->{terminalError=false;schedule(0);});
        if(readOnly()) a.navigationButton(page,text("Language","Sprache","Langue","Idioma"),()->a.roomLanguage(this::hub));
        a.navigationButton(page,readOnly() ? text("Leave shared match","Geteilte Partie verlassen","Quitter le match partagé","Salir del partido compartido")
            : text("Stop watch sharing","Uhrenfreigabe beenden","Arrêter le partage","Dejar de compartir"),this::confirmLeave);
        a.backButton(page,readOnly() ? text("Back to score","Zurück zum Spielstand","Retour au score","Volver al marcador")
            : text("Back to match","Zurück zur Partie","Retour au match","Volver al partido"),readOnly()?a::closePanel:a::showMatchControls);
    }
    private void start(boolean referee) {
        try {
            JSONObject score=local();
            apply(s==null ? client.prepare(score,referee,!referee) : client.delegate(score));
            ++generation; failures=0;terminalError=false; error="";askQr=referee;
            if(referee) a.pauseForRoom();
            hub();schedule(0);
        } catch(Exception failure) { error=problem(failure,false); a.roomMessage(error,this::hub); }
    }
    private void toggleJoin() {
        try { apply(client.setWatchJoin(!s.watchJoin)); hub();schedule(0); }
        catch(Exception failure){a.roomMessage(problem(failure,false),this::hub);}
    }
    private void qr() {askQr=false;if(s!=null && isHost() && s.ready && !finalScore())a.showQr(s.refereeLink,text("Scan to referee","Zum Zählen scannen","Scanner pour arbitrer","Escanear para arbitrar"),this::hub,text("Back","Zurück","Retour","Volver"));}
    void joinPage() {
        if(s!=null || blocked || joining){hub();return;}
        LinearLayout p=a.roomPanel(joinTitle(),"room-code");
        a.paragraph(p,text("Enter the code shown on the host watch.","Gib den Code von der Gastgeberuhr ein.","Saisissez le code affiché sur la montre hôte.","Introduce el código del reloj anfitrión."));
        EditText field=new EditText(a); field.setSingleLine(); field.setTextColor(android.graphics.Color.WHITE); field.setGravity(android.view.Gravity.CENTER);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setFilters(new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(14)});
        field.setContentDescription(joinTitle());p.addView(field);
        a.button(p,text("Join match","Partie beitreten","Rejoindre le match","Unirse al partido"),()->join(field.getText().toString()));
        a.backButton(p,text("Back","Zurück","Retour","Volver"),a::showSports);
    }
    private void join(String code) {
        if(joining || s!=null) return;
        joining=true;error="";hub();final long attempt=++generation;
        io.execute(()->{
            try {
                RoomClient.Session joined=client.join(code);
                main.post(()->{if(destroyed||attempt!=generation)return;joining=false;apply(joined);a.pauseForRoom();errorsReset();if(visible){if(finalScore())hub();else a.closePanel();}schedule(0);});
            } catch(Exception e) {main.post(()->{if(destroyed||attempt!=generation)return;joining=false;a.roomMessage(problem(e,true),this::joinPage);});}
        });
    }
    private void errorsReset(){error="";terminalError=false;failures=0;}
    private void pickSeat() {
        if(!finalScore() || !readOnly()) return;
        if(isHost() || a.roomAlreadySaved(match.id())) {save(0);return;}
        LinearLayout p=a.roomPanel(text("Who did you play as?","Als wer hast du gespielt?","Qui étiez-vous ?","¿Qué jugador eras?"),"room-seat");
        a.paragraph(p,text("Your record uses the selected player’s side.","Deine Bilanz wird aus Sicht dieser Person gespeichert.","Votre bilan suivra le côté de ce joueur.","Tu historial usará el lado de este jugador."));
        for(int i=0;i<(match.format().doubles?4:2);i++){final int seat=i;a.button(p,label(i),()->save(seat));}
        a.backButton(p,text("Back","Zurück","Retour","Volver"),this::hub);
    }
    private void save(int seat) {
        if(!finalScore() || !readOnly() || match==null) return;
        try {
            String id=match.id(),sport=match.format().sport;
            a.saveRoomResult(match,data,seat,isHost());
            client.leave(); ++generation; apply(null);errorsReset();
            a.openSavedRoomResult(id,sport);
        } catch(Exception failed){a.roomMessage(text("Could not save. The final score is still here. Check available result storage and retry.","Speichern fehlgeschlagen. Das Endergebnis bleibt hier. Prüfe den Ergebnisspeicher und versuche es erneut.","Échec de l’enregistrement. Le résultat reste disponible. Vérifiez l’espace et réessayez.","No se pudo guardar. El resultado sigue aquí. Revisa el espacio y vuelve a intentarlo."),this::hub);}
    }
    private void confirmLeave(){
        if(s==null) return;
        boolean host=isHost(),finished=finalScore();
        LinearLayout p=a.roomPanel(text("Leave match?","Partie verlassen?","Quitter le match ?","¿Salir del partido?"),"room-leave");
        String message=finished ? text("Leave without making another copy of this result?","Diese Ansicht ohne weitere Ergebniskopie schließen?","Quitter sans enregistrer une autre copie du résultat ?","¿Salir sin guardar otra copia del resultado?")
            : host ? text("Cancel this referee match for all connected devices and discard the local match?","Gerichtete Partie für alle verbundenen Geräte beenden und lokal verwerfen?","Annuler ce match arbitré sur tous les appareils et effacer la partie locale ?","¿Cancelar este partido arbitrado en todos los dispositivos y descartar la partida local?")
            : readOnly() ? text("The other watches will keep following the match.","Die anderen Uhren verfolgen die Partie weiter.","Les autres montres continueront à suivre le match.","Los demás relojes seguirán viendo el partido.")
            : text("The other watches will stop receiving this score. You can keep scoring locally.","Weitere Uhren erhalten keine neuen Punkte mehr. Du kannst auf deiner Uhr weiterzählen.","Les autres montres cesseront de recevoir ce score. Vous pouvez continuer à compter.","Los demás relojes dejarán de recibir este marcador. Puedes seguir anotando.");
        a.paragraph(p,message); a.deleteButton(p,text("Leave","Verlassen","Quitter","Salir"),()->{
            try{
                if(host) a.discardRoomDraft(s.matchId);
                if(!finished && !"follow".equals(s.role)) client.revoke(); else client.leave();
                ++generation;apply(null);errorsReset();requestOutbox();a.restoreLocalScore();a.showSports();
            }catch(Exception e){a.roomMessage(problem(e,false),this::hub);}
        });
        a.backButton(p,text("Keep match","Partie behalten","Garder le match","Conservar el partido"),this::hub);
    }
}
