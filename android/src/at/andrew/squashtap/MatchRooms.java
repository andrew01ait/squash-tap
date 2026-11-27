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
    enum Entry { SPORTS, SPORT, MATCH, SHARED }
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
    private boolean visible, destroyed, syncing, askQr, blocked, terminalError, outboxRequested;
    private String joiningCode;
    private boolean watchSharingFromLive;
    private int failures;
    private long generation;
    private Entry entry=Entry.MATCH;

    MatchRooms(MainActivity activity) {
        a = activity;
        client = new RoomClient(a);
        connectivity = (ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE);
        try { apply(client.current()); }
        catch (Exception failure) { blocked = true; error = text("The saved connection could not be opened.","Die gespeicherte Verbindung lässt sich nicht öffnen.","Impossible d’ouvrir la connexion enregistrée.","No se pudo abrir la conexión guardada."); }
    }
    String text(String en, String de, String fr, String es) { return WatchLanguage.get(a).pick(en,de,fr,es); }
    String title() { return text("Live sharing & referee","Live teilen & Schiedsrichter","Direct et arbitrage","Directo y arbitraje"); }
    String backToSharing() { return text("Back to sharing","Zurück zum Teilen","Retour au partage","Volver a compartir"); }
    private String audienceTitle() { return text("Audience live link","Live-Link für Zuschauer","Lien pour le public","Enlace para el público"); }
    private String joinTitle() { return text("Join with watch code","Mit Uhrencode beitreten","Rejoindre avec un code","Unirse con código"); }
    private String watchSharingTitle() { return a.tr("Share with watches"); }
    private String refereeTitle() { return a.tr("Referee on phone"); }
    private String pairingTitle() { return a.tr("Pair watches"); }
    void open(Entry from) { entry=from;hub(); }
    boolean readOnly() { return blocked || joiningCode!=null || s != null && !"score".equals(s.role); }
    boolean hasRoom(){return s!=null;}
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
        String name=recordedName(i);
        if (!name.isEmpty()) return name;
        if (i==0 && isHost()) return text("Me","Ich","Moi","Yo");
        return text("Player ","Spieler ","Joueur ","Jugador ")+(i+1);
    }
    String team(int side) {
        return label(side) + (match!=null && match.format().doubles ? " & "+label(side+2) : "");
    }
    String recordedName(int slot) {
        return data == null ? "" : data.optJSONArray("labels").optString(slot);
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
        if (joiningCode!=null) return text("Connecting…","Verbindung wird hergestellt…","Connexion…","Conectando…");
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
        if (!visible || destroyed || syncing || joiningCode!=null || s==null || terminalError) return;
        if (s.ready && !s.pending && ("score".equals(s.role) || finalScore() && !s.needsCheckIn)) return;
        syncing=true;
        final long attempt=generation;
        io.execute(()->{
            Exception issue=null;
            try { client.sync(); } catch(Exception failure) { issue=failure; }
            final Exception failure=issue;
            main.post(()->{
                syncing=false; if(destroyed) return;
                if(attempt!=generation){schedule(0);return;}
                String old=shape(); boolean wasFinal=finalScore(); long previousRevision=s==null?-1:s.seenRevision;
                MatchState previousScore = displayed();
                try { apply(client.current()); } catch(Exception invalid) { blocked=true; }
                if (visible && failure == null && !blocked && s != null && s.seenRevision > previousRevision)
                    a.speakRoomScore(previousScore, displayed());
                if (failure==null) {error=""; failures=0;}
                else {
                    failures=Math.min(5,failures+1);
                    error=problem(failure,false);terminalError=gone(failure);
                }
                a.refreshRoomScore();
                if (visible && a.roomPanelVisible("room-referee")) {
                    if (askQr && isHost() && s.ready && !finalScore() && !terminalError) refereeQr();
                    else if (!old.equals(shape())) showReferee(); else refreshText();
                } else if (visible && a.roomPanelVisible("room-pairing")) {
                    if (!old.equals(shape())) showPairing(); else refreshText();
                } else if (visible && a.roomPanelVisible("room-watches")) {
                    if (!old.equals(shape())) showWatchSharing(); else refreshText();
                } else if (visible && a.roomPanelVisible("room-hub")) {
                    if (!old.equals(shape())) hub(); else refreshText();
                } else if(visible && a.roomMenuVisible()){
                    if(!old.equals(shape()))menu();else refreshText();
                } else if(visible && a.roomDetailsVisible() && s!=null && s.seenRevision!=previousRevision) a.showSharedMatchDetails();
                else if (visible && !wasFinal && finalScore() && readOnly()
                    && (a.roomScoreVisible() || a.roomCancelVisible())) menu();
                schedule(failures>0 ? Math.min(30000,2000L<<failures) : 3000);
            });
        });
    }
    private static boolean gone(Exception e) {return e instanceof RoomClient.ServiceError && (((RoomClient.ServiceError)e).status==404 || ((RoomClient.ServiceError)e).status==410);}
    private String problem(Exception e, boolean code) {
        if(e instanceof RoomClient.ServiceError && ((RoomClient.ServiceError)e).status==410)return text("Access to this shared match has ended.","Der Zugriff auf diese geteilte Partie wurde beendet.","L’accès à ce match partagé a pris fin.","El acceso a este partido compartido ha terminado.");
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
    void menu(){
        if(s==null||blocked||joiningCode!=null){hub();return;}
        LinearLayout page=a.roomPanel("Shared match","room-menu");
        if(finalScore())a.primaryNavigationButton(page,text("Save to my results","In meine Ergebnisse speichern","Enregistrer dans mes résultats","Guardar en mis resultados"),this::pickSeat);
        else if(isHost())leaveButton(page,this::menu);
        a.section(page,"Score");
        connection=a.paragraph(page,status());
        summary=a.paragraph(page,"");
        refreshText();
        if(match!=null)a.navigationButton(page,text("Set scores","Satzstände","Scores des manches","Marcadores parciales"),a::showSharedMatchDetails);
        a.section(page,"Sharing");
        a.navigationButton(page,title(),()->open(Entry.SHARED));
        a.section(page,"App");
        a.roomSettings(page);
        a.backButton(page,"Back to score",a::closePanel);
    }
    void hub() {
        LinearLayout page=a.roomPanel(title(),"room-hub");summary=null;
        boolean cancelAtTop=isHost() && !finalScore() && !blocked;
        if(cancelAtTop)leaveButton(page,this::hub);
        connection=a.paragraph(page,status()); connection.setVisibility(s==null && !blocked && joiningCode==null ? android.view.View.GONE : android.view.View.VISIBLE);
        if(blocked){a.backButton(page,"Back to score",a::closePanel);return;}
        if(joiningCode!=null){
            a.backButton(page,"Cancel",()->{String code=joiningCode;++generation;joiningCode=null;errorsReset();joinPage(code);});
            return;
        }
        if(s==null) {
            boolean audienceOnly=entry==Entry.MATCH && a.roomLocalFinished();
            if(!audienceOnly)a.section(page,"Share your match");
            a.navigationButton(page,audienceTitle(),()->a.chooseSharingMatch(entry,true,null,a::showLive));
            if(audienceOnly){
                a.sharingBack(page,entry);
                return;
            }
            a.navigationButton(page,refereeTitle(),
                ()->a.chooseSharingMatch(entry,false,text("Create & invite referee","Erstellen & Schiedsrichter einladen","Créer et inviter l’arbitre","Crear e invitar al árbitro"),()->start(true)));
            a.navigationButton(page,watchSharingTitle(),
                ()->a.chooseSharingMatch(entry,false,text("Create & invite watches","Erstellen & Uhren einladen","Créer et inviter des montres","Crear e invitar a otros relojes"),()->openWatchSharing(false)));
            a.section(page,"Join another match");
            if(a.nfc.canRead())a.navigationButton(page,a.tr("Join via NFC"),
                ()->a.nfc.receive(this::join,this::hub,backToSharing()));
            a.navigationButton(page,joinTitle(),this::joinPage);
            a.sharingBack(page,entry);
            return;
        }
        if(ownsSharing()) {
            if(!finalScore() || a.hasAudienceLink())a.section(page,"Share your match");
            if(!finalScore()||a.hasAudienceLink())a.navigationButton(page,audienceTitle(),a::showLive);
            if(!finalScore())a.navigationButton(page,refereeTitle(),this::showReferee);
            a.navigationButton(page,watchSharingTitle(),()->openWatchSharing(false));
        }
        if(!s.memberId.isEmpty())a.paragraph(page,text("This watch","Diese Uhr","Cette montre","Este reloj")+" · "+shortId(s.memberId));
        syncButton(page);
        if(!cancelAtTop && readOnly())leaveButton(page,this::hub);
        if(readOnly()&&entry!=Entry.SHARED)a.backButton(page,"Back to score",a::closePanel);
        else a.sharingBack(page,entry);
    }

    private boolean ownsSharing() {
        return s!=null && !blocked && !"follow".equals(s.role);
    }

    private void syncButton(LinearLayout page) {
        if(s.pending || !error.isEmpty()) {
            a.secondaryButton(page,text("Sync now","Jetzt abgleichen","Synchroniser","Sincronizar"),()->{
                terminalError=false;
                schedule(0);
            });
        }
    }

    void watchInvitesFromLive(LinearLayout page) {
        if (blocked || terminalError || joiningCode != null || finalScore() || s != null && "follow".equals(s.role)) return;
        a.navigationButton(page, a.tr("Invite a watch"), ()->openWatchSharing(true));
    }

    private void openWatchSharing(boolean fromLive) {
        watchSharingFromLive=fromLive;
        if(s==null)start(false);
        else showWatchSharing();
    }

    private void showWatchSharing() {
        if(!ownsSharing()){hub();return;}
        LinearLayout page=a.roomPanel(watchSharingTitle(),"room-watches");
        summary=null;
        connection=null;
        if(!terminalError)a.primaryNavigationButton(page,pairingTitle(),this::showPairing);
        if(s.managed && s.ready)a.navigationButton(page,devicesTitle(),this::devices);
        if(!s.ready || !error.isEmpty()) {
            connection=a.paragraph(page,status());
            syncButton(page);
        }
        if(!s.managed)a.paragraph(page,text("Individual devices are available in new shares from v0.20.","Einzelne Geräte siehst du in neuen Freigaben ab v0.20.","Les appareils individuels apparaissent dans les nouveaux partages depuis la v0.20.","Los dispositivos aparecen en las nuevas sesiones desde la v0.20."));
        if(!readOnly())leaveButton(page,this::showWatchSharing);
        a.backButton(page,watchSharingFromLive?"Back to Live scoreboard":backToSharing(),watchSharingFromLive?a::showLive:this::hub);
    }

    private void showPairing() {
        if(!ownsSharing()){showWatchSharing();return;}
        LinearLayout page=a.roomPanel(pairingTitle(),"room-pairing");
        summary=null;
        connection=null;
        if(s.watchJoinReady && !terminalError) {
            a.navigationButton(page,a.tr("Show watch QR"),()->{
                if(ownsSharing() && s.watchJoinReady && !terminalError) {
                    String link=BuildConfig.SITE_URL+"/watch-invite.html?lang="+WatchLanguage.get(a).code+"#code="+s.joinCode;
                    a.showQr(link,a.tr("Scan to get the watch code"),this::showPairing,"Back to pairing",false);
                }
            });
            a.navigationButton(page,a.tr("Show watch code"),this::showWatchCode);
            if(a.nfc.canSend())a.navigationButton(page,a.tr("Pair a watch via NFC"),()->{
                if(ownsSharing() && s.watchJoinReady && !terminalError)
                    a.nfc.shareInvitation(s.joinCode,this::showPairing,"Back to pairing");
            });
        }
        if(!terminalError) {
            if(s.watchJoin) {
                a.secondaryButton(page,a.tr("Disable new joins"),this::toggleJoin);
                a.paragraph(page,a.tr("Connected watches can keep following."));
            } else a.primaryButton(page,a.tr("Enable new joins"),this::toggleJoin);
        }
        if(s.pending || !error.isEmpty()) {
            connection=a.paragraph(page,status());
            syncButton(page);
        }
        a.backButton(page,"Back to watch sharing",this::showWatchSharing);
    }

    private void showWatchCode() {
        if(!ownsSharing() || !s.watchJoinReady || terminalError){showPairing();return;}
        LinearLayout page=a.roomPanel(a.tr("Watch code"),"room-watch-code");
        TextView code=a.paragraph(page,s.joinCode.substring(0,5)+" "+s.joinCode.substring(5));
        code.setTextSize(22);
        a.paragraph(page,a.tr("Use this code on each watch.")+"\n"+title()+" → "+joinTitle());
        a.backButton(page,"Back to pairing",this::showPairing);
    }

    private void showReferee() {
        if(!ownsSharing() || finalScore()){hub();return;}
        LinearLayout page=a.roomPanel(refereeTitle(),"room-referee");
        summary=null;
        if(isHost())leaveButton(page,this::showReferee);
        connection=a.paragraph(page,status());
        if(!terminalError) {
            if(!isHost())a.primaryButton(page,a.tr("Hand over to referee"),()->start(true));
            else if(s.ready) {
                a.primaryNavigationButton(page,a.tr("Referee QR"),this::refereeQr);
                if(a.nfc.canSend())a.navigationButton(page,a.tr("Referee page via NFC"),()->{
                    if(isHost() && s.ready && !finalScore() && !terminalError)
                        a.nfc.shareLink(s.refereeLink,this::showReferee,"Back to phone referee");
                });
            }
        }
        syncButton(page);
        a.backButton(page,backToSharing(),()->{askQr=false;hub();});
    }

    private void start(boolean referee) {
        if(readOnly() || finalScore() || s!=null&&!referee){hub();return;}
        try {
            JSONObject score=local();
            if(score.getBoolean("final")){hub();return;}
            apply(s==null ? client.prepare(score,referee,!referee) : client.delegate(score));
            ++generation; failures=0;terminalError=false; error="";askQr=referee;
            if(referee) a.pauseForRoom();
            if(referee)showReferee();else showWatchSharing();
            schedule(0);
        } catch(Exception failure) { error=problem(failure,false); a.roomMessage(error,this::hub); }
    }
    private void toggleJoin() {
        if(!ownsSharing() || terminalError)return;
        try { apply(client.setWatchJoin(!s.watchJoin));showPairing();schedule(0); }
        catch(Exception failure){a.roomMessage(problem(failure,false),this::showPairing);}
    }
    private void refereeQr() {
        askQr=false;
        if(isHost() && s.ready && !finalScore() && !terminalError)
            a.showQr(s.refereeLink,text("Scan to referee","Zum Zählen scannen","Scanner pour arbitrer","Escanear para arbitrar"),this::showReferee,"Back to phone referee");
    }
    void joinPage(){joinPage("");}
    private void joinPage(String code) {
        if(s!=null || blocked || joiningCode!=null){hub();return;}
        LinearLayout p=a.roomPanel(joinTitle(),"room-code");
        a.paragraph(p,text("Enter the code shown on the host watch.","Gib den Code von der Gastgeberuhr ein.","Saisissez le code affiché sur la montre hôte.","Introduce el código del reloj anfitrión."));
        EditText field=a.menuInput(p,"",joinTitle(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setGravity(android.view.Gravity.CENTER);
        field.setFilters(new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(14)});
        field.setText(code);field.setSelection(field.length());
        a.primaryButton(p,text("Join match","Partie beitreten","Rejoindre le match","Unirse al partido"),()->join(field.getText().toString()));
        a.backButton(p,backToSharing(),this::hub);
    }
    private void join(String code) {
        if(joiningCode!=null || s!=null)return;
        joiningCode=code;error="";hub();final long attempt=++generation;
        io.execute(()->{
            try {
                RoomClient.PendingJoin pending=client.findJoin(code);
                main.post(()->{
                    if(destroyed||attempt!=generation)return;
                    joiningCode=null;
                    try{
                        apply(pending.accept());a.pauseForRoom();errorsReset();
                        if(visible){if(finalScore())menu();else a.closePanel();}schedule(0);
                    }catch(Exception failed){a.roomMessage(problem(failed,true),()->joinPage(code));}
                });
            }catch(Exception failed){
                main.post(()->{if(destroyed||attempt!=generation)return;joiningCode=null;a.roomMessage(problem(failed,true),()->joinPage(code));});
            }
        });
    }
    private String devicesTitle(){return text("Connected devices","Verbundene Geräte","Appareils connectés","Dispositivos conectados");}
    private String shortId(String id){return "#"+id.substring(Math.max(0,id.length()-6)).toUpperCase(java.util.Locale.ROOT);}
    private String deviceName(JSONObject member){
        return ("watch".equals(member.optString("role"))?text("Player watch","Spieleruhr","Montre de joueur","Reloj de jugador"):text("Referee","Schiedsrichter","Arbitre","Árbitro"))+" "+shortId(member.optString("id"));
    }
    private void devices(){
        if(!ownsSharing()||!s.managed||!s.ready){showWatchSharing();return;}
        String id=s.id;
        LinearLayout page=a.roomPanel(devicesTitle(),"room-devices");
        a.paragraph(page,text("Loading devices…","Geräte werden geladen …","Chargement des appareils…","Cargando dispositivos…"));
        a.backButton(page,"Back to watch sharing",this::showWatchSharing);
        io.execute(()->{
            try{
                JSONObject result=client.participants(id);
                main.post(()->{if(a.roomPageVisible(page)&&!destroyed)showDevices(result);});
            }catch(Exception failed){
                main.post(()->{if(!a.roomPageVisible(page)||destroyed)return;
                    LinearLayout retry=a.roomPanel(devicesTitle(),"room-devices");
                    a.paragraph(retry,text("Could not load the device list.","Die Geräteliste konnte nicht geladen werden.","Impossible de charger les appareils.","No se pudieron cargar los dispositivos."));
                    a.primaryButton(retry,text("Try again","Erneut versuchen","Réessayer","Reintentar"),this::devices);
                    a.backButton(retry,"Back to watch sharing",this::showWatchSharing);
                });
            }
        });
    }
    private void showDevices(JSONObject result){
        LinearLayout page=a.roomPanel(devicesTitle(),"room-devices");
        org.json.JSONArray members=result.optJSONArray("members");
        int count=members==null?0:members.length();
        if(count==0)a.paragraph(page,text("No watches or referees have connected yet.","Bisher ist keine Spieleruhr und kein Schiedsrichter beigetreten.","Aucune montre ni aucun arbitre ne s’est connecté.","Aún no se ha conectado ningún reloj ni árbitro."));
        for(String role:new String[]{"watch","referee"}){
            boolean first=true;
            for(int i=0;i<count;i++){
                JSONObject member=members.optJSONObject(i);
                if(member==null||!role.equals(member.optString("role")))continue;
                if(first){a.section(page,role.equals("watch")?"Player watches":"Referees");first=false;}
                long minutes=Math.max(0,(result.optLong("now")-member.optLong("seenAt"))/60000);
                String seen=minutes==0?text("just now","gerade eben","à l’instant","ahora"):minutes+" min";
                a.navigationButton(page,deviceName(member)+" · "+seen,()->confirmRemoval(member));
            }
        }
        if(count>0)a.paragraph(page,text("Time shows the last contact. Select a device to remove it.","Die Zeit zeigt den letzten Kontakt. Tippe zum Entfernen auf das Gerät.","L’heure indique le dernier contact. Touchez un appareil pour le retirer.","El tiempo indica el último contacto. Toca un dispositivo para quitarlo."));
        a.secondaryButton(page,text("Refresh","Aktualisieren","Actualiser","Actualizar"),this::devices);
        a.backButton(page,"Back to watch sharing",this::showWatchSharing);
    }
    private void confirmRemoval(JSONObject member){
        if(s==null)return;
        String id=s.id;
        LinearLayout page=a.roomPanel(text("Remove device?","Gerät entfernen?","Retirer l’appareil ?","¿Quitar dispositivo?"),"room-remove");
        a.paragraph(page,deviceName(member));
        a.paragraph(page,text("This device will lose access. Other devices stay connected and the watch code stays valid.","Dieses Gerät verliert den Zugriff. Der Uhrencode bleibt gültig und alle anderen Geräte bleiben verbunden.","Cet appareil perdra l’accès. Les autres appareils et le code resteront actifs.","Este dispositivo perderá el acceso. Los demás dispositivos y el código seguirán activos."));
        a.primaryButton(page,text("Remove device","Gerät entfernen","Retirer l’appareil","Quitar dispositivo"),()->{
            android.widget.Button action=(android.widget.Button)page.findViewWithTag("remove-device");
            if(action!=null)action.setEnabled(false);
            io.execute(()->{
                try{client.removeParticipant(id,member.getString("id"));main.post(()->{if(!destroyed&&a.roomPageVisible(page))devices();});}
                catch(Exception failed){main.post(()->{if(!destroyed&&a.roomPageVisible(page))a.roomMessage(text("Removal was not confirmed. Refresh the device list.","Entfernen wurde nicht bestätigt. Aktualisiere die Geräteliste.","Retrait non confirmé. Actualisez la liste.","No se confirmó la eliminación. Actualiza la lista."),this::devices);});}
            });
        }).setTag("remove-device");
        a.backButton(page,"Cancel",this::devices);
    }
    private void errorsReset(){error="";terminalError=false;failures=0;}
    private void pickSeat() {
        if(!finalScore() || !readOnly()) return;
        if(isHost() || a.roomAlreadySaved(match.id())) {save(0);return;}
        LinearLayout p=a.roomPanel(text("Who did you play as?","Als wer hast du gespielt?","Qui étiez-vous ?","¿Qué jugador eras?"),"room-seat");
        a.paragraph(p,text("Your record uses the selected player’s side.","Deine Bilanz wird aus Sicht dieser Person gespeichert.","Votre bilan suivra le côté de ce joueur.","Tu historial usará el lado de este jugador."));
        for(int i=0;i<(match.format().doubles?4:2);i++){final int seat=i;a.choiceButton(p,label(i),false,()->save(seat));}
        a.backButton(p,"Back to Menu",this::menu);
    }
    private void save(int seat) {
        if(!finalScore() || !readOnly() || match==null) return;
        try {
            String id=match.id(),sport=match.format().sport;
            a.saveRoomResult(match,data,seat,isHost());
            client.leave(); ++generation; apply(null);errorsReset();
            a.openSavedRoomResult(id,sport);
        } catch(Exception failed){a.roomMessage(text("Could not save. The final score is still here. Check available result storage and retry.","Speichern fehlgeschlagen. Das Endergebnis bleibt hier. Prüfe den Ergebnisspeicher und versuche es erneut.","Échec de l’enregistrement. Le résultat reste disponible. Vérifiez l’espace et réessayez.","No se pudo guardar. El resultado sigue aquí. Revisa el espacio y vuelve a intentarlo."),this::menu);}
    }
    private void leaveButton(LinearLayout page,Runnable back){
        String label;
        if(isHost() && !finalScore())label=a.tr("Cancel match");
        else if(readOnly())label=text("Leave shared match","Geteilte Partie verlassen","Quitter le match partagé","Salir del partido compartido");
        else label=text("Stop watch sharing","Uhrenfreigabe beenden","Arrêter le partage","Dejar de compartir");
        a.secondaryNavigationButton(page,label,()->confirmLeave(back));
    }
    private void confirmLeave(Runnable back){
        if(s==null) return;
        RoomClient.Session shown=s;
        boolean finished=finalScore(), cancelHost=isHost() && !finished;
        askQr=false;
        String title=cancelHost ? text("Cancel match?","Partie abbrechen?","Annuler le match ?","¿Cancelar el partido?")
            : text("Leave match?","Partie verlassen?","Quitter le match ?","¿Salir del partido?");
        LinearLayout p=a.roomPanel(title,cancelHost?"room-cancel":"room-leave");
        String message=finished ? text("Leave without making another copy of this result?","Diese Ansicht ohne weitere Ergebniskopie schließen?","Quitter sans enregistrer une autre copie du résultat ?","¿Salir sin guardar otra copia del resultado?")
            : cancelHost ? text("This ends the match for the referee and all connected watches without saving a result.","Die Partie endet für den Schiedsrichter und alle verbundenen Uhren. Es wird kein Ergebnis gespeichert.","Le match prendra fin pour l’arbitre et toutes les montres connectées, sans enregistrer de résultat.","El partido terminará para el árbitro y todos los relojes conectados sin guardar un resultado.")
            : readOnly() ? text("The other watches will keep following the match.","Die anderen Uhren verfolgen die Partie weiter.","Les autres montres continueront à suivre le match.","Los demás relojes seguirán viendo el partido.")
            : text("The other watches will stop receiving this score. You can keep scoring locally.","Weitere Uhren erhalten keine neuen Punkte mehr. Du kannst auf deiner Uhr weiterzählen.","Les autres montres cesseront de recevoir ce score. Vous pouvez continuer à compter.","Los demás relojes dejarán de recibir este marcador. Puedes seguir anotando.");
        a.paragraph(p,message);
        Runnable leave=()->{
            if(!a.roomPageVisible(p))return;
            try{
                if(client.leaveConfirmed(shown,a::discardRoomDraft)){
                    ++generation;apply(null);errorsReset();requestOutbox();
                    a.restoreLocalScore();
                    if(cancelHost)a.showSportAfterDiscard();
                    else a.showSports();
                }else{
                    apply(client.current());
                    a.restoreLocalScore();
                    a.showSports();
                }
            }catch(Exception e){a.roomMessage(problem(e,false),back);}
        };
        if(cancelHost)a.deleteButton(p,a.tr("Discard match"),leave);
        else a.primaryButton(p,text("Leave","Verlassen","Quitter","Salir"),leave);
        a.backButton(p,cancelHost?a.tr("Keep playing"):text("Keep match","Partie behalten","Garder le match","Conservar el partido"),back);
    }
}
